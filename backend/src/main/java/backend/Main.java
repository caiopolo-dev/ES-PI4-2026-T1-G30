package backend;

import backend.handlers.HealthHandler;
import backend.handlers.MedicationHandler;
import backend.repositories.InMemoryMedicationRepository;
import backend.repositories.MedicationRepository;
import backend.router.Router;
import backend.server.Servidor;
import backend.services.MedicationService;

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

        Servidor servidor = new Servidor(porta, router);
        servidor.aceitadorDeConexao();
    }
}
