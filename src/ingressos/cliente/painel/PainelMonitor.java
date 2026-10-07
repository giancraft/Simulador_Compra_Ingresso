package ingressos.cliente.painel;

import ingressos.comum.Conexao;
import ingressos.comum.Config;
import ingressos.comum.Mensagem;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static ingressos.comum.Mensagem.Tipo.MONITORAR;

/**
 * Janela de monitoramento: conecta com {@code MONITORAR}, interpreta os eventos do servidor e
 * atualiza o {@link CabecalhoPainel}, a {@link GradeAssentos}, o {@link PainelGuiches} e o log.
 * Uma thread lê o socket e repassa cada mensagem à EDT com {@link SwingUtilities#invokeLater}.
 */
public final class PainelMonitor {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JFrame janela = new JFrame("Monitor — Venda de Ingressos");
    private final GradeAssentos grade = new GradeAssentos();
    private final PainelGuiches guiches = new PainelGuiches();
    private final CabecalhoPainel cabecalho = new CabecalhoPainel();
    private final JTextArea log = new JTextArea(12, 80);

    private PainelMonitor() {
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        guiches.setPreferredSize(new Dimension(320, 0));
        janela.setLayout(new BorderLayout());
        janela.add(cabecalho, BorderLayout.NORTH);
        janela.add(grade, BorderLayout.CENTER);
        janela.add(guiches, BorderLayout.EAST);
        janela.add(new JScrollPane(log), BorderLayout.SOUTH);
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        janela.setSize(1100, 750);
        janela.setLocationRelativeTo(null);
    }

    /**
     * Abre a janela, conecta ao servidor e bloqueia até a conexão terminar.
     *
     * @param config host e porta do servidor
     * @throws Exception se não conseguir conectar
     */
    public static void abrir(Config config) throws Exception {
        PainelMonitor painel = new PainelMonitor();
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
            case CONFIG -> {
                grade.configurar(Integer.parseInt(m.arg(0)), Integer.parseInt(m.arg(1)));
                guiches.configurar(Integer.parseInt(m.arg(2)), Integer.parseInt(m.arg(3)));
                cabecalho.configurar(Integer.parseInt(m.arg(0)) * Integer.parseInt(m.arg(1)), Integer.parseInt(m.arg(2)));
                janela.revalidate();
            }
            case MAPA -> grade.pintarMapa(m.args());
            case EVT -> tratarEvento(m.arg(0), m.arg(1), m.arg(2), m.resto(3));
            default -> { }
        }
    }

    /** Evento no formato {@code TIPO nome assento detalhe...}. */
    private void tratarEvento(String tipo, String nome, String assento, String detalhe) {
        switch (tipo) {
            case "MAPA" -> grade.pintarMapa(List.of(detalhe.split(" ")));
            case "STATUS" -> cabecalho.atualizarStatus(detalhe);
            case "ENTROU_AREA" -> guiches.ocupar(nome);
            case "RESERVOU" -> grade.pintar(assento, GradeAssentos.RESERVADO);
            case "VENDEU" -> {
                guiches.liberar(nome, "comprou " + assento, new Color(0x43A047));
                grade.pintar(assento, GradeAssentos.VENDIDO);
                cabecalho.registrarVenda();
            }
            case "EXPIROU" -> {
                guiches.liberar(nome, "expirou", GradeAssentos.VENDIDO);
                grade.pintar(assento, GradeAssentos.EXPIRADO);
                cabecalho.registrarExpiracao();
            }
            case "RECUSADO" -> {
                guiches.liberar(nome, "pagamento recusado", GradeAssentos.EXPIRADO);
                grade.pintar(assento, GradeAssentos.LIVRE);
                cabecalho.registrarRecusa();
            }
            case "DESCONECTOU" -> {
                guiches.liberar(nome, "desconectou", Color.GRAY);
                cabecalho.registrarDesistencia();
            }
            case "ESGOTADO" -> guiches.liberar(nome, "esgotado", Color.GRAY);
            default -> { }
        }
        if (!tipo.equals("MAPA") && !tipo.equals("STATUS")) {
            registrar(String.format("%-12s %-10s %-4s %s", tipo, nome, assento, detalhe));
        }
    }

    private void registrar(String linha) {
        log.append(LocalTime.now().format(HORA) + "  " + linha + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }
}
