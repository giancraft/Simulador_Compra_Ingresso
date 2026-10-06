package ingressos;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Padrão Strategy: ordem em que a sala de espera entra na fila quando as vendas abrem. */
public enum PoliticaFila {

    /** Embaralha: quem chegou cedo não tem vantagem. */
    SORTEIO {
        @Override
        void ordenar(List<Sessao> sessoes, Random random) {
            Collections.shuffle(sessoes, random);
        }
    },

    /** Mantém a ordem de chegada. */
    FIFO {
        @Override
        void ordenar(List<Sessao> sessoes, Random random) {
        }
    };

    /**
     * @param sessoes sessões em ordem de chegada; reordenadas no lugar
     * @param random  gerador usado pelo sorteio
     */
    abstract void ordenar(List<Sessao> sessoes, Random random);
}
