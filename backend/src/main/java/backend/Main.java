package backend;

import backend.config.MongoConnection;
import backend.handlers.HealthHandler;
import backend.handlers.MedicationHandler;
import backend.repositories.InMemoryMedicationRepository;
import backend.repositories.MedicationRepository;
import backend.repositories.MongoUserRepository;
import backend.repositories.UserRepository;
import backend.router.Router;
import backend.server.Servidor;
import backend.services.MedicationService;
import backend.services.UserService;

public class Main {
    public static void main(String[] args) throws Exception {

        int porta;

        if (args.length == 0) {
            porta = 8080;
        } else if (args.length >= 2) {
            throw new Exception("Args maior ou igual a 2");
        } else {
            porta = Integer.parseInt(args[0]);
        }



        
        MedicationRepository medicationRepository = new InMemoryMedicationRepository();
        MedicationService medicationService = new MedicationService(medicationRepository);
        MedicationHandler medicationHandler = new MedicationHandler(medicationService);

        Router router = new Router();
        router.register("GET", "/health", new HealthHandler());
        router.register("GET", "/medications", medicationHandler);

        // A aplicação compartilha a conexão durante toda a execução do servidor.
        try (MongoConnection connection = new MongoConnection()) {
            UserRepository userRepository = new MongoUserRepository(connection.getCollection("users"));
            // Os futuros handlers de usuários receberão este serviço pelo construtor.
            UserService userService = new UserService(userRepository);

            // Também fecha o cliente ao encerrar o processo com Ctrl+C.
            Thread shutdown = new Thread(connection::close, "mongodb-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                Servidor servidor = new Servidor(porta, router);
                servidor.aceitadorDeConexao();
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                } catch (IllegalStateException e) {
                    // O hook já está sendo executado durante o encerramento do processo.
                }
            }
        }
    }
}
