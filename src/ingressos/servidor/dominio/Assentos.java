package ingressos.servidor.dominio;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Mapa de assentos da casa de show, com transições protegidas por {@link ReentrantLock}. */
public final class Assentos {

    /** Situação de um assento, com a sigla usada no protocolo. */
    public enum Estado {
        LIVRE('L'), RESERVADO('R'), VENDIDO('V');

        private final char sigla;

        Estado(char sigla) {
            this.sigla = sigla;
        }
    }

    private static final class Assento {
        Estado estado = Estado.LIVRE;
        long dono;
    }

    private final Map<String, Assento> assentos = new LinkedHashMap<>();
    private final Lock trava = new ReentrantLock();
    private final int linhas;
    private final int colunas;

    /**
     * @param linhas  fileiras (A, B, C...), no máximo 26
     * @param colunas assentos por fileira
     */
    public Assentos(int linhas, int colunas) {
        this.linhas = linhas;
        this.colunas = colunas;
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
            if (assento == null || assento.estado != Estado.LIVRE) {
                return false;
            }
            assento.estado = Estado.RESERVADO;
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
            assentos.get(id).estado = Estado.VENDIDO;
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
            if (assento.estado == Estado.RESERVADO && assento.dono == sessao) {
                assento.estado = Estado.LIVRE;
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
            assentos.forEach((id, a) -> mapa.add(id + ":" + a.estado.sigla));
            return mapa.toString();
        } finally {
            trava.unlock();
        }
    }

    /** @return quantidade de assentos vendidos */
    public int vendidos() {
        trava.lock();
        try {
            return (int) assentos.values().stream().filter(a -> a.estado == Estado.VENDIDO).count();
        } finally {
            trava.unlock();
        }
    }

    /** @return {@code true} se todos os assentos foram vendidos */
    public boolean esgotados() {
        return vendidos() == capacidade();
    }

    /** @return quantidade de fileiras */
    public int linhas() {
        return linhas;
    }

    /** @return assentos por fileira */
    public int colunas() {
        return colunas;
    }

    /** @return total de assentos */
    public int capacidade() {
        return assentos.size();
    }
}
