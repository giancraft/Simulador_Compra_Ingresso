package ingressos.servidor;

import ingressos.comum.Conexao;
import ingressos.comum.Config;
import ingressos.comum.FabricaThreads;
import ingressos.servidor.comando.ComandoFactory;
import ingressos.servidor.dominio.AreaCompra;
import ingressos.servidor.dominio.Assentos;
import ingressos.servidor.dominio.RegistroCpf;
import ingressos.servidor.evento.Estatisticas;
import ingressos.servidor.evento.Evento;
import ingressos.servidor.evento.Notificador;
import ingressos.servidor.fila.FilaVirtual;
import ingressos.servidor.fila.PoliticaFila;
import ingressos.servidor.fila.Porteiro;
import ingressos.servidor.pagamento.PagamentoSimulado;
import ingressos.servidor.venda.Bilheteria;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Servidor de ingressos. Monta os componentes e injeta as dependências (raiz de composição),
 * aceita conexões e entrega cada uma ao pool de atendimento.
 */
public final class Servidor {

    private final Config config;
    private final ServerSocket servidor;
    private final ThreadPoolExecutor poolAtendimento;
    private final ThreadPoolExecutor poolPagamento;
    private final ScheduledThreadPoolExecutor agendador;
    private final ExecutorService porteiro;
    private final Assentos assentos;
    private final FilaVirtual fila;
    private final AreaCompra area;
    private final Estatisticas estatisticas = new Estatisticas();
    private final ComandoFactory comandos = new ComandoFactory();
    private final Bilheteria bilheteria;

    /**
     * Abre a porta, cria os executores e monta a venda.
     *
     * @param config configuração
     * @throws IOException se a porta não puder ser aberta
     */
    public Servidor(Config config) throws IOException {
        this.config = config;
        this.servidor = new ServerSocket(config.porta());
        this.poolAtendimento = new ThreadPoolExecutor(config.threadsAtendimento(), config.threadsAtendimento(),
                0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(config.filaAtendimento()),
                new FabricaThreads("atendimento"), (tarefa, pool) -> ((Atendimento) tarefa).recusar());
        this.poolPagamento = (ThreadPoolExecutor) Executors.newFixedThreadPool(config.threadsPagamento(),
                new FabricaThreads("pagamento"));
        this.agendador = new ScheduledThreadPoolExecutor(2, new FabricaThreads("agendador"));
        agendador.setRemoveOnCancelPolicy(true);
        this.porteiro = Executors.newSingleThreadExecutor(new FabricaThreads("porteiro", Thread.MAX_PRIORITY));

        Random random = config.sementeSorteio() == null ? new Random() : new Random(config.sementeSorteio());
        this.assentos = new Assentos(config.linhas(), config.colunas());
        this.fila = new FilaVirtual(config.threadsAtendimento() + config.filaAtendimento(),
                PoliticaFila.valueOf(config.politicaFila()), random);
        this.area = new AreaCompra(config.vagasAreaCompra());

        Notificador notificador = new Notificador();
        notificador.adicionar(estatisticas);
        notificador.adicionar(Servidor::registrarNoConsole);
        this.bilheteria = new Bilheteria(assentos, fila, area, new RegistroCpf(),
                new PagamentoSimulado(poolPagamento, config.latenciaPagamentoMinMs(), config.latenciaPagamentoMaxMs(),
                        config.probRecusaPagamento()),
                notificador, agendador, config.prazoCompraSeg(), this::pararDeAceitar);
    }

    /**
     * Agenda abertura, prazo máximo e status; atende até o encerramento e desliga tudo.
     *
     * @throws InterruptedException se interrompido durante o desligamento
     */
    public void executar() throws InterruptedException {
        porteiro.execute(new Porteiro(fila, area, bilheteria::admitir));
        agendador.schedule(registrandoFalhas(bilheteria::abrirVendas), config.atrasoAberturaSeg(), TimeUnit.SECONDS);
        agendador.schedule(registrandoFalhas(() -> bilheteria.encerrar("tempo-maximo")), config.duracaoMaximaSeg(), TimeUnit.SECONDS);
        agendador.scheduleAtFixedRate(registrandoFalhas(() -> bilheteria.publicarStatus(
                "atendimento=" + descrever(poolAtendimento) + " pagamento=" + descrever(poolPagamento))),
                1, 1, TimeUnit.SECONDS);

        System.out.printf("Servidor na porta %d | %d vagas | prazo %ds | %s | vendas abrem em %ds%n",
                config.porta(), area.total(), config.prazoCompraSeg(), fila.politica(), config.atrasoAberturaSeg());
        aceitarConexoes();
        desligar();
    }

    /** @param motivo encerra as vendas antes do esgotamento ou do tempo máximo */
    public void encerrar(String motivo) {
        bilheteria.encerrar(motivo);
    }

    private void aceitarConexoes() {
        while (!servidor.isClosed()) {
            try {
                Socket socket = servidor.accept();
                poolAtendimento.execute(new Atendimento(new Conexao(socket), bilheteria, comandos));
            } catch (IOException e) {
                if (!servidor.isClosed()) {
                    System.err.println("Falha ao aceitar conexão: " + e.getMessage());
                }
            }
        }
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
        System.out.print(estatisticas.relatorio(assentos.vendidos(), assentos.capacidade()));
    }

    private static void aguardar(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
    }

    /**
     * O agendador descarta exceções das tarefas em silêncio e, nas periódicas, cancela as
     * próximas execuções. Aqui a falha é exibida e a tarefa continua agendada.
     */
    private static Runnable registrandoFalhas(Runnable tarefa) {
        return () -> {
            try {
                tarefa.run();
            } catch (RuntimeException e) {
                System.err.println("Falha em tarefa agendada: " + e);
                e.printStackTrace();
            }
        };
    }

    private static void registrarNoConsole(Evento evento) {
        if (evento.tipo() != Evento.Tipo.STATUS && evento.tipo() != Evento.Tipo.MAPA) {
            System.out.printf("[%-14s] %s%n", Thread.currentThread().getName(), evento);
        }
    }

    /** Formato: ativas/threads/fila/concluídas. */
    private static String descrever(ThreadPoolExecutor pool) {
        return pool.getActiveCount() + "/" + pool.getPoolSize() + "/" + pool.getQueue().size()
                + "/" + pool.getCompletedTaskCount();
    }
}
