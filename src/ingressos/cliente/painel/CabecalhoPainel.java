package ingressos.cliente.painel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * Cabeçalho do painel: cartões com os números da venda, uma linha com a ocupação dos
 * pools de threads em linguagem simples e a legenda de cores dos assentos. Usar apenas na EDT.
 */
final class CabecalhoPainel extends JPanel {

    private static final Color TEXTO_SECUNDARIO = new Color(0x666666);

    private final JLabel naFila = new JLabel("0");
    private final JLabel comprando = new JLabel("0");
    private final JLabel vendidos = new JLabel("0");
    private final JLabel expirados = new JLabel("0");
    private final JLabel recusados = new JLabel("0");
    private final JLabel desconectados = new JLabel("0");
    private final JLabel threads = new JLabel("Aguardando o servidor...");
    private int capacidade;
    private int vagas;
    private int totalVendidos;

    /** Monta os cartões, a linha de threads e a legenda. */
    CabecalhoPainel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JPanel cartoes = new JPanel(new GridLayout(1, 6, 8, 0));
        cartoes.add(cartao("Na fila", naFila, new Color(0x607D8B)));
        cartoes.add(cartao("Comprando agora", comprando, new Color(0x1E88E5)));
        cartoes.add(cartao("Ingressos vendidos", vendidos, GradeAssentos.VENDIDO));
        cartoes.add(cartao("Tempo esgotado", expirados, GradeAssentos.EXPIRADO));
        cartoes.add(cartao("Pagamento recusado", recusados, new Color(0x8D6E63)));
        cartoes.add(cartao("Desconectados", desconectados, Color.GRAY));

        threads.setForeground(TEXTO_SECUNDARIO);
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.add(threads, BorderLayout.WEST);
        rodape.add(legenda(), BorderLayout.EAST);

        add(cartoes, BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);
    }

    /**
     * @param capacidade total de assentos
     * @param vagas      vagas da área de compra
     */
    void configurar(int capacidade, int vagas) {
        this.capacidade = capacidade;
        this.vagas = vagas;
        vendidos.setText(totalVendidos + " / " + capacidade);
        comprando.setText("0 / " + vagas);
    }

    /** Conta mais um ingresso vendido. */
    void registrarVenda() {
        vendidos.setText(++totalVendidos + " / " + capacidade);
    }

    /** Conta mais uma compra que expirou. */
    void registrarExpiracao() {
        incrementar(expirados);
    }

    /** Conta mais um pagamento recusado. */
    void registrarRecusa() {
        incrementar(recusados);
    }

    /** Conta mais um cliente que caiu ou saiu antes de terminar. */
    void registrarDesistencia() {
        incrementar(desconectados);
    }

    /**
     * @param status texto {@code atendimento=a/p/f/c pagamento=a/p/f/c fila=n vagasLivres=v}, onde
     *               a/p/f/c = threads ativas / threads no pool / tarefas na fila / tarefas concluídas
     */
    void atualizarStatus(String status) {
        Map<String, String> valores = new HashMap<>();
        for (String par : status.split(" ")) {
            String[] chaveValor = par.split("=", 2);
            if (chaveValor.length == 2) {
                valores.put(chaveValor[0], chaveValor[1]);
            }
        }
        naFila.setText(valores.getOrDefault("fila", "0"));
        int livres = Integer.parseInt(valores.getOrDefault("vagasLivres", String.valueOf(vagas)));
        comprando.setText((vagas - livres) + " / " + vagas);

        String[] atendimento = valores.getOrDefault("atendimento", "0/0/0/0").split("/");
        String[] pagamento = valores.getOrDefault("pagamento", "0/0/0/0").split("/");
        threads.setText(String.format(
                "Threads de atendimento: %s ocupadas de %s (%s conexões aguardando)   •   Pagamentos: %s em processamento, %s aguardando",
                atendimento[0], atendimento[1], atendimento[2], pagamento[0], pagamento[2]));
    }

    private static JPanel cartao(String titulo, JLabel valor, Color cor) {
        valor.setFont(valor.getFont().deriveFont(Font.BOLD, 24f));
        valor.setForeground(cor);
        valor.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel rotulo = new JLabel(titulo, SwingConstants.CENTER);
        rotulo.setForeground(TEXTO_SECUNDARIO);

        JPanel cartao = new JPanel(new BorderLayout());
        cartao.setBackground(Color.WHITE);
        cartao.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, cor),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
        cartao.add(valor, BorderLayout.CENTER);
        cartao.add(rotulo, BorderLayout.SOUTH);
        return cartao;
    }

    private static JPanel legenda() {
        JPanel legenda = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legenda.add(item("Livre", GradeAssentos.LIVRE));
        legenda.add(item("Reservado", GradeAssentos.RESERVADO));
        legenda.add(item("Vendido", GradeAssentos.VENDIDO));
        legenda.add(item("Expirou", GradeAssentos.EXPIRADO));
        return legenda;
    }

    private static JPanel item(String texto, Color cor) {
        JLabel cores = new JLabel("■");
        cores.setForeground(cor);
        cores.setFont(cores.getFont().deriveFont(16f));
        JLabel rotulo = new JLabel(texto);
        rotulo.setForeground(TEXTO_SECUNDARIO);
        JPanel item = new JPanel();
        item.setLayout(new BoxLayout(item, BoxLayout.X_AXIS));
        item.add(cores);
        item.add(rotulo);
        return item;
    }

    private static void incrementar(JLabel contador) {
        contador.setText(String.valueOf(Integer.parseInt(contador.getText()) + 1));
    }
}
