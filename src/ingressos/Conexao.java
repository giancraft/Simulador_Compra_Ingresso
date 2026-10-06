package ingressos;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Socket TCP com leitura ({@link BufferedReader}) e escrita ({@link PrintStream}) por linha.
 * O envio é sincronizado porque várias threads escrevem na mesma conexão.
 */
public final class Conexao implements Closeable {

    private final Socket socket;
    private final BufferedReader entrada;
    private final PrintStream saida;

    /**
     * @param socket socket já conectado
     * @throws IOException se os fluxos não puderem ser abertos
     */
    public Conexao(Socket socket) throws IOException {
        this.socket = socket;
        this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.saida = new PrintStream(socket.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    /** @param mensagem mensagem enviada como uma linha */
    public synchronized void enviar(Mensagem mensagem) {
        saida.println(mensagem);
    }

    /**
     * @param tipo tipo da mensagem
     * @param args argumentos
     */
    public void enviar(Mensagem.Tipo tipo, Object... args) {
        enviar(Mensagem.de(tipo, args));
    }

    /**
     * Bloqueia até a próxima linha não vazia.
     *
     * @return mensagem recebida ou {@code null} se o outro lado fechou
     * @throws IOException se a conexão falhar
     * @throws IllegalArgumentException se o tipo for desconhecido
     */
    public Mensagem receber() throws IOException {
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (!linha.isBlank()) {
                return Mensagem.parse(linha);
            }
        }
        return null;
    }

    /** Fecha o socket, desbloqueando quem estiver em {@link #receber()}. */
    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignorada) {
            // nada a recuperar ao fechar
        }
    }
}
