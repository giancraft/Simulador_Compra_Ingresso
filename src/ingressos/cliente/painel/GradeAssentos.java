package ingressos.cliente.painel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Grade colorida dos assentos. Usar apenas na EDT. */
final class GradeAssentos extends JPanel {

    static final Color LIVRE = new Color(0x4CAF50);
    static final Color RESERVADO = new Color(0xFFC107);
    static final Color VENDIDO = new Color(0xE53935);
    static final Color EXPIRADO = new Color(0xFF7043);

    private final transient Map<String, JLabel> assentos = new HashMap<>();

    /**
     * @param linhas  fileiras
     * @param colunas assentos por fileira
     */
    void configurar(int linhas, int colunas) {
        setLayout(new GridLayout(linhas, colunas, 3, 3));
        setBorder(BorderFactory.createTitledBorder("Palco ↑  Assentos"));
        for (int l = 0; l < linhas; l++) {
            for (int c = 1; c <= colunas; c++) {
                JLabel rotulo = new JLabel((char) ('A' + l) + String.valueOf(c), SwingConstants.CENTER);
                rotulo.setOpaque(true);
                rotulo.setForeground(Color.WHITE);
                rotulo.setBackground(LIVRE);
                assentos.put(rotulo.getText(), rotulo);
                add(rotulo);
            }
        }
        revalidate();
    }

    /** @param mapa tokens {@code A1:L} vindos do servidor */
    void pintarMapa(List<String> mapa) {
        for (String token : mapa) {
            pintar(token.substring(0, token.indexOf(':')), switch (token.charAt(token.length() - 1)) {
                case 'R' -> RESERVADO;
                case 'V' -> VENDIDO;
                default -> LIVRE;
            });
        }
    }

    /**
     * @param id  assento
     * @param cor nova cor (até o próximo mapa)
     */
    void pintar(String id, Color cor) {
        JLabel rotulo = assentos.get(id);
        if (rotulo != null) {
            rotulo.setBackground(cor);
        }
    }
}
