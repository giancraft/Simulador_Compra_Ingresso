package ingressos.servidor;

import ingressos.comum.Conexao;
import ingressos.comum.Mensagem;
import ingressos.servidor.comando.ComandoFactory;
import ingressos.servidor.dominio.Sessao;
import ingressos.servidor.venda.Bilheteria;

import java.io.IOException;

import static ingressos.comum.Mensagem.Tipo.ERRO;
import static ingressos.comum.Mensagem.Tipo.LOTADO;

/** Tarefa do pool de atendimento: uma por conexão. Lê comandos, valida pelo estado e executa. */
public final class Atendimento implements Runnable {

    private final Conexao conexao;
    private final Bilheteria bilheteria;
    private final ComandoFactory comandos;

    /**
     * @param conexao    conexão aceita
     * @param bilheteria casos de uso da venda
     * @param comandos   fábrica de comandos
     */
    public Atendimento(Conexao conexao, Bilheteria bilheteria, ComandoFactory comandos) {
        this.conexao = conexao;
        this.bilheteria = bilheteria;
        this.comandos = comandos;
    }

    @Override
    public void run() {
        Sessao sessao = bilheteria.conectar(conexao);
        try {
            Mensagem m;
            while ((m = proximaMensagem(sessao)) != null) {
                Sessao.Estado estado = sessao.estado();
                if (estado.aceita(m.tipo())) {
                    comandos.criar(m.tipo()).executar(sessao, m, bilheteria);
                } else {
                    sessao.enviar(ERRO, m.tipo(), "nao permitido em", estado);
                }
            }
        } catch (IOException conexaoCaiu) {
            // queda ou fechamento: a liberação acontece no finally
        } finally {
            bilheteria.desconectar(sessao);
        }
    }

    /** Lê a próxima mensagem válida, respondendo ERRO às desconhecidas. */
    private Mensagem proximaMensagem(Sessao sessao) throws IOException {
        while (true) {
            try {
                return conexao.receber();
            } catch (IllegalArgumentException e) {
                sessao.enviar(ERRO, "comando desconhecido");
            }
        }
    }

    /** Responde LOTADO e fecha; usado quando o pool de atendimento rejeita a tarefa. */
    public void recusar() {
        conexao.enviar(LOTADO);
        conexao.close();
        bilheteria.registrarRejeicao();
    }
}
