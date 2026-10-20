package backend.src.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
    public static void aceitadorDeConexao(int porta){
        try(ServerSocket servidorSocket = new ServerSocket(porta);){
            while (!(servidorSocket.isClosed())) {
                
                Socket socket = servidorSocket.accept();
    
            }
        }catch(IOException e){
            System.out.println(e.getMessage());
        }
           
    }
}
