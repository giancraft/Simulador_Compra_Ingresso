package ingressos.servidor.fila;

import ingressos.servidor.dominio.AreaCompra;
import ingressos.servidor.dominio.Sessao;

import java.util.function.Predicate;

/**
 * Libera a entrada na área de compra respeitando a ordem da fila: primeiro obtém uma vaga
 * da {@link AreaCompra}, depois chama o próximo da fila.
 */
public final class Porteiro implements Runnable {

    private final FilaVirtual fila;
    private final AreaCompra area;
    private final Predicate<Sessao> admitir;

    /**
     * @param fila    fila virtual
     * @param area    área de compra
     * @param admitir coloca a sessão na área de compra; {@code false} se ela já saiu
     */
    public Porteiro(FilaVirtual fila, AreaCompra area, Predicate<Sessao> admitir) {
        this.fila = fila;
        this.area = area;
        this.admitir = admitir;
    }

    @Override
    public void run() {
        try {
            fila.aguardarAbertura();
            while (!Thread.currentThread().isInterrupted()) {
                area.ocuparVaga();
                if (!admitir.test(fila.proximo())) {
                    area.liberarVaga();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
