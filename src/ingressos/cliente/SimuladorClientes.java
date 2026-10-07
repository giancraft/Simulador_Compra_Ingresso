package ingressos.cliente;

import ingressos.comum.Config;
import ingressos.comum.FabricaThreads;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Dispara os clientes simulados num {@link ExecutorService} em duas ondas: a primeira cai na sala de espera,
 * a segunda chega após a abertura e vai para o fim da fila.
 */
public final class SimuladorClientes {

    private final Config config;

    /** @param config quantidade de clientes, ondas e endereço do servidor */
    public SimuladorClientes(Config config) {
        this.config = config;
    }

    /**
     * Executa todos os clientes, espera terminarem e imprime o resumo.
     *
     * @throws InterruptedException se interrompido enquanto espera
     * @throws ExecutionException   se algum cliente falhar
     */
    public void executar() throws InterruptedException, ExecutionException {
        ExecutorService clientes = Executors.newFixedThreadPool(config.numClientes(), new FabricaThreads("cliente"));
        List<Future<String>> resultados = new ArrayList<>();
        for (int i = 1; i <= config.numClientes(); i++) {
            if (i == config.clientesPrimeiraOnda() + 1) {
                TimeUnit.SECONDS.sleep(config.intervaloOndasSeg());
            }
            resultados.add(clientes.submit(new ClienteSimulado(i, config)));
        }
        clientes.shutdown();
        clientes.awaitTermination(1, TimeUnit.HOURS);

        Map<String, Integer> resumo = new TreeMap<>();
        for (Future<String> resultado : resultados) {
            resumo.merge(resultado.get(), 1, Integer::sum);
        }
        System.out.println("===== Resumo dos clientes =====");
        resumo.forEach((desfecho, total) -> System.out.printf("%-16s %d%n", desfecho, total));
    }
}
