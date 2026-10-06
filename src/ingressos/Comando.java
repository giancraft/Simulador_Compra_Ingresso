package ingressos;

import static ingressos.Mensagem.Tipo.ATE_LOGO;
import static ingressos.Mensagem.Tipo.ERRO;

/**
 * Padrão Command: uma constante por comando que o cliente pode enviar.
 * {@link #de(Mensagem.Tipo)} é a fábrica (padrão Factory).
 */
public enum Comando {

    ENTRAR {
        @Override
        void executar(Sessao sessao, Mensagem m, Bilheteria bilheteria) {
            if (m.args().size() != 2 || !m.arg(1).matches("\\d{3,11}")) {
                sessao.enviar(ERRO, "uso: ENTRAR <nome> <cpf numerico>");
            } else {
                bilheteria.entrar(sessao, m.arg(0), m.arg(1));
            }
        }
    },

    LISTAR {
        @Override
        void executar(Sessao sessao, Mensagem m, Bilheteria bilheteria) {
            bilheteria.listar(sessao);
        }
    },

    RESERVAR {
        @Override
        void executar(Sessao sessao, Mensagem m, Bilheteria bilheteria) {
            if (m.args().size() != 1) {
                sessao.enviar(ERRO, "uso: RESERVAR <assento>");
            } else {
                bilheteria.reservar(sessao, m.arg(0));
            }
        }
    },

    PAGAR {
        @Override
        void executar(Sessao sessao, Mensagem m, Bilheteria bilheteria) {
            bilheteria.pagar(sessao);
        }
    },

    SAIR {
        @Override
        void executar(Sessao sessao, Mensagem m, Bilheteria bilheteria) {
            sessao.enviar(ATE_LOGO);
            sessao.fechar();
        }
    },

    MONITORAR {
        @Override
        void executar(Sessao sessao, Mensagem m, Bilheteria bilheteria) {
            bilheteria.monitorar(sessao);
        }
    };

    /**
     * @param sessao     sessão que enviou o comando
     * @param m          mensagem recebida
     * @param bilheteria regras da venda
     */
    abstract void executar(Sessao sessao, Mensagem m, Bilheteria bilheteria);

    /**
     * @param tipo tipo da mensagem recebida
     * @return comando correspondente
     * @throws IllegalArgumentException se o tipo não for um comando de cliente
     */
    static Comando de(Mensagem.Tipo tipo) {
        return valueOf(tipo.name());
    }
}
