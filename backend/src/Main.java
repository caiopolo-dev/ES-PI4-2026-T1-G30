package backend.src;
import backend.src.server.Servidor;

public class Main {
    public static void main(String[] args)throws Exception{

        int porta;

        if(args.length == 0){
            porta = 3000;
        }else if(args.length >= 2){
            throw new Exception("Args maior ou igual a 2");
        }else{
            porta = Integer.parseInt(args[0]);
        }
        Servidor servidor = new Servidor(porta);
        servidor.aceitadorDeConexao();

        
    }
    
}
