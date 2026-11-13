package backend.server;

import backend.router.Router;
import java.util.Objects;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
    int porta;
    private final Router router;
    private volatile ServerSocket activeServerSocket;

    public Servidor(int porta){
        this(porta, new Router());
    }

    public Servidor(int porta, Router router){
        this.porta = porta;
        this.router = Objects.requireNonNull(router);
    }

    public void aceitadorDeConexao(){
        try(ServerSocket servidorSocket = new ServerSocket(this.porta);){
            this.activeServerSocket = servidorSocket;
            while (!servidorSocket.isClosed()) {
                
                Socket socket = servidorSocket.accept();
                ClienteHandler clienteHandler = new ClienteHandler(socket, router);
                Thread thread = new Thread(clienteHandler);
                thread.start();

            }
        }catch(IOException e ){
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
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
