package backend.src;

import backend.src.handlers.HealthHandler;
import backend.src.handlers.MedicationHandler;
import backend.src.repositories.InMemoryMedicationRepository;
import backend.src.repositories.MedicationRepository;
import backend.src.router.Router;
import backend.src.server.Servidor;
import backend.src.services.MedicationService;

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
