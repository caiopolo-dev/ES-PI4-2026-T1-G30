package backend.src.server;

import backend.src.router.Router;
import java.util.Objects;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
    int porta;
    private final Router router;

    public Servidor(int porta){
        this(porta, new Router());
    }

    public Servidor(int porta, Router router){
        this.porta = porta;
        this.router = Objects.requireNonNull(router);
    }

    public void aceitadorDeConexao(){
        try(ServerSocket servidorSocket = new ServerSocket(this.porta);){
           
            while (!servidorSocket.isClosed()) {
                
                Socket socket = servidorSocket.accept();
                ClienteHandler clienteHandler = new ClienteHandler(socket, router);
                Thread thread = new Thread(clienteHandler);
                thread.start();

            }
        }catch(IOException e ){
            System.out.println(e.getMessage());
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
           
    }
}
