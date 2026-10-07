package ingressos.servidor.evento;

import java.util.ArrayList;
import java.util.List;

/**
 * Sujeito do padrão Observer. Novos interessados (log, painel, estatísticas) entram por
 * {@link #adicionar} sem alterar quem publica.
 */
public final class Notificador {

    private final List<Observador> observadores = new ArrayList<>();

    /** @param observador novo interessado */
    public synchronized void adicionar(Observador observador) {
        observadores.add(observador);
    }

    /** @param observador interessado a remover */
    public synchronized void remover(Observador observador) {
        observadores.remove(observador);
    }

    /** @param evento evento entregue a todos os observadores, na thread de quem publica */
    public void publicar(Evento evento) {
        List<Observador> copia;
        synchronized (this) {
            copia = List.copyOf(observadores);
        }
        copia.forEach(o -> o.notificar(evento));
    }
}
