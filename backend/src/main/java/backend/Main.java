package backend;

import backend.auth.InMemorySessionAuthenticator;
import backend.config.MongoConnection;
import backend.handlers.AdherenceHandler;
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
import backend.services.AdherenceService;
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

        // Configuração dos medicamentos
        MedicationRepository medicationRepository = new InMemoryMedicationRepository();
        MedicationService medicationService = new MedicationService(medicationRepository);
        MedicationHandler medicationHandler = new MedicationHandler(medicationService);

        // Configuração de agenda e adesão/pontualidade
        PlannedDoseRepository plannedDoseRepository = new MongoPlannedDoseRepository();
        DoseRecordRepository doseRecordRepository = new MongoDoseRecordRepository();
        AgendaService agendaService = new AgendaService(plannedDoseRepository, doseRecordRepository);
        AdherenceService adherenceService = new AdherenceService(plannedDoseRepository, doseRecordRepository);
        InMemorySessionAuthenticator sessionAuthenticator = new InMemorySessionAuthenticator();
        AgendaHandler agendaHandler = new AgendaHandler(agendaService, sessionAuthenticator);
        AdherenceHandler adherenceHandler = new AdherenceHandler(adherenceService, sessionAuthenticator);

        // Conexão com o MongoDB
        try (MongoConnection mongoConnection = new MongoConnection()) {

            // Configuração do cadastro de usuários
            UserRepository userRepository = new MongoUserRepository(mongoConnection.getCollection("users"));
            AuthService authService = new AuthService(userRepository);
            AuthHandler authHandler = new AuthHandler(authService);

            // Configuração das rotas HTTP
            Router router = new Router();
            router.register("GET", "/health", new HealthHandler());
            router.register("GET", "/medications", medicationHandler);
            router.register("GET", "/doses", agendaHandler);
            router.register("GET", "/adherence/summary", adherenceHandler);
            router.register("POST", "/auth/register", authHandler);

            // Inicialização do servidor
            Servidor servidor = new Servidor(porta, router);
            servidor.aceitadorDeConexao();
        }
    }
}
