package ingressos.servidor.pagamento;

/**
 * Abstração da operadora de pagamento. A venda depende desta interface, não da simulação,
 * então um gateway real (ou um falso para testes) entra sem alterar a {@code Bilheteria}.
 */
@FunctionalInterface
public interface GatewayPagamento {

    /**
     * Bloqueia até a operadora responder.
     *
     * @return {@code true} se o pagamento foi aprovado
     */
    boolean pagar();
}
