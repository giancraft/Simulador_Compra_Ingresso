package ingressos.servidor.dominio;

import java.util.concurrent.Semaphore;

/** Área de compra com número fixo de vagas (guichês), controladas por um {@link Semaphore}. */
public final class AreaCompra {

    private final Semaphore vagas;
    private final int total;

    /** @param total quantidade de clientes comprando ao mesmo tempo */
    public AreaCompra(int total) {
        this.vagas = new Semaphore(total);
        this.total = total;
    }

    /**
     * Bloqueia até haver vaga livre.
     *
     * @throws InterruptedException se interrompido enquanto espera
     */
    public void ocuparVaga() throws InterruptedException {
        vagas.acquire();
    }

    /** Devolve uma vaga. */
    public void liberarVaga() {
        vagas.release();
    }

    /** @return vagas livres no momento */
    public int livres() {
        return vagas.availablePermits();
    }

    /** @return total de vagas */
    public int total() {
        return total;
    }
}
