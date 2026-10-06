package ingressos;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ingressos.Mensagem.Tipo.MONITORAR;

/**
 * Painel Swing que se conecta com {@code MONITORAR} e mostra a venda ao vivo: assentos,
 * guichês da área de compra com o prazo correndo, status dos pools e log.
 * Uma thread lê o socket e repassa cada mensagem à EDT com {@link SwingUtilities#invokeLater}.
 */
public final class Painel {

    private static final Color LIVRE = new Color(0x4CAF50);
    private static final Color RESERVADO = new Color(0xFFC107);
    private static final Color VENDIDO = new Color(0xE53935);
    private static final Color EXPIRADO = new Color(0xFF7043);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JFrame janela = new JFrame("Monitor — Venda de Ingressos");
    private final JPanel grade = new JPanel();
    private final JPanel painelGuiches = new JPanel();
    private final JLabel contadores = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JTextArea log = new JTextArea(12, 80);
    private final Map<String, JLabel> assentos = new HashMap<>();
    private final Map<String, Integer> totais = new HashMap<>();
    private JProgressBar[] guiches = new JProgressBar[0];
    private String[] ocupantes = new String[0];
    private long[] prazoFinal = new long[0];
    private int prazoMs;

    private Painel() {
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        contadores.setFont(contadores.getFont().deriveFont(Font.BOLD, 14f));
        painelGuiches.setPreferredSize(new Dimension(320, 0));
        JPanel topo = new JPanel(new GridLayout(2, 1));
        topo.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        topo.add(contadores);
        topo.add(status);

        janela.setLayout(new BorderLayout());
        janela.add(topo, BorderLayout.NORTH);
        janela.add(grade, BorderLayout.CENTER);
        janela.add(painelGuiches, BorderLayout.EAST);
        janela.add(new JScrollPane(log), BorderLayout.SOUTH);
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        janela.setSize(1100, 750);
        janela.setLocationRelativeTo(null);
        new Timer(200, e -> animarGuiches()).start();
        atualizarContadores();
    }

    /**
     * Abre a janela, conecta ao servidor e bloqueia até a conexão terminar.
     *
     * @param config host e porta do servidor
     * @throws Exception se não conseguir conectar
     */
    public static void abrir(Config config) throws Exception {
        Painel painel = new Painel();
        SwingUtilities.invokeAndWait(() -> painel.janela.setVisible(true));
        try (Socket socket = new Socket(config.host(), config.porta()); Conexao conexao = new Conexao(socket)) {
            conexao.enviar(MONITORAR);
            Thread leitor = new Thread(() -> painel.ler(conexao), "leitor-painel");
            leitor.start();
            leitor.join();
        }
    }

    private void ler(Conexao conexao) {
        try {
            Mensagem m;
            while ((m = conexao.receber()) != null) {
                Mensagem recebida = m;
                SwingUtilities.invokeLater(() -> tratar(recebida));
            }
        } catch (IOException e) {
            // servidor encerrou
        }
        SwingUtilities.invokeLater(() -> registrar("*** conexão com o servidor encerrada ***"));
    }

    private void tratar(Mensagem m) {
        switch (m.tipo()) {
            case CONFIG -> configurar(Integer.parseInt(m.arg(0)), Integer.parseInt(m.arg(1)),
                    Integer.parseInt(m.arg(2)), Integer.parseInt(m.arg(3)));
            case MAPA -> pintarMapa(m.args());
            case EVT -> tratarEvento(m.arg(0), m.arg(1), m.arg(2), m.resto(3));
            default -> { }
        }
    }

    /** Evento no formato {@code TIPO nome assento detalhe...}. */
    private void tratarEvento(String tipo, String nome, String assento, String detalhe) {
        switch (tipo) {
            case "MAPA" -> pintarMapa(List.of(detalhe.split(" ")));
            case "STATUS" -> status.setText("Pools (ativas/threads/fila/concluídas): " + detalhe);
            case "ENTROU_AREA" -> ocupar(nome);
            case "RESERVOU" -> pintar(assento, RESERVADO);
            case "VENDEU" -> {
                liberar(nome, "comprou " + assento, new Color(0x43A047));
                pintar(assento, VENDIDO);
                contar("Vendidos");
            }
            case "EXPIROU" -> {
                liberar(nome, "expirou", VENDIDO);
                pintar(assento, EXPIRADO);
                contar("Expirados");
            }
            case "RECUSADO" -> {
                liberar(nome, "pagamento recusado", EXPIRADO);
                pintar(assento, LIVRE);
                contar("Recusados");
            }
            case "DESCONECTOU" -> {
                liberar(nome, "desconectou", Color.GRAY);
                contar("Desconectados");
            }
            case "ESGOTADO" -> liberar(nome, "esgotado", Color.GRAY);
            default -> { }
        }
        if (!tipo.equals("MAPA") && !tipo.equals("STATUS")) {
            registrar(String.format("%-12s %-10s %-4s %s", tipo, nome, assento, detalhe));
        }
    }

    private void configurar(int linhas, int colunas, int vagas, int prazoSeg) {
        grade.setLayout(new GridLayout(linhas, colunas, 3, 3));
        grade.setBorder(BorderFactory.createTitledBorder("Palco ↑  Assentos"));
        for (int l = 0; l < linhas; l++) {
            for (int c = 1; c <= colunas; c++) {
                JLabel rotulo = new JLabel((char) ('A' + l) + String.valueOf(c), SwingConstants.CENTER);
                rotulo.setOpaque(true);
                rotulo.setForeground(Color.WHITE);
                rotulo.setBackground(LIVRE);
                assentos.put(rotulo.getText(), rotulo);
                grade.add(rotulo);
            }
        }
        prazoMs = prazoSeg * 1000;
        guiches = new JProgressBar[vagas];
        ocupantes = new String[vagas];
        prazoFinal = new long[vagas];
        painelGuiches.setLayout(new GridLayout(vagas, 1, 4, 4));
        painelGuiches.setBorder(BorderFactory.createTitledBorder("Área de compra (" + vagas + " vagas)"));
        for (int i = 0; i < vagas; i++) {
            guiches[i] = new JProgressBar(0, prazoMs);
            guiches[i].setStringPainted(true);
            guiches[i].setString("livre");
            painelGuiches.add(guiches[i]);
        }
        janela.revalidate();
    }

    private void pintarMapa(List<String> mapa) {
        for (String token : mapa) {
            String id = token.substring(0, token.indexOf(':'));
            pintar(id, switch (token.charAt(token.length() - 1)) {
                case 'R' -> RESERVADO;
                case 'V' -> VENDIDO;
                default -> LIVRE;
            });
        }
    }

    private void pintar(String id, Color cor) {
        JLabel rotulo = assentos.get(id);
        if (rotulo != null) {
            rotulo.setBackground(cor);
        }
    }

    private void ocupar(String nome) {
        for (int i = 0; i < guiches.length; i++) {
            if (ocupantes[i] == null) {
                ocupantes[i] = nome;
                prazoFinal[i] = System.currentTimeMillis() + prazoMs;
                guiches[i].setForeground(new Color(0x1E88E5));
                return;
            }
        }
    }

    private void liberar(String nome, String resultado, Color cor) {
        for (int i = 0; i < guiches.length; i++) {
            if (nome.equals(ocupantes[i])) {
                ocupantes[i] = null;
                guiches[i].setForeground(cor);
                guiches[i].setValue(prazoMs);
                guiches[i].setString(nome + " — " + resultado);
                return;
            }
        }
    }

    /** Barra de cada guichê esvazia conforme o prazo do cliente acaba. */
    private void animarGuiches() {
        long agora = System.currentTimeMillis();
        for (int i = 0; i < guiches.length; i++) {
            if (ocupantes[i] != null) {
                long resta = Math.max(0, prazoFinal[i] - agora);
                guiches[i].setValue((int) resta);
                guiches[i].setString(String.format("%s — %.1fs", ocupantes[i], resta / 1000.0));
            }
        }
    }

    private void contar(String chave) {
        totais.merge(chave, 1, Integer::sum);
        atualizarContadores();
    }

    private void atualizarContadores() {
        contadores.setText(String.format("Vendidos: %d   Expirados: %d   Recusados: %d   Desconectados: %d",
                totais.getOrDefault("Vendidos", 0), totais.getOrDefault("Expirados", 0),
                totais.getOrDefault("Recusados", 0), totais.getOrDefault("Desconectados", 0)));
    }

    private void registrar(String linha) {
        log.append(LocalTime.now().format(HORA) + "  " + linha + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }
}
