// Desenvolvido originalmente por Caio Ferreira Polo.
// Issue #8 - Núcleo do servidor e ciclo de vida.
package backend.server;

import backend.router.Router;
import java.util.Objects;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Abre o servidor na porta configurada, aceita conexões e cria um atendimento
 * separado para cada cliente. Também permite interromper o ciclo de aceitação.
 */
public class Servidor {
    // A porta determina em qual endereço local o servidor aguardará conexões.
    int porta;
    // O mesmo Router é compartilhado para que todos os clientes usem as rotas configuradas.
    private final Router router;
    // Guardo o ServerSocket ativo para que parar() consiga encerrá-lo.
    private volatile ServerSocket activeServerSocket;

    // Quando nenhum Router é informado, o servidor utiliza uma configuração padrão.
    public Servidor(int porta){
        this(porta, new Router());
    }

    // Este construtor recebe o Router já configurado e preserva as mesmas rotas para todos.
    public Servidor(int porta, Router router){
        this.porta = porta;
        this.router = Objects.requireNonNull(router);
    }

    public void aceitadorDeConexao(){
        // O ServerSocket fica aguardando conexões na porta configurada.
        // O try-with-resources garante seu fechamento ao sair deste método.
        try(ServerSocket servidorSocket = new ServerSocket(this.porta);){
            this.activeServerSocket = servidorSocket;
            while (!servidorSocket.isClosed()) {
                
                // accept() bloqueia até um cliente se conectar e então entrega seu Socket.
                Socket socket = servidorSocket.accept();
                // Cada conexão recebe um ClienteHandler, mas reutiliza o mesmo Router.
                ClienteHandler clienteHandler = new ClienteHandler(socket, router);
                // A thread atende este cliente sem impedir a aceitação dos próximos.
                Thread thread = new Thread(clienteHandler);
                thread.start();

            }
        }catch(IOException e ){
            // Fechar o servidor por parar() também libera o accept(). Nesse caso,
            // não trato o encerramento esperado como falha de conexão.
            if (activeServerSocket == null || !activeServerSocket.isClosed()) {
                System.out.println(e.getMessage());
            }
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
           
    }

    public void parar() {
        ServerSocket socket = this.activeServerSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                // Fechar o ServerSocket interrompe o accept() e impede novas conexões.
                socket.close();
            } catch (IOException ignored) {
                // O encerramento é uma tentativa segura: não há outra ação útil
                // se o socket já não puder ser fechado normalmente.
            }
        }
    }
}
