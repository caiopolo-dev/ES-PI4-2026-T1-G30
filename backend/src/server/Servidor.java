package backend.src.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
    int porta;

    public Servidor(int porta){
        this.porta = porta;
    }

    public void aceitadorDeConexao(){
        try(ServerSocket servidorSocket = new ServerSocket(this.porta);){
           
            while (!servidorSocket.isClosed()) {
                
                Socket socket = servidorSocket.accept();
                ClienteHandler clienteHandler = new ClienteHandler(socket);
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
