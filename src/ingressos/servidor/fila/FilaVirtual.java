package ingressos.servidor.fila;

import ingressos.servidor.dominio.Sessao;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static ingressos.servidor.dominio.Sessao.Estado;

/**
 * Sala de espera + fila virtual. É um monitor: {@link #registrar} e {@link #abrir} usam o mesmo
 * bloqueio, então nenhum cliente que chega no instante do sorteio fica perdido na sala.
 * Quem espera a abertura usa {@code wait()}; a abertura acorda com {@code notifyAll()}.
 */
public final class FilaVirtual {

    private final List<Sessao> salaEspera = new ArrayList<>();
    private final BlockingQueue<Sessao> fila;
    private final PoliticaFila politica;
    private final Random random;
    private boolean aberta;

    /**
     * @param capacidade máximo de clientes na fila
     * @param politica   ordem aplicada à sala de espera na abertura
     * @param random     gerador usado pela política
     */
    public FilaVirtual(int capacidade, PoliticaFila politica, Random random) {
        this.fila = new ArrayBlockingQueue<>(capacidade);
        this.politica = politica;
        this.random = random;
    }

    /**
     * Coloca o cliente na sala de espera (antes da abertura) ou no fim da fila (depois).
     *
     * @param sessao sessão recém identificada
     * @return 0 se ficou na sala de espera, ou a posição na fila
     */
    public synchronized int registrar(Sessao sessao) {
        if (aberta) {
            sessao.transitar(Estado.CONECTADO, Estado.NA_FILA);
            fila.add(sessao);
            return fila.size();
        }
        sessao.transitar(Estado.CONECTADO, Estado.SALA_ESPERA);
        salaEspera.add(sessao);
        return 0;
    }

    /**
     * Abre as vendas: ordena a sala pela política, move todos para a fila e acorda quem espera.
     *
     * @return sessões na ordem em que entraram na fila
     */
    public synchronized List<Sessao> abrir() {
        politica.ordenar(salaEspera, random);
        List<Sessao> ordem = new ArrayList<>();
        for (Sessao sessao : salaEspera) {
            if (sessao.transitar(Estado.SALA_ESPERA, Estado.NA_FILA)) {
                fila.add(sessao);
                ordem.add(sessao);
            }
        }
        salaEspera.clear();
        aberta = true;
        notifyAll();
        return ordem;
    }

    /**
     * Bloqueia até a abertura das vendas.
     *
     * @throws InterruptedException se interrompida enquanto espera
     */
    public synchronized void aguardarAbertura() throws InterruptedException {
        while (!aberta) {
            wait();
        }
    }

    /**
     * Bloqueia até haver alguém na fila. Fora do monitor para não travar {@link #registrar}.
     *
     * @return primeira sessão da fila
     * @throws InterruptedException se o servidor estiver encerrando
     */
    public Sessao proximo() throws InterruptedException {
        return fila.take();
    }

    /** @param sessao sessão que saiu antes da vez */
    public void remover(Sessao sessao) {
        synchronized (this) {
            salaEspera.remove(sessao);
        }
        fila.remove(sessao);
    }

    /** @return quantidade de clientes na fila */
    public int tamanho() {
        return fila.size();
    }

    /** @return política em uso */
    public PoliticaFila politica() {
        return politica;
    }
}
