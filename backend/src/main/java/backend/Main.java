package backend;

import backend.auth.InMemorySessionAuthenticator;
import backend.config.MongoConnection;
import backend.handlers.AgendaHandler;
import backend.handlers.AuthHandler;
import backend.handlers.HealthHandler;
import backend.handlers.MedicationHandler;
import backend.repositories.DoseRecordRepository;
import backend.repositories.InMemoryMedicationRepository;
import backend.repositories.MedicationRepository;
import backend.repositories.MongoDoseRecordRepository;
import backend.repositories.MongoPlannedDoseRepository;
import backend.repositories.MongoUserRepository;
import backend.repositories.PlannedDoseRepository;
import backend.repositories.UserRepository;
import backend.router.Router;
import backend.server.Servidor;
import backend.services.AgendaService;
import backend.services.AuthService;
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

        PlannedDoseRepository plannedDoseRepository = new MongoPlannedDoseRepository();
        DoseRecordRepository doseRecordRepository = new MongoDoseRecordRepository();
        AgendaService agendaService = new AgendaService(plannedDoseRepository, doseRecordRepository);
        InMemorySessionAuthenticator sessionAuthenticator = new InMemorySessionAuthenticator();
        AgendaHandler agendaHandler = new AgendaHandler(agendaService, sessionAuthenticator);

        try (MongoConnection mongoConnection = new MongoConnection()) {
            UserRepository userRepository = new MongoUserRepository(mongoConnection.getCollection("users"));

            AuthService authService = new AuthService(userRepository);
            AuthHandler authHandler = new AuthHandler(authService);

            Router router = new Router();
            router.register("GET", "/health", new HealthHandler());
            router.register("GET", "/medications", medicationHandler);
            router.register("GET", "/doses", agendaHandler);
            router.register("POST", "/auth/register", authHandler);

            Servidor servidor = new Servidor(porta, router);
            servidor.aceitadorDeConexao();
        }
    }
}
