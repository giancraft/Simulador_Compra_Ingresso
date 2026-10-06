package ingressos;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Configuração imutável lida de {@code ingressos.properties}; chaves ausentes usam o padrão. */
public record Config(
        String host, int porta, int linhas, int colunas,
        int vagasAreaCompra, int prazoCompraSeg, int atrasoAberturaSeg, int duracaoMaximaSeg,
        PoliticaFila politicaFila, Long sementeSorteio,
        double probRecusaPagamento, int latenciaPagamentoMinMs, int latenciaPagamentoMaxMs,
        int threadsAtendimento, int filaAtendimento, int threadsPagamento,
        int numRobos, int robosPrimeiraOnda, int intervaloOndasSeg, int tempoHumanoMinSeg, int tempoHumanoMaxSeg) {

    /**
     * @return configuração lida do diretório de execução
     * @throws IOException se o arquivo existir e não puder ser lido
     */
    public static Config carregar() throws IOException {
        Properties p = new Properties();
        Path arquivo = Path.of("ingressos.properties");
        if (Files.exists(arquivo)) {
            try (Reader leitor = Files.newBufferedReader(arquivo)) {
                p.load(leitor);
            }
        }
        String semente = p.getProperty("sementeSorteio", "").trim();
        return new Config(
                p.getProperty("host", "localhost"), inteiro(p, "porta", 5000),
                inteiro(p, "linhas", 10), inteiro(p, "colunas", 10),
                inteiro(p, "vagasAreaCompra", 5), inteiro(p, "prazoCompraSeg", 8),
                inteiro(p, "atrasoAberturaSeg", 10), inteiro(p, "duracaoMaximaSeg", 600),
                PoliticaFila.valueOf(p.getProperty("politicaFila", "SORTEIO").trim()),
                semente.isEmpty() ? null : Long.valueOf(semente),
                Double.parseDouble(p.getProperty("probRecusaPagamento", "0.1")),
                inteiro(p, "latenciaPagamentoMinMs", 300), inteiro(p, "latenciaPagamentoMaxMs", 1200),
                inteiro(p, "threadsAtendimento", 250), inteiro(p, "filaAtendimento", 50),
                inteiro(p, "threadsPagamento", 3),
                inteiro(p, "numRobos", 200), inteiro(p, "robosPrimeiraOnda", 150), inteiro(p, "intervaloOndasSeg", 12),
                inteiro(p, "tempoHumanoMinSeg", 2), inteiro(p, "tempoHumanoMaxSeg", 11));
    }

    private static int inteiro(Properties p, String chave, int padrao) {
        return Integer.parseInt(p.getProperty(chave, String.valueOf(padrao)).trim());
    }

    /**
     * Fábrica que nomeia as threads de um pool ({@code atendimento-3}) e define a prioridade.
     *
     * @param prefixo    prefixo do nome
     * @param prioridade prioridade de 1 a 10
     * @return fábrica de threads
     */
    public static ThreadFactory threads(String prefixo, int prioridade) {
        AtomicInteger contador = new AtomicInteger();
        return tarefa -> {
            Thread thread = new Thread(tarefa, prefixo + "-" + contador.incrementAndGet());
            thread.setPriority(prioridade);
            return thread;
        };
    }
}
