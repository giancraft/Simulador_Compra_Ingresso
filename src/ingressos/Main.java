package ingressos;

import ingressos.cliente.SimuladorClientes;
import ingressos.cliente.painel.PainelMonitor;
import ingressos.comum.Config;
import ingressos.servidor.Servidor;

/**
 * Ponto de entrada. Sem argumentos sobe servidor, painel e clientes simulados na mesma JVM, cada um na sua
 * thread, conversando por socket em localhost. Com argumento ({@code servidor}, {@code painel}
 * ou {@code clientes}) sobe só uma parte, para rodar em terminais ou máquinas separadas.
 */
public final class Main {

    private Main() {
    }

    /**
     * @param args vazio ou {@code servidor|painel|clientes}
     * @throws Exception se alguma parte não puder iniciar
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.carregar();
        switch (args.length == 0 ? "tudo" : args[0]) {
            case "servidor" -> new Servidor(config).executar();
            case "painel" -> PainelMonitor.abrir(config);
            case "clientes" -> new SimuladorClientes(config).executar();
            case "tudo" -> tudo(config);
            default -> System.err.println("uso: Main [servidor|painel|clientes]");
        }
    }

    private static void tudo(Config config) throws Exception {
        Servidor servidor = new Servidor(config); // a porta já fica aberta antes dos clientes
        Thread threadServidor = new Thread(() -> {
            try {
                servidor.executar();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "servidor");
        threadServidor.start();
        new Thread(() -> {
            try {
                PainelMonitor.abrir(config);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "painel").start();

        new SimuladorClientes(config).executar();
        servidor.encerrar("simulacao-concluida"); // sem efeito se já esgotou
        threadServidor.join();
    }
}
