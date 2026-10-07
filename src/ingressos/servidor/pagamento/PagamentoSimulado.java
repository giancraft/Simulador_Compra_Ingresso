package ingressos.servidor.pagamento;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Operadora simulada: cada pagamento roda no pool de pagamento, demora um tempo aleatório
 * e é recusado com certa probabilidade.
 */
public final class PagamentoSimulado implements GatewayPagamento {

    private final ExecutorService pool;
    private final int latenciaMinMs;
    private final int latenciaMaxMs;
    private final double probRecusa;

    /**
     * @param pool          executor que processa os pagamentos
     * @param latenciaMinMs latência mínima
     * @param latenciaMaxMs latência máxima
     * @param probRecusa    probabilidade de recusa, de 0 a 1
     */
    public PagamentoSimulado(ExecutorService pool, int latenciaMinMs, int latenciaMaxMs, double probRecusa) {
        this.pool = pool;
        this.latenciaMinMs = latenciaMinMs;
        this.latenciaMaxMs = latenciaMaxMs;
        this.probRecusa = probRecusa;
    }

    @Override
    public boolean pagar() {
        try {
            return pool.submit(this::processar).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | RejectedExecutionException e) {
            return false;
        }
    }

    private boolean processar() throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Thread.sleep(random.nextInt(latenciaMinMs, latenciaMaxMs + 1));
        return random.nextDouble() >= probRecusa;
    }
}
