package ingressos.servidor.venda;

import ingressos.comum.Conexao;
import ingressos.servidor.dominio.AreaCompra;
import ingressos.servidor.dominio.Assentos;
import ingressos.servidor.dominio.RegistroCpf;
import ingressos.servidor.dominio.Sessao;
import ingressos.servidor.evento.Evento;
import ingressos.servidor.evento.Notificador;
import ingressos.servidor.evento.Observador;
import ingressos.servidor.fila.FilaVirtual;
import ingressos.servidor.pagamento.GatewayPagamento;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ingressos.comum.Mensagem.Tipo.*;
import static ingressos.servidor.dominio.Sessao.Estado;
import static ingressos.servidor.evento.Evento.Tipo;

/**
 * Casos de uso da venda (entrar, reservar, pagar, sair...). Orquestra os componentes recebidos
 * pelo construtor e publica um {@link Evento} a cada acontecimento.
 */
public final class Bilheteria {

    private final Assentos assentos;
    private final FilaVirtual fila;
    private final AreaCompra area;
    private final RegistroCpf cpfs;
    private final GatewayPagamento pagamento;
    private final Notificador notificador;
    private final ScheduledExecutorService agendador;
    private final int prazoCompraSeg;
    private final Runnable aoEncerrar;
    private final Set<Sessao> sessoes = Collections.synchronizedSet(new HashSet<>());
    private final Map<Sessao, Observador> paineis = Collections.synchronizedMap(new HashMap<>());
    private boolean encerrada;

    /**
     * @param assentos       mapa de assentos
     * @param fila           sala de espera e fila virtual
     * @param area           área de compra com vagas limitadas
     * @param cpfs           registro de CPFs ativos
     * @param pagamento      operadora de pagamento
     * @param notificador    publicador de eventos
     * @param agendador      executor dos prazos de compra
     * @param prazoCompraSeg prazo para concluir a compra
     * @param aoEncerrar     chamado uma vez quando as vendas terminam
     */
    public Bilheteria(Assentos assentos, FilaVirtual fila, AreaCompra area, RegistroCpf cpfs,
                      GatewayPagamento pagamento, Notificador notificador, ScheduledExecutorService agendador,
                      int prazoCompraSeg, Runnable aoEncerrar) {
        this.assentos = assentos;
        this.fila = fila;
        this.area = area;
        this.cpfs = cpfs;
        this.pagamento = pagamento;
        this.notificador = notificador;
        this.agendador = agendador;
        this.prazoCompraSeg = prazoCompraSeg;
        this.aoEncerrar = aoEncerrar;
    }

    /**
     * @param conexao conexão aceita
     * @return nova sessão
     */
    public Sessao conectar(Conexao conexao) {
        Sessao sessao = new Sessao(conexao);
        sessoes.add(sessao);
        return sessao;
    }

    /**
     * Identifica o cliente e o coloca na sala de espera ou no fim da fila.
     *
     * @param sessao sessão
     * @param nome   nome do cliente
     * @param cpf    CPF do cliente
     */
    public void entrar(Sessao sessao, String nome, String cpf) {
        if (encerrada() || !cpfs.registrar(cpf)) {
            sessao.enviar(encerrada() ? ESGOTADO : CPF_EM_USO);
            sessao.fechar();
            return;
        }
        sessao.identificar(nome, cpf);
        int posicao = fila.registrar(sessao);
        publicar(Evento.de(Tipo.ENTROU, nome, null).com(posicao == 0 ? "sala-de-espera" : "fila " + posicao));
        if (posicao == 0) {
            sessao.enviar(SALA_ESPERA);
        } else {
            sessao.enviar(POSICAO, posicao);
        }
    }

    /** Abre as vendas (tarefa agendada): sorteia a sala de espera e informa as posições. */
    public void abrirVendas() {
        List<Sessao> ordem = fila.abrir();
        for (int i = 0; i < ordem.size(); i++) {
            ordem.get(i).enviar(POSICAO, i + 1);
        }
        publicar(Evento.sistema(Tipo.SORTEIO, fila.politica() + " " + ordem.size() + " clientes"));
    }

    /**
     * Coloca a sessão na área de compra e agenda o fim do prazo. Usado pelo porteiro.
     *
     * @param sessao próxima sessão da fila
     * @return {@code false} se a sessão já não estava na fila
     */
    public boolean admitir(Sessao sessao) {
        if (!sessao.entrarAreaCompra(() -> agendador.schedule(() -> expirar(sessao), prazoCompraSeg, TimeUnit.SECONDS))) {
            return false;
        }
        publicar(Evento.de(Tipo.ENTROU_AREA, sessao.nome(), null).com("prazo " + prazoCompraSeg + "s"));
        sessao.enviar(SUA_VEZ, prazoCompraSeg);
        return true;
    }

    private void expirar(Sessao sessao) {
        if (sessao.finalizar(Estado.EXPIRADO, assentos, area)) {
            sessao.enviar(TEMPO_ESGOTADO);
            publicar(Evento.de(Tipo.EXPIROU, sessao.nome(), sessao.assento()));
            sessao.fechar();
        }
    }

    /** @param sessao sessão que pediu o mapa */
    public void listar(Sessao sessao) {
        sessao.enviar(MAPA, assentos.mapa());
    }

    /**
     * @param sessao  sessão comprando
     * @param assento assento desejado
     */
    public void reservar(Sessao sessao, String assento) {
        if (sessao.assento() != null) {
            sessao.enviar(ERRO, "ja existe reserva em", sessao.assento());
        } else if (sessao.reservar(assento, assentos)) {
            sessao.enviar(RESERVADO, sessao.assento());
            publicar(Evento.de(Tipo.RESERVOU, sessao.nome(), sessao.assento()));
        } else {
            sessao.enviar(INDISPONIVEL, assento);
        }
    }

    /**
     * Aprovado conclui a compra; recusado devolve assento e vaga para o próximo da fila.
     * Se o prazo vencer durante o pagamento, o timeout vence a disputa.
     *
     * @param sessao sessão comprando
     */
    public void pagar(Sessao sessao) {
        String assento = sessao.assento();
        if (assento == null) {
            sessao.enviar(ERRO, "reserve um assento antes de pagar");
            return;
        }
        boolean aprovado = pagamento.pagar();
        if (!sessao.finalizar(aprovado ? Estado.CONCLUIDO : Estado.RECUSADO, assentos, area)) {
            sessao.enviar(TEMPO_ESGOTADO);
            return;
        }
        if (aprovado) {
            sessao.enviar(COMPRA_OK, assento);
            publicar(Evento.de(Tipo.VENDEU, sessao.nome(), assento).com(String.format("%.1fs", sessao.segundosComprando())));
        } else {
            sessao.enviar(PAGAMENTO_RECUSADO);
            publicar(Evento.de(Tipo.RECUSADO, sessao.nome(), assento));
        }
        sessao.fechar();
        if (assentos.esgotados()) {
            encerrar("esgotado");
        }
    }

    /**
     * Transforma a conexão em painel: envia configuração e mapa e passa a receber os eventos.
     *
     * @param sessao sessão do painel
     */
    public void monitorar(Sessao sessao) {
        sessao.transitar(Estado.CONECTADO, Estado.MONITORANDO);
        sessao.enviar(CONFIG, assentos.linhas(), assentos.colunas(), area.total(), prazoCompraSeg);
        sessao.enviar(MAPA, assentos.mapa());
        Observador painel = evento -> sessao.enviar(EVT, evento);
        paineis.put(sessao, painel);
        notificador.adicionar(painel);
    }

    /**
     * Libera tudo o que a sessão ocupava. Chamado no {@code finally} do atendimento:
     * cobre saída voluntária e queda de conexão.
     *
     * @param sessao sessão encerrada
     */
    public void desconectar(Sessao sessao) {
        Observador painel = paineis.remove(sessao);
        if (painel != null) {
            notificador.remover(painel);
        }
        if (retirar(sessao, Estado.DESCONECTADO)) {
            publicar(Evento.de(Tipo.DESCONECTOU, sessao.nome(), sessao.assento()));
        }
        if (sessao.cpf() != null) {
            cpfs.liberar(sessao.cpf());
        }
        sessoes.remove(sessao);
        sessao.fechar();
    }

    /** Registra uma conexão recusada por falta de capacidade do pool de atendimento. */
    public void registrarRejeicao() {
        publicar(Evento.sistema(Tipo.REJEITADO, ""));
    }

    /**
     * Publica o status dos pools e o mapa de assentos (tarefa periódica).
     *
     * @param pools descrição dos pools de threads
     */
    public void publicarStatus(String pools) {
        publicar(Evento.sistema(Tipo.STATUS, pools + " fila=" + fila.tamanho() + " vagasLivres=" + area.livres()));
        publicar(Evento.sistema(Tipo.MAPA, assentos.mapa()));
    }

    /**
     * Encerra as vendas uma única vez: avisa quem ainda espera e dispara o desligamento.
     *
     * @param motivo motivo do encerramento
     */
    public void encerrar(String motivo) {
        synchronized (this) {
            if (encerrada) {
                return;
            }
            encerrada = true;
        }
        for (Sessao sessao : copiaSessoes()) {
            if (retirar(sessao, Estado.ESGOTADO)) {
                sessao.enviar(ESGOTADO, motivo);
                publicar(Evento.de(Tipo.ESGOTADO, sessao.nome(), null));
            }
        }
        publicar(Evento.sistema(Tipo.ENCERRADO, motivo));
        aoEncerrar.run();
    }

    /** Fecha todas as conexões, desbloqueando as threads paradas em leitura. */
    public void fecharConexoes() {
        copiaSessoes().forEach(Sessao::fechar);
    }

    /** Tira a sessão da sala, da fila ou da área de compra. */
    private boolean retirar(Sessao sessao, Estado destino) {
        if (sessao.transitar(Estado.SALA_ESPERA, destino) || sessao.transitar(Estado.NA_FILA, destino)) {
            fila.remover(sessao);
            return true;
        }
        return sessao.finalizar(destino, assentos, area);
    }

    private synchronized boolean encerrada() {
        return encerrada;
    }

    private List<Sessao> copiaSessoes() {
        synchronized (sessoes) {
            return List.copyOf(sessoes);
        }
    }

    private void publicar(Evento evento) {
        notificador.publicar(evento);
    }
}
