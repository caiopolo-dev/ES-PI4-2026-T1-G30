package backend;

import backend.auth.SessionAuthenticator;
import backend.config.MongoConnection;
import backend.handlers.AdherenceHandler;
import backend.handlers.AgendaHandler;
import backend.handlers.AuthHandler;
import backend.handlers.HealthHandler;
import backend.handlers.MedicationHandler;
import backend.repositories.DoseRecordRepository;
import backend.repositories.InMemoryMedicationRepository;
import backend.repositories.MedicationRepository;
import backend.services.MedicationService;
import backend.repositories.MongoUserRepository;

import backend.repositories.MongoDoseRecordRepository;
import backend.repositories.MongoPlannedDoseRepository;
import backend.repositories.PlannedDoseRepository;
import backend.repositories.UserRepository;

import backend.router.Router;
import backend.server.Servidor;

import backend.services.AuthService;

import backend.handlers.LoginHandler;
import backend.services.LoginService;

import backend.repositories.SessionRepository;
import backend.repositories.MongoSessionRepository;
import backend.services.SessionService;
import backend.services.BearerTokenExtractor;
import backend.services.LoginRateLimiter;
import backend.handlers.MeHandler;
import backend.handlers.LogoutHandler;
import backend.services.AdherenceService;
import backend.services.AgendaService;

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


        // Conexão com o MongoDB
        try (MongoConnection mongoConnection = new MongoConnection()) {

            // Configuração dos repositories de doses usando a conexão MongoDB
            PlannedDoseRepository plannedDoseRepository =
                    new MongoPlannedDoseRepository(mongoConnection.getDatabase());

            DoseRecordRepository doseRecordRepository =
                    new MongoDoseRecordRepository(mongoConnection.getDatabase());

            // Configuração dos serviços de agenda e adesão
            AgendaService agendaService =
                    new AgendaService(plannedDoseRepository, doseRecordRepository);

            AdherenceService adherenceService =
                    new AdherenceService(plannedDoseRepository, doseRecordRepository);

            // Configuração do cadastro de usuários
            UserRepository userRepository =
                    new MongoUserRepository(mongoConnection.getCollection("users"));

            AuthService authService = new AuthService(userRepository);
            AuthHandler authHandler = new AuthHandler(authService);

            
            LoginService loginService = new LoginService(userRepository);
            // Configuração das sessões
            SessionRepository sessionRepository = new MongoSessionRepository(mongoConnection.getCollection("auth_sessions"));
            SessionService sessionService = new SessionService(sessionRepository);
            
            BearerTokenExtractor bearerTokenExtractor = new BearerTokenExtractor();
            // Usa a mesma sessao do login nas consultas privadas.
            SessionAuthenticator sessionAuthenticator = request -> bearerTokenExtractor.extract(request)
                .flatMap(sessionService::resolve)
                .map(session -> session.getUserId());
            AgendaHandler agendaHandler = new AgendaHandler(agendaService);
            AdherenceHandler adherenceHandler = new AdherenceHandler(adherenceService);

            LoginRateLimiter loginRateLimiter = new LoginRateLimiter();
            LoginHandler loginHandler = new LoginHandler(loginService, sessionService, loginRateLimiter);
            MeHandler meHandler = new MeHandler(sessionService, userRepository, bearerTokenExtractor);
            LogoutHandler logoutHandler = new LogoutHandler(sessionService, bearerTokenExtractor);


            // Configuração das rotas HTTP
            Router router = new Router(sessionAuthenticator);
            router.register("GET", "/health", new HealthHandler());
            router.register("GET", "/medications", medicationHandler);
            router.register("GET", "/doses", agendaHandler);
            router.register("GET", "/adherence/summary", adherenceHandler);
            router.register("POST", "/auth/register", authHandler);
            router.register("POST", "/auth/login", loginHandler);
            router.register("GET", "/auth/me", meHandler);
            router.register("POST", "/auth/logout", logoutHandler);

            // Inicialização do servidor
            Servidor servidor = new Servidor(porta, router);
            servidor.aceitadorDeConexao();
        }
    }
}
