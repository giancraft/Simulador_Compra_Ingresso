package ingressos.comum;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link ThreadFactory} que nomeia as threads de cada pool ({@code atendimento-3}) e define a prioridade. */
public final class FabricaThreads implements ThreadFactory {

    private final String prefixo;
    private final int prioridade;
    private final AtomicInteger contador = new AtomicInteger();

    /** @param prefixo prefixo do nome das threads */
    public FabricaThreads(String prefixo) {
        this(prefixo, Thread.NORM_PRIORITY);
    }

    /**
     * @param prefixo    prefixo do nome das threads
     * @param prioridade prioridade de 1 a 10
     */
    public FabricaThreads(String prefixo, int prioridade) {
        this.prefixo = prefixo;
        this.prioridade = prioridade;
    }

    @Override
    public Thread newThread(Runnable tarefa) {
        Thread thread = new Thread(tarefa, prefixo + "-" + contador.incrementAndGet());
        thread.setPriority(prioridade);
        return thread;
    }
}
