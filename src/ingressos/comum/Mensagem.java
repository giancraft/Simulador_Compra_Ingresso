package ingressos.comum;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Mensagem imutável do protocolo texto: um tipo seguido de argumentos separados por espaço.
 *
 * @param tipo tipo da mensagem
 * @param args argumentos, na ordem em que aparecem na linha
 */
public record Mensagem(Tipo tipo, List<String> args) {

    /** Tipos de mensagem trocados entre clientes e servidor. */
    public enum Tipo {
        ENTRAR, LISTAR, RESERVAR, PAGAR, SAIR, MONITORAR,
        SALA_ESPERA, POSICAO, SUA_VEZ, MAPA, RESERVADO, INDISPONIVEL, COMPRA_OK,
        PAGAMENTO_RECUSADO, TEMPO_ESGOTADO, ESGOTADO, LOTADO, CPF_EM_USO, ATE_LOGO, CONFIG, EVT, ERRO
    }

    public Mensagem {
        args = List.copyOf(args);
    }

    /**
     * @param tipo tipo da mensagem
     * @param args argumentos, convertidos com {@link String#valueOf(Object)}
     * @return nova mensagem
     */
    public static Mensagem de(Tipo tipo, Object... args) {
        return new Mensagem(tipo, Arrays.stream(args).map(String::valueOf).toList());
    }

    /**
     * @param linha linha recebida do socket
     * @return mensagem correspondente
     * @throws IllegalArgumentException se o tipo não existir no protocolo
     */
    public static Mensagem parse(String linha) {
        String[] partes = linha.trim().split("\\s+");
        Tipo tipo = Tipo.valueOf(partes[0].toUpperCase(Locale.ROOT));
        return new Mensagem(tipo, Arrays.asList(partes).subList(1, partes.length));
    }

    /**
     * @param indice posição do argumento
     * @return argumento na posição
     */
    public String arg(int indice) {
        return args.get(indice);
    }

    /**
     * @param inicio primeiro argumento incluído
     * @return argumentos a partir de {@code inicio}, unidos por espaço
     */
    public String resto(int inicio) {
        return inicio >= args.size() ? "" : String.join(" ", args.subList(inicio, args.size()));
    }

    @Override
    public String toString() {
        return args.isEmpty() ? tipo.name() : tipo + " " + String.join(" ", args);
    }
}
