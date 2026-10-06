package ingressos;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

import static ingressos.Mensagem.Tipo.*;

/**
 * Cliente simulado. Um único laço lê mensagens e reage; o tempo humano de preenchimento
 * é sorteado aqui, o servidor só conhece o prazo.
 */
public final class Robo implements Callable<String> {

    private final Config config;
    private final String nome;
    private final String cpf;
    private final long tempoHumanoMs;

    /**
     * @param numero número do robô (gera nome e CPF únicos)
     * @param config configuração
     */
    public Robo(int numero, Config config) {
        this.config = config;
        this.nome = "robo" + numero;
        this.cpf = String.valueOf(10_000_000_000L + numero);
        this.tempoHumanoMs = ThreadLocalRandom.current()
                .nextLong(config.tempoHumanoMinSeg() * 1000L, config.tempoHumanoMaxSeg() * 1000L + 1);
    }

    /** @return desfecho: COMPROU, EXPIROU, RECUSADO, ESGOTADO, LOTADO ou CONEXAO_PERDIDA */
    @Override
    public String call() throws InterruptedException {
        try (Socket socket = new Socket(config.host(), config.porta()); Conexao conexao = new Conexao(socket)) {
            conexao.enviar(ENTRAR, nome, cpf);
            Mensagem m;
            while ((m = conexao.receber()) != null) {
                switch (m.tipo()) {
                    case SUA_VEZ, INDISPONIVEL -> conexao.enviar(LISTAR);
                    case MAPA -> escolherAssento(conexao, m.args());
                    case RESERVADO -> {
                        Thread.sleep(tempoHumanoMs);
                        conexao.enviar(PAGAR);
                    }
                    case COMPRA_OK -> { return "COMPROU"; }
                    case TEMPO_ESGOTADO -> { return "EXPIROU"; }
                    case PAGAMENTO_RECUSADO -> { return "RECUSADO"; }
                    case ESGOTADO, LOTADO, CPF_EM_USO -> { return m.tipo().name(); }
                    default -> { }
                }
            }
        } catch (IOException e) {
            // tratado como conexão perdida
        }
        return "CONEXAO_PERDIDA";
    }

    /** Todos querem a frente do palco: sorteia entre os 3 primeiros livres, gerando disputa. */
    private void escolherAssento(Conexao conexao, List<String> mapa) throws InterruptedException {
        List<String> livres = mapa.stream().filter(a -> a.endsWith(":L")).map(a -> a.substring(0, a.indexOf(':'))).toList();
        if (livres.isEmpty()) {
            Thread.sleep(500);
            conexao.enviar(LISTAR);
        } else {
            conexao.enviar(RESERVAR, livres.get(ThreadLocalRandom.current().nextInt(Math.min(3, livres.size()))));
        }
    }
}
