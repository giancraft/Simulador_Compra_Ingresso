package ingressos.servidor.dominio;

import java.util.HashSet;
import java.util.Set;

/** Garante no máximo uma sessão ativa por CPF (regra anti-cambista). */
public final class RegistroCpf {

    private final Set<String> ativos = new HashSet<>();

    /**
     * @param cpf CPF informado no ENTRAR
     * @return {@code true} se o CPF estava livre e foi registrado
     */
    public synchronized boolean registrar(String cpf) {
        return ativos.add(cpf);
    }

    /** @param cpf CPF liberado quando a sessão termina */
    public synchronized void liberar(String cpf) {
        ativos.remove(cpf);
    }
}
