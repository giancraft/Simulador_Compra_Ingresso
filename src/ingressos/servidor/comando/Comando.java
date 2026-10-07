package ingressos.servidor.comando;

import ingressos.comum.Mensagem;
import ingressos.servidor.dominio.Sessao;
import ingressos.servidor.venda.Bilheteria;

/** Padrão Command: ação executada para uma mensagem recebida do cliente. */
@FunctionalInterface
public interface Comando {

    /**
     * @param sessao     sessão que enviou a mensagem
     * @param mensagem   mensagem recebida
     * @param bilheteria casos de uso da venda
     */
    void executar(Sessao sessao, Mensagem mensagem, Bilheteria bilheteria);
}
