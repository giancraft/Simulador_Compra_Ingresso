package ingressos;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static ingressos.Mensagem.Tipo.*;

/**
 * Cliente conectado ao servidor. É um monitor: as transições de estado são {@code synchronized},
 * o que resolve as disputas entre a thread de atendimento, o porteiro e o timeout (ex.: PAGAR × prazo).
 */
public final class Sessao {

    /** Padrão State: cada estado define quais comandos o cliente pode enviar. */
    public enum Estado {
        CONECTADO(ENTRAR, MONITORAR, SAIR),
        SALA_ESPERA(SAIR),
        NA_FILA(SAIR),
        COMPRANDO(LISTAR, RESERVAR, PAGAR, SAIR),
        MONITORANDO(SAIR),
        CONCLUIDO, EXPIRADO, RECUSADO, ESGOTADO, DESCONECTADO;

        private final Set<Mensagem.Tipo> aceitos;

        Estado(Mensagem.Tipo... aceitos) {
            this.aceitos = aceitos.length == 0 ? EnumSet.noneOf(Mensagem.Tipo.class) : EnumSet.copyOf(Arrays.asList(aceitos));
        }

        /**
         * @param tipo comando recebido
         * @return {@code true} se o comando é válido neste estado
         */
        public boolean aceita(Mensagem.Tipo tipo) {
            return aceitos.contains(tipo);
        }
    }

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    private final long id = SEQUENCIA.incrementAndGet();
    private final Conexao conexao;
    private String nome;
    private String cpf;
    private Estado estado = Estado.CONECTADO;
    private String assento;
    private ScheduledFuture<?> timeout;
    private long entradaArea;

    /** @param conexao conexão do cliente */
    public Sessao(Conexao conexao) {
        this.conexao = conexao;
    }

    /**
     * @param nome nome do cliente
     * @param cpf  CPF já registrado como ativo
     */
    public synchronized void identificar(String nome, String cpf) {
        this.nome = nome;
        this.cpf = cpf;
    }

    /** @return nome do cliente ou {@code #id} antes do ENTRAR */
    public synchronized String nome() {
        return nome != null ? nome : "#" + id;
    }

    /** @return CPF ou {@code null} se não se identificou */
    public synchronized String cpf() {
        return cpf;
    }

    /** @return estado atual */
    public synchronized Estado estado() {
        return estado;
    }

    /** @return assento reservado ou {@code null} */
    public synchronized String assento() {
        return assento;
    }

    /**
     * @param de   estado esperado
     * @param para novo estado
     * @return {@code true} se a sessão estava em {@code de} e mudou
     */
    public synchronized boolean transitar(Estado de, Estado para) {
        if (estado != de) {
            return false;
        }
        estado = para;
        return true;
    }

    /**
     * Passa de NA_FILA para COMPRANDO e agenda o fim do prazo.
     *
     * @param agendarTimeout agenda a expiração
     * @return {@code false} se a sessão já saiu da fila
     */
    public synchronized boolean entrarAreaCompra(Supplier<ScheduledFuture<?>> agendarTimeout) {
        if (!transitar(Estado.NA_FILA, Estado.COMPRANDO)) {
            return false;
        }
        entradaArea = System.currentTimeMillis();
        timeout = agendarTimeout.get();
        return true;
    }

    /**
     * Reserva sob o monitor da sessão, para não reservar depois de um timeout.
     *
     * @param id       assento desejado
     * @param assentos mapa de assentos
     * @return {@code true} se reservou
     */
    public synchronized boolean reservar(String id, Assentos assentos) {
        if (estado != Estado.COMPRANDO || assento != null || !assentos.reservar(id, this.id)) {
            return false;
        }
        assento = id.toUpperCase(Locale.ROOT);
        return true;
    }

    /**
     * Único ponto de saída de COMPRANDO: vende ou libera o assento e devolve a vaga.
     * Quem chegar primeiro (pagamento, timeout ou desconexão) vence; os demais recebem {@code false}.
     *
     * @param destino  estado final
     * @param assentos mapa de assentos
     * @param vagas    semáforo da área de compra
     * @return {@code true} se esta chamada finalizou a compra
     */
    public synchronized boolean finalizar(Estado destino, Assentos assentos, Semaphore vagas) {
        if (!transitar(Estado.COMPRANDO, destino)) {
            return false;
        }
        timeout.cancel(false);
        if (assento != null) {
            if (destino == Estado.CONCLUIDO) {
                assentos.vender(assento);
            } else {
                assentos.liberar(assento, id);
            }
        }
        vagas.release(); // liberado só aqui: um release por sessão
        return true;
    }

    /** @return segundos desde a entrada na área de compra */
    public synchronized double segundosComprando() {
        return (System.currentTimeMillis() - entradaArea) / 1000.0;
    }

    /**
     * @param tipo tipo da mensagem
     * @param args argumentos
     */
    public void enviar(Mensagem.Tipo tipo, Object... args) {
        conexao.enviar(tipo, args);
    }

    /** @return conexão do cliente */
    public Conexao conexao() {
        return conexao;
    }

    /** Fecha a conexão do cliente. */
    public void fechar() {
        conexao.close();
    }
}
