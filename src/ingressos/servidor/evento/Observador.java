package ingressos.servidor.evento;

/** Padrão Observer: interessado nos eventos publicados pelo {@link Notificador}. */
@FunctionalInterface
public interface Observador {

    /** @param evento evento ocorrido */
    void notificar(Evento evento);
}
