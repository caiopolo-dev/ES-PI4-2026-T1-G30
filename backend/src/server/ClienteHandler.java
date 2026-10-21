package backend.src.server;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClienteHandler implements Runnable {
    private final Socket socket;

    public ClienteHandler(Socket socketRecebido){
        if(socketRecebido == null){
            throw new IllegalArgumentException();
        }
        this.socket = socketRecebido;
    }

    @Override 
    public void run(){
        try(this.socket){
            InputStream inputStream = this.socket.getInputStream();
            OutputStream outputStream = this.socket.getOutputStream();

            int data = inputStream.read();
            while(data != -1){
                outputStream.write(data);
                System.out.println(data);
                data = inputStream.read();
            }
            System.out.println("Cliente encerrou o envio de dados");

            

        }catch(IOException e){
            System.out.println(e.getMessage());
        }

    }
    
}
