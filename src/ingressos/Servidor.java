package ingressos;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Servidor de ingressos: monta os executores, aceita conexões e entrega cada uma ao pool de atendimento. */
public final class Servidor {

    private final Config config;
    private final ServerSocket servidor;
    private final ThreadPoolExecutor poolAtendimento;
    private final ThreadPoolExecutor poolPagamento;
    private final ScheduledThreadPoolExecutor agendador;
    private final ExecutorService porteiro;
    private final Bilheteria bilheteria;

    /**
     * Abre a porta e cria os executores.
     *
     * @param config configuração
     * @throws IOException se a porta não puder ser aberta
     */
    public Servidor(Config config) throws IOException {
        this.config = config;
        this.servidor = new ServerSocket(config.porta());
        this.poolAtendimento = new ThreadPoolExecutor(config.threadsAtendimento(), config.threadsAtendimento(),
                0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(config.filaAtendimento()),
                Config.threads("atendimento", Thread.NORM_PRIORITY), (tarefa, pool) -> ((Atendimento) tarefa).recusar());
        this.poolPagamento = (ThreadPoolExecutor) Executors.newFixedThreadPool(config.threadsPagamento(),
                Config.threads("pagamento", Thread.NORM_PRIORITY));
        this.agendador = new ScheduledThreadPoolExecutor(2, Config.threads("agendador", Thread.NORM_PRIORITY));
        agendador.setRemoveOnCancelPolicy(true);
        this.porteiro = Executors.newSingleThreadExecutor(Config.threads("porteiro", Thread.MAX_PRIORITY));
        this.bilheteria = new Bilheteria(config, agendador, poolPagamento, this::pararDeAceitar);
        bilheteria.adicionarObservador(evento -> {
            if (!evento.startsWith("STATUS") && !evento.startsWith("MAPA")) {
                System.out.printf("[%-14s] %s%n", Thread.currentThread().getName(), evento);
            }
        });
    }

    /**
     * Agenda abertura, prazo máximo e status; atende até o encerramento e desliga tudo.
     *
     * @throws InterruptedException se interrompido durante o desligamento
     */
    public void executar() throws InterruptedException {
        porteiro.execute(bilheteria::porteiro);
        agendador.schedule(bilheteria::abrirVendas, config.atrasoAberturaSeg(), TimeUnit.SECONDS);
        agendador.schedule(() -> bilheteria.encerrar("tempo-maximo"), config.duracaoMaximaSeg(), TimeUnit.SECONDS);
        agendador.scheduleAtFixedRate(() -> bilheteria.publicarStatus(
                "atendimento=" + descrever(poolAtendimento) + " pagamento=" + descrever(poolPagamento)),
                1, 1, TimeUnit.SECONDS);

        System.out.printf("Servidor na porta %d | %d vagas | prazo %ds | %s | vendas abrem em %ds%n",
                config.porta(), config.vagasAreaCompra(), config.prazoCompraSeg(), config.politicaFila(),
                config.atrasoAberturaSeg());
        while (!servidor.isClosed()) {
            try {
                Socket socket = servidor.accept();
                poolAtendimento.execute(new Atendimento(new Conexao(socket), bilheteria));
            } catch (IOException e) {
                if (!servidor.isClosed()) {
                    System.err.println("Falha ao aceitar conexão: " + e.getMessage());
                }
            }
        }
        desligar();
    }

    /** @param motivo encerra as vendas antes do esgotamento ou do tempo máximo */
    public void encerrar(String motivo) {
        bilheteria.encerrar(motivo);
    }

    private void pararDeAceitar() {
        try {
            servidor.close();
        } catch (IOException ignorada) {
            // o laço de accept termina de qualquer forma
        }
    }

    private void desligar() throws InterruptedException {
        agendador.shutdownNow();
        porteiro.shutdownNow();
        aguardar(poolPagamento);
        bilheteria.fecharConexoes();
        aguardar(poolAtendimento);
        System.out.print(bilheteria.relatorio());
    }

    private static void aguardar(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
    }

    /** Formato: ativas/threads/fila/concluídas. */
    private static String descrever(ThreadPoolExecutor pool) {
        return pool.getActiveCount() + "/" + pool.getPoolSize() + "/" + pool.getQueue().size()
                + "/" + pool.getCompletedTaskCount();
    }
}
