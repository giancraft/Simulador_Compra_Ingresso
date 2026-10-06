package ingressos;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Mapa de assentos com estados L (livre), R (reservado) e V (vendido), protegido por {@link ReentrantLock}. */
public final class Assentos {

    private static final class Assento {
        char estado = 'L';
        long dono;
    }

    private final Map<String, Assento> assentos = new LinkedHashMap<>();
    private final Lock trava = new ReentrantLock();

    /**
     * @param linhas  fileiras (A, B, C...), no máximo 26
     * @param colunas assentos por fileira
     */
    public Assentos(int linhas, int colunas) {
        for (int l = 0; l < linhas; l++) {
            for (int c = 1; c <= colunas; c++) {
                assentos.put((char) ('A' + l) + String.valueOf(c), new Assento());
            }
        }
    }

    /**
     * @param id     assento, ex.: {@code A1}
     * @param sessao sessão que reserva
     * @return {@code true} se estava livre e foi reservado
     */
    public boolean reservar(String id, long sessao) {
        trava.lock();
        try {
            Assento assento = assentos.get(id.toUpperCase(Locale.ROOT));
            if (assento == null || assento.estado != 'L') {
                return false;
            }
            assento.estado = 'R';
            assento.dono = sessao;
            return true;
        } finally {
            trava.unlock();
        }
    }

    /** @param id assento reservado que passa a vendido */
    public void vender(String id) {
        trava.lock();
        try {
            assentos.get(id).estado = 'V';
        } finally {
            trava.unlock();
        }
    }

    /**
     * Devolve o assento a livre se ainda estiver reservado pela sessão.
     *
     * @param id     assento
     * @param sessao sessão que o reservou
     */
    public void liberar(String id, long sessao) {
        trava.lock();
        try {
            Assento assento = assentos.get(id);
            if (assento.estado == 'R' && assento.dono == sessao) {
                assento.estado = 'L';
            }
        } finally {
            trava.unlock();
        }
    }

    /** @return mapa no formato {@code A1:L A2:R A3:V ...} */
    public String mapa() {
        trava.lock();
        try {
            StringJoiner mapa = new StringJoiner(" ");
            assentos.forEach((id, a) -> mapa.add(id + ":" + a.estado));
            return mapa.toString();
        } finally {
            trava.unlock();
        }
    }

    /** @return quantidade de assentos vendidos */
    public int vendidos() {
        trava.lock();
        try {
            return (int) assentos.values().stream().filter(a -> a.estado == 'V').count();
        } finally {
            trava.unlock();
        }
    }

    /** @return total de assentos */
    public int capacidade() {
        return assentos.size();
    }
}
