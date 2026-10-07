package ingressos.servidor.evento;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static ingressos.servidor.evento.Evento.Tipo.*;

/** Observador que conta os eventos e monta o relatório final. */
public final class Estatisticas implements Observador {

    private final Map<Evento.Tipo, AtomicInteger> contagem = new EnumMap<>(Evento.Tipo.class);

    /** Cria todos os contadores zerados. */
    public Estatisticas() {
        for (Evento.Tipo tipo : Evento.Tipo.values()) {
            contagem.put(tipo, new AtomicInteger());
        }
    }

    @Override
    public void notificar(Evento evento) {
        contagem.get(evento.tipo()).incrementAndGet();
    }

    /**
     * @param vendidos   assentos vendidos
     * @param capacidade total de assentos
     * @return relatório final em texto
     */
    public String relatorio(int vendidos, int capacidade) {
        int clientes = total(ENTROU);
        int desfechos = total(VENDEU) + total(EXPIROU) + total(RECUSADO) + total(DESCONECTOU) + total(ESGOTADO);
        return """
                ============ RELATÓRIO FINAL ============
                Clientes ................. %d
                Rejeitados (LOTADO) ...... %d
                Ingressos vendidos ....... %d de %d
                Expirados ................ %d
                Pagamentos recusados ..... %d
                Desconectados ............ %d
                Sem ingresso (esgotado) .. %d
                Desfechos / clientes ..... %d / %d %s
                =========================================
                """.formatted(clientes, total(REJEITADO), vendidos, capacidade, total(EXPIROU), total(RECUSADO),
                total(DESCONECTOU), total(ESGOTADO), desfechos, clientes, desfechos == clientes ? "(ok)" : "(DIVERGENTE)");
    }

    private int total(Evento.Tipo tipo) {
        return contagem.get(tipo).get();
    }
}
