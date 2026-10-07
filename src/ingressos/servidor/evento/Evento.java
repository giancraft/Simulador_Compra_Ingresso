package ingressos.servidor.evento;

/**
 * Acontecimento da venda. {@link #toString()} gera o formato enviado ao painel:
 * {@code TIPO nome assento detalhe}.
 *
 * @param tipo    tipo do evento
 * @param nome    cliente envolvido ou {@code -}
 * @param assento assento envolvido ou {@code -}
 * @param detalhe texto livre, pode ser vazio
 */
public record Evento(Tipo tipo, String nome, String assento, String detalhe) {

    /** Tipos de evento publicados pelo servidor. */
    public enum Tipo {
        ENTROU, SORTEIO, ENTROU_AREA, RESERVOU, VENDEU, EXPIROU, RECUSADO,
        DESCONECTOU, ESGOTADO, REJEITADO, ENCERRADO, STATUS, MAPA
    }

    /**
     * @param tipo    tipo
     * @param nome    cliente
     * @param assento assento ou {@code null}
     * @return evento de um cliente
     */
    public static Evento de(Tipo tipo, String nome, String assento) {
        return new Evento(tipo, nome, assento == null ? "-" : assento, "");
    }

    /**
     * @param tipo    tipo
     * @param detalhe descrição
     * @return evento do sistema, sem cliente
     */
    public static Evento sistema(Tipo tipo, String detalhe) {
        return new Evento(tipo, "-", "-", detalhe);
    }

    /**
     * @param detalhe descrição adicional
     * @return cópia com o detalhe informado
     */
    public Evento com(String detalhe) {
        return new Evento(tipo, nome, assento, detalhe);
    }

    @Override
    public String toString() {
        return (tipo + " " + nome + " " + assento + " " + detalhe).trim();
    }
}
