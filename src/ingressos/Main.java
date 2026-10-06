package ingressos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Ponto de entrada. Sem argumentos sobe servidor, painel e robôs na mesma JVM, cada um na sua
 * thread, conversando por socket em localhost. Com argumento ({@code servidor}, {@code painel}
 * ou {@code clientes}) sobe só uma parte, para rodar em terminais ou máquinas separadas.
 */
public final class Main {

    private Main() {
    }

    /**
     * @param args vazio ou {@code servidor|painel|clientes}
     * @throws Exception se alguma parte não puder iniciar
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.carregar();
        switch (args.length == 0 ? "tudo" : args[0]) {
            case "servidor" -> new Servidor(config).executar();
            case "painel" -> Painel.abrir(config);
            case "clientes" -> simularClientes(config);
            case "tudo" -> tudo(config);
            default -> System.err.println("uso: Main [servidor|painel|clientes]");
        }
    }

    private static void tudo(Config config) throws Exception {
        Servidor servidor = new Servidor(config); // a porta já fica aberta antes dos clientes
        Thread threadServidor = new Thread(() -> {
            try {
                servidor.executar();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "servidor");
        threadServidor.start();
        new Thread(() -> {
            try {
                Painel.abrir(config);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "painel").start();

        simularClientes(config);
        servidor.encerrar("simulacao-concluida"); // sem efeito se já esgotou
        threadServidor.join();
    }

    /** Dispara os robôs em duas ondas: a primeira cai na sala de espera, a segunda vai para o fim da fila. */
    private static void simularClientes(Config config) throws Exception {
        ExecutorService robos = Executors.newFixedThreadPool(config.numRobos(), Config.threads("robo", Thread.NORM_PRIORITY));
        List<Future<String>> resultados = new ArrayList<>();
        for (int i = 1; i <= config.numRobos(); i++) {
            if (i == config.robosPrimeiraOnda() + 1) {
                TimeUnit.SECONDS.sleep(config.intervaloOndasSeg());
            }
            resultados.add(robos.submit(new Robo(i, config)));
        }
        robos.shutdown();
        robos.awaitTermination(1, TimeUnit.HOURS);

        Map<String, Integer> resumo = new TreeMap<>();
        for (Future<String> resultado : resultados) {
            resumo.merge(resultado.get(), 1, Integer::sum);
        }
        System.out.println("===== Resumo dos robôs =====");
        resumo.forEach((desfecho, total) -> System.out.printf("%-16s %d%n", desfecho, total));
    }
}
