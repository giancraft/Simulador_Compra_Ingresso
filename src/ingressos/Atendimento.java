package ingressos;

import java.io.IOException;

import static ingressos.Mensagem.Tipo.ERRO;
import static ingressos.Mensagem.Tipo.LOTADO;

/** Tarefa do pool de atendimento: uma por conexão. Lê comandos, valida pelo estado e executa. */
public final class Atendimento implements Runnable {

    private final Conexao conexao;
    private final Bilheteria bilheteria;

    /**
     * @param conexao    conexão aceita
     * @param bilheteria regras da venda
     */
    public Atendimento(Conexao conexao, Bilheteria bilheteria) {
        this.conexao = conexao;
        this.bilheteria = bilheteria;
    }

    @Override
    public void run() {
        Sessao sessao = bilheteria.conectar(conexao);
        try {
            while (true) {
                Mensagem m;
                try {
                    m = conexao.receber();
                } catch (IllegalArgumentException e) {
                    sessao.enviar(ERRO, "comando desconhecido");
                    continue;
                }
                if (m == null) {
                    break;
                }
                Sessao.Estado estado = sessao.estado();
                if (estado.aceita(m.tipo())) {
                    Comando.de(m.tipo()).executar(sessao, m, bilheteria);
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

    /** Responde LOTADO e fecha; usado quando o pool de atendimento rejeita a tarefa. */
    public void recusar() {
        conexao.enviar(LOTADO);
        conexao.close();
        bilheteria.registrarRejeicao();
    }
}
