package ingressos.cliente.painel;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.GridLayout;

/** Guichês da área de compra, com a barra do prazo esvaziando. Usar apenas na EDT. */
final class PainelGuiches extends JPanel {

    private JProgressBar[] guiches = new JProgressBar[0];
    private String[] ocupantes = new String[0];
    private long[] prazoFinal = new long[0];
    private int prazoMs;

    /** Inicia a animação das barras (Swing {@link Timer}, roda na EDT). */
    PainelGuiches() {
        new Timer(200, e -> animar()).start();
    }

    /**
     * @param vagas    número de guichês
     * @param prazoSeg prazo de compra
     */
    void configurar(int vagas, int prazoSeg) {
        prazoMs = prazoSeg * 1000;
        guiches = new JProgressBar[vagas];
        ocupantes = new String[vagas];
        prazoFinal = new long[vagas];
        setLayout(new GridLayout(vagas, 1, 4, 4));
        setBorder(BorderFactory.createTitledBorder("Área de compra (" + vagas + " vagas)"));
        for (int i = 0; i < vagas; i++) {
            guiches[i] = new JProgressBar(0, prazoMs);
            guiches[i].setStringPainted(true);
            guiches[i].setString("livre");
            add(guiches[i]);
        }
        revalidate();
    }

    /** @param nome cliente que entrou na área de compra */
    void ocupar(String nome) {
        for (int i = 0; i < guiches.length; i++) {
            if (ocupantes[i] == null) {
                ocupantes[i] = nome;
                prazoFinal[i] = System.currentTimeMillis() + prazoMs;
                guiches[i].setForeground(new Color(0x1E88E5));
                return;
            }
        }
    }

    /**
     * @param nome      cliente que saiu
     * @param resultado texto do desfecho
     * @param cor       cor do desfecho
     */
    void liberar(String nome, String resultado, Color cor) {
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

    private void animar() {
        long agora = System.currentTimeMillis();
        for (int i = 0; i < guiches.length; i++) {
            if (ocupantes[i] != null) {
                long resta = Math.max(0, prazoFinal[i] - agora);
                guiches[i].setValue((int) resta);
                guiches[i].setString(String.format("%s — %.1fs", ocupantes[i], resta / 1000.0));
            }
        }
    }
}
