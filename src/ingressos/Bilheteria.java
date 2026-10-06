package ingressos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static ingressos.Mensagem.Tipo.*;
import static ingressos.Sessao.Estado;

/**
 * Regras da venda: sala de espera, sorteio, fila virtual, vagas da área de compra,
 * prazo, pagamento e CPF único. Publica eventos aos observadores (padrão Observer).
 */
public final class Bilheteria {

    /** Padrão Observer: recebe cada evento como texto {@code TIPO nome assento detalhe}. */
    @FunctionalInterface
    public interface Observador {
        void notificar(String evento);
    }

    private final Config config;
    private final ScheduledExecutorService agendador;
    private final ExecutorService poolPagamento;
    private final Runnable aoEncerrar;
    private final Assentos assentos;
    private final Semaphore vagas;
    private final BlockingQueue<Sessao> fila;
    private final Random random;
    private final List<Sessao> salaEspera = new ArrayList<>();
    private boolean aberta;
    private boolean encerrada;
    private final Set<String> cpfsAtivos = new HashSet<>();
    private final Set<Sessao> sessoes = Collections.synchronizedSet(new HashSet<>());
    private final List<Observador> observadores = new ArrayList<>();
    private final Map<Sessao, Observador> monitores = Collections.synchronizedMap(new HashMap<>());
    private final AtomicInteger clientes = new AtomicInteger();
    private final AtomicInteger rejeitados = new AtomicInteger();
    private final Map<Estado, AtomicInteger> desfechos = new EnumMap<>(Estado.class);

    /**
     * @param config        configuração
     * @param agendador     executor dos timeouts
     * @param poolPagamento executor do pagamento simulado
     * @param aoEncerrar    chamado uma vez quando as vendas terminam
     */
    public Bilheteria(Config config, ScheduledExecutorService agendador, ExecutorService poolPagamento, Runnable aoEncerrar) {
        this.config = config;
        this.agendador = agendador;
        this.poolPagamento = poolPagamento;
        this.aoEncerrar = aoEncerrar;
        this.assentos = new Assentos(config.linhas(), config.colunas());
        this.vagas = new Semaphore(config.vagasAreaCompra());
        this.fila = new ArrayBlockingQueue<>(config.threadsAtendimento() + config.filaAtendimento());
        this.random = config.sementeSorteio() == null ? new Random() : new Random(config.sementeSorteio());
        for (Estado e : List.of(Estado.CONCLUIDO, Estado.EXPIRADO, Estado.RECUSADO, Estado.DESCONECTADO, Estado.ESGOTADO)) {
            desfechos.put(e, new AtomicInteger());
        }
    }

    /** @param observador interessado nos eventos */
    public void adicionarObservador(Observador observador) {
        synchronized (observadores) {
            observadores.add(observador);
        }
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
     * Identifica o cliente e o coloca na sala de espera (antes da abertura) ou no fim da fila.
     * Sala e abertura usam o mesmo monitor, então ninguém se perde no instante do sorteio.
     *
     * @param sessao sessão
     * @param nome   nome do cliente
     * @param cpf    CPF do cliente
     */
    public void entrar(Sessao sessao, String nome, String cpf) {
        boolean cpfLivre;
        synchronized (cpfsAtivos) {
            cpfLivre = !encerrada() && cpfsAtivos.add(cpf);
        }
        if (!cpfLivre) {
            sessao.enviar(encerrada() ? ESGOTADO : CPF_EM_USO);
            sessao.fechar();
            return;
        }
        sessao.identificar(nome, cpf);
        clientes.incrementAndGet();
        int posicao = 0;
        synchronized (salaEspera) {
            if (aberta) {
                sessao.transitar(Estado.CONECTADO, Estado.NA_FILA);
                fila.add(sessao);
                posicao = fila.size();
            } else {
                sessao.transitar(Estado.CONECTADO, Estado.SALA_ESPERA);
                salaEspera.add(sessao);
            }
        }
        publicar("ENTROU", nome, null, posicao == 0 ? "sala-de-espera" : "fila " + posicao);
        if (posicao == 0) {
            sessao.enviar(SALA_ESPERA);
        } else {
            sessao.enviar(POSICAO, posicao);
        }
    }

    /** Abre as vendas (tarefa agendada): ordena a sala pela política e forma a fila. */
    public void abrirVendas() {
        List<Sessao> ordem = new ArrayList<>();
        synchronized (salaEspera) {
            config.politicaFila().ordenar(salaEspera, random);
            for (Sessao sessao : salaEspera) {
                if (sessao.transitar(Estado.SALA_ESPERA, Estado.NA_FILA)) {
                    fila.add(sessao);
                    ordem.add(sessao);
                }
            }
            salaEspera.clear();
            aberta = true;
            salaEspera.notifyAll();
        }
        for (int i = 0; i < ordem.size(); i++) {
            ordem.get(i).enviar(POSICAO, i + 1);
        }
        publicar("SORTEIO", "-", null, config.politicaFila() + " " + ordem.size() + " clientes");
    }

    /**
     * Laço do porteiro: espera a abertura e, a cada vaga do semáforo, chama o próximo da fila.
     * Pegar a vaga antes do cliente garante que a ordem da fila seja respeitada.
     */
    public void porteiro() {
        try {
            synchronized (salaEspera) {
                while (!aberta) {
                    salaEspera.wait();
                }
            }
            while (!Thread.currentThread().isInterrupted()) {
                vagas.acquire();
                Sessao sessao = fila.take();
                if (!admitir(sessao)) {
                    vagas.release();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean admitir(Sessao sessao) {
        int prazo = config.prazoCompraSeg();
        if (!sessao.entrarAreaCompra(() -> agendador.schedule(() -> expirar(sessao), prazo, TimeUnit.SECONDS))) {
            return false;
        }
        publicar("ENTROU_AREA", sessao.nome(), null, "prazo " + prazo + "s");
        sessao.enviar(SUA_VEZ, prazo);
        return true;
    }

    private void expirar(Sessao sessao) {
        if (finalizar(sessao, Estado.EXPIRADO)) {
            sessao.enviar(TEMPO_ESGOTADO);
            publicar("EXPIROU", sessao.nome(), sessao.assento(), null);
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
            publicar("RESERVOU", sessao.nome(), sessao.assento(), null);
        } else {
            sessao.enviar(INDISPONIVEL, assento);
        }
    }

    /**
     * Paga no pool de pagamento. Aprovado conclui a compra; recusado devolve assento e vaga
     * para o próximo da fila. Se o prazo vencer durante o pagamento, o timeout vence a disputa.
     *
     * @param sessao sessão comprando
     */
    public void pagar(Sessao sessao) {
        String assento = sessao.assento();
        if (assento == null) {
            sessao.enviar(ERRO, "reserve um assento antes de pagar");
            return;
        }
        boolean aprovado = processarPagamento();
        if (!finalizar(sessao, aprovado ? Estado.CONCLUIDO : Estado.RECUSADO)) {
            sessao.enviar(TEMPO_ESGOTADO);
            return;
        }
        if (aprovado) {
            sessao.enviar(COMPRA_OK, assento);
            publicar("VENDEU", sessao.nome(), assento, String.format("%.1fs", sessao.segundosComprando()));
        } else {
            sessao.enviar(PAGAMENTO_RECUSADO);
            publicar("RECUSADO", sessao.nome(), assento, null);
        }
        sessao.fechar();
        if (assentos.vendidos() == assentos.capacidade()) {
            encerrar("esgotado");
        }
    }

    private boolean processarPagamento() {
        try {
            return poolPagamento.submit(this::pagamentoAprovado).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | RejectedExecutionException e) {
            return false;
        }
    }

    /** Operadora de cartão simulada: demora um tempo aleatório e recusa com certa probabilidade. */
    private boolean pagamentoAprovado() throws InterruptedException {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Thread.sleep(r.nextInt(config.latenciaPagamentoMinMs(), config.latenciaPagamentoMaxMs() + 1));
        return r.nextDouble() >= config.probRecusaPagamento();
    }

    /**
     * Transforma a conexão em painel de monitoramento: envia a configuração e o mapa e
     * passa a receber os eventos.
     *
     * @param sessao sessão do painel
     */
    public void monitorar(Sessao sessao) {
        sessao.transitar(Estado.CONECTADO, Estado.MONITORANDO);
        sessao.enviar(CONFIG, config.linhas(), config.colunas(), config.vagasAreaCompra(), config.prazoCompraSeg());
        sessao.enviar(MAPA, assentos.mapa());
        Observador painel = evento -> sessao.enviar(EVT, evento);
        monitores.put(sessao, painel);
        adicionarObservador(painel);
    }

    /**
     * Libera tudo o que a sessão ocupava (vaga, assento, lugar na fila, CPF).
     * Chamado no {@code finally} do atendimento: cobre saída voluntária e queda de conexão.
     *
     * @param sessao sessão encerrada
     */
    public void desconectar(Sessao sessao) {
        Observador painel = monitores.remove(sessao);
        if (painel != null) {
            synchronized (observadores) {
                observadores.remove(painel);
            }
        }
        if (retirar(sessao, Estado.DESCONECTADO)) {
            publicar("DESCONECTOU", sessao.nome(), sessao.assento(), null);
        }
        if (sessao.cpf() != null) {
            synchronized (cpfsAtivos) {
                cpfsAtivos.remove(sessao.cpf());
            }
        }
        sessoes.remove(sessao);
        sessao.fechar();
    }

    /** Conta uma conexão recusada por falta de capacidade do pool de atendimento. */
    public void registrarRejeicao() {
        rejeitados.incrementAndGet();
        publicar("REJEITADO", "-", null, null);
    }

    /**
     * Publica o status dos pools e o mapa de assentos (tarefa periódica).
     *
     * @param pools descrição dos pools de threads
     */
    public void publicarStatus(String pools) {
        publicar("STATUS", "-", null, pools + " fila=" + fila.size() + " vagasLivres=" + vagas.availablePermits());
        publicar("MAPA", "-", null, assentos.mapa());
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
                publicar("ESGOTADO", sessao.nome(), null, null);
            }
        }
        publicar("ENCERRADO", "-", null, motivo);
        aoEncerrar.run();
    }

    /** Fecha todas as conexões, desbloqueando as threads paradas em leitura. */
    public void fecharConexoes() {
        copiaSessoes().forEach(Sessao::fechar);
    }

    /** @return relatório final em texto */
    public String relatorio() {
        int total = desfechos.values().stream().mapToInt(AtomicInteger::get).sum();
        return """
                ============ RELATÓRIO FINAL ============
                Clientes ................. %d
                Rejeitados (LOTADO) ...... %d
                Ingressos vendidos ....... %d de %d
                Expirados ................ %d
                Pagamentos recusados ..... %d
                Desconectados ............ %d
                Sem ingresso (esgotado) .. %d
                Desfechos / clientes ..... %d / %d %s
                =========================================
                """.formatted(clientes.get(), rejeitados.get(), assentos.vendidos(), assentos.capacidade(),
                desfechos.get(Estado.EXPIRADO).get(), desfechos.get(Estado.RECUSADO).get(),
                desfechos.get(Estado.DESCONECTADO).get(), desfechos.get(Estado.ESGOTADO).get(),
                total, clientes.get(), total == clientes.get() ? "(ok)" : "(DIVERGENTE)");
    }

    /** Tira a sessão da sala, da fila ou da área de compra, contando o desfecho. */
    private boolean retirar(Sessao sessao, Estado destino) {
        if (sessao.transitar(Estado.SALA_ESPERA, destino) || sessao.transitar(Estado.NA_FILA, destino)) {
            synchronized (salaEspera) {
                salaEspera.remove(sessao);
            }
            fila.remove(sessao);
            desfechos.get(destino).incrementAndGet();
            return true;
        }
        return finalizar(sessao, destino);
    }

    private boolean finalizar(Sessao sessao, Estado destino) {
        if (!sessao.finalizar(destino, assentos, vagas)) {
            return false;
        }
        desfechos.get(destino).incrementAndGet();
        return true;
    }

    private synchronized boolean encerrada() {
        return encerrada;
    }

    private List<Sessao> copiaSessoes() {
        synchronized (sessoes) {
            return List.copyOf(sessoes);
        }
    }

    private void publicar(String tipo, String nome, String assento, String detalhe) {
        String evento = tipo + " " + nome + " " + (assento == null ? "-" : assento) + (detalhe == null ? "" : " " + detalhe);
        List<Observador> copia;
        synchronized (observadores) {
            copia = List.copyOf(observadores);
        }
        copia.forEach(o -> o.notificar(evento));
    }
}
