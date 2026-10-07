package ingressos.servidor.comando;

import ingressos.comum.Mensagem;
import ingressos.servidor.dominio.Sessao;
import ingressos.servidor.venda.Bilheteria;

import java.util.EnumMap;
import java.util.Map;

import static ingressos.comum.Mensagem.Tipo.*;

/**
 * Padrão Factory: entrega o {@link Comando} de cada tipo de mensagem. Novos comandos entram
 * por {@link #registrar} sem alterar quem os executa.
 */
public final class ComandoFactory {

    private final Map<Mensagem.Tipo, Comando> comandos = new EnumMap<>(Mensagem.Tipo.class);

    /** Registra os comandos do protocolo. */
    public ComandoFactory() {
        registrar(ENTRAR, ComandoFactory::entrar);
        registrar(LISTAR, (sessao, m, bilheteria) -> bilheteria.listar(sessao));
        registrar(RESERVAR, ComandoFactory::reservar);
        registrar(PAGAR, (sessao, m, bilheteria) -> bilheteria.pagar(sessao));
        registrar(MONITORAR, (sessao, m, bilheteria) -> bilheteria.monitorar(sessao));
        registrar(SAIR, (sessao, m, bilheteria) -> {
            sessao.enviar(ATE_LOGO);
            sessao.fechar();
        });
    }

    /**
     * @param tipo    tipo de mensagem
     * @param comando ação correspondente
     */
    public void registrar(Mensagem.Tipo tipo, Comando comando) {
        comandos.put(tipo, comando);
    }

    /**
     * @param tipo tipo da mensagem recebida
     * @return comando correspondente
     * @throws IllegalArgumentException se o tipo não for um comando de cliente
     */
    public Comando criar(Mensagem.Tipo tipo) {
        Comando comando = comandos.get(tipo);
        if (comando == null) {
            throw new IllegalArgumentException(tipo + " não é um comando de cliente");
        }
        return comando;
    }

    private static void entrar(Sessao sessao, Mensagem m, Bilheteria bilheteria) {
        if (m.args().size() != 2 || !m.arg(1).matches("\\d{3,11}")) {
            sessao.enviar(ERRO, "uso: ENTRAR <nome> <cpf numerico>");
        } else {
            bilheteria.entrar(sessao, m.arg(0), m.arg(1));
        }
    }

    private static void reservar(Sessao sessao, Mensagem m, Bilheteria bilheteria) {
        if (m.args().size() != 1) {
            sessao.enviar(ERRO, "uso: RESERVAR <assento>");
        } else {
            bilheteria.reservar(sessao, m.arg(0));
        }
    }
}
