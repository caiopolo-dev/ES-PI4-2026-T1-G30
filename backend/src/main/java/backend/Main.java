//Autores: Caio Ferreira Polo, Cauã Bianchi Ferroni, Miguel Batista Gallinucci, Samuel Campovilla
package backend;

import backend.auth.SessionAuthenticator;
import backend.config.MongoConnection;
import backend.handlers.*;
import backend.repositories.*;
import backend.services.*;

import backend.router.Router;
import backend.server.Servidor;
import java.time.Clock;

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
        // Montei as dependencias aqui para cada classe receber apenas o que precisa usar.
        // A listagem inicial continua usando o repository em memoria.
        MedicationRepository medicationRepository = new InMemoryMedicationRepository();
        MedicationService medicationService = new MedicationService(medicationRepository);
        MedicationHandler medicationHandler = new MedicationHandler(medicationService);


        // Conexão com o MongoDB
        // Mantive o cliente aberto enquanto o servidor executa; o try fecha os recursos ao sair.
        try (MongoConnection mongoConnection = new MongoConnection()) {

            // Configuração dos repositories de doses usando a conexão MongoDB
            PlannedDoseRepository plannedDoseRepository =
                    new MongoPlannedDoseRepository(mongoConnection.getDatabase());

            DoseRecordRepository doseRecordRepository =
                    new MongoDoseRecordRepository(mongoConnection.getDatabase());

            // issue 32 - Reutiliza os repositórios e a autorização de titularidade existentes.
            DoseAccessService doseAccessService =
                    new DoseAccessService(plannedDoseRepository, doseRecordRepository);
            DoseService doseService =
                    new DoseService(doseRecordRepository, doseAccessService);
            DoseRecordHandler doseRecordHandler = new DoseRecordHandler(doseService);

            // Configuração dos serviços de agenda e adesão
            AgendaService agendaService =
                    new AgendaService(plannedDoseRepository, doseRecordRepository);

            AdherenceService adherenceService =
                    new AdherenceService(plannedDoseRepository, doseRecordRepository);

            // Reutilizei os repositories existentes porque o historico consulta as mesmas doses e registros.
            DoseHistoryService doseHistoryService =
                    new DoseHistoryService(plannedDoseRepository, doseRecordRepository);

            // O handler recebe o servico pronto e fica responsavel apenas pela consulta HTTP do historico.
            HistoryHandler historyHandler = new HistoryHandler(doseHistoryService);

            // Configuração do cadastro de usuários
            // Todos os servicos de usuarios recebem o repository da mesma collection e conexao.
            UserRepository userRepository =
                    new MongoUserRepository(mongoConnection.getCollection("users"));

            // Repository que persiste os tratamentos no MongoDB
            TreatmentRepository treatmentRepository =
                    new MongoTreatmentRepository(mongoConnection.getDatabase());

            // Relógio utilizado pelos serviços de tratamentos e geração de doses.
            Clock clock = Clock.systemUTC();

            // Serviço responsável pela geração e reconciliação das doses.
            DoseGenerationService doseGenerationService =
                    new DoseGenerationService(
                            treatmentRepository,
                            plannedDoseRepository,
                            clock
                    );

            // Serviço responsável pelo ciclo de vida dos tratamentos.
            TreatmentService treatmentService =
                    new TreatmentService(
                            treatmentRepository,
                            clock,
                            doseGenerationService
                    );

            // Handler que recebe as requisições HTTP de tratamentos
            TreatmentHandler treatmentHandler =
                    new TreatmentHandler(treatmentService);

            // Medicamentos em uso utilizam a mesma conexao e as regras do tratamento.
            MedicationInUseRepository medicationInUseRepository =
                    new MongoMedicationInUseRepository(mongoConnection.getCollection("treatments"));
            TreatmentMedicationRules treatmentMedicationRules =
                    new DefaultTreatmentMedicationRules(treatmentService);

            ScheduleService scheduleService = new ScheduleService(treatmentRepository, doseGenerationService);

            MedicationInUseService medicationInUseService =
                    new MedicationInUseService(
                            medicationInUseRepository,
                            treatmentMedicationRules,
                            scheduleService,
                            doseGenerationService
                    );

            MedicationInUseHandler medicationInUseHandler =
                    new MedicationInUseHandler(medicationInUseService);

            // issue 28 - Compartilha o mesmo repositório MongoDB com o serviço de cronogramas.
            ScheduleHandler scheduleHandler = new ScheduleHandler(scheduleService);

            AuthService authService = new AuthService(userRepository);
            AuthHandler authHandler = new AuthHandler(authService);

            
            LoginService loginService = new LoginService(userRepository);
            // Configuração das sessões
            SessionRepository sessionRepository = new MongoSessionRepository(mongoConnection.getCollection("auth_sessions"));
            SessionService sessionService = new SessionService(sessionRepository);
            
            BearerTokenExtractor bearerTokenExtractor = new BearerTokenExtractor();

            // Usa a mesma sessao do login nas consultas privadas.
            // Lambda e uma funcao escrita em linha; usei para ligar a validacao da sessao ao Router.
            // Extraio o token, valido a sessao e entrego so o ID do usuario ao Router.
            SessionAuthenticator sessionAuthenticator = request -> bearerTokenExtractor.extract(request).flatMap(sessionService::resolve).map(session -> session.getUserId());
            AgendaHandler agendaHandler = new AgendaHandler(agendaService);
            AdherenceHandler adherenceHandler = new AdherenceHandler(adherenceService);

            LoginRateLimiter loginRateLimiter = new LoginRateLimiter();
            LoginHandler loginHandler = new LoginHandler(loginService, sessionService, loginRateLimiter);
            MeHandler meHandler = new MeHandler(sessionService, userRepository, bearerTokenExtractor);
            LogoutHandler logoutHandler = new LogoutHandler(sessionService, bearerTokenExtractor);


            // Configuração das rotas HTTP
            Router router = new Router(sessionAuthenticator);
            router.register("GET", "/health", new HealthHandler());
            // Associei a rota ao handler para a requisicao chegar ao servico de medicamentos.
            router.register("GET", "/medications", medicationHandler);
            router.register("GET", "/doses", agendaHandler);
            // issue 32
            router.register("PUT", "/doses/{id}/record", doseRecordHandler);

            router.register("GET", "/adherence/summary", adherenceHandler);
            router.register("GET", "/adherence/history", historyHandler);
            router.register("POST", "/auth/register", authHandler);
            router.register("POST", "/auth/login", loginHandler);
            router.register("GET", "/auth/me", meHandler);
            router.register("POST", "/auth/logout", logoutHandler);
            router.register("POST", "/treatments", treatmentHandler);
            router.register("GET", "/treatments", treatmentHandler);
            router.register("GET", "/treatments/{id}", treatmentHandler);
            router.register("PUT", "/treatments/{id}", treatmentHandler);
            router.register("POST", "/treatments/{id}/activate", treatmentHandler);
            router.register("POST", "/treatments/{id}/close", treatmentHandler);
            medicationInUseHandler.registerRoutes(router);

            // issue 28 - Registra configuração e consulta do cronograma do medicamento.
            scheduleHandler.registerRoutes(router);


            //!!!!!!!!!!!!!
            // Inicializa o servidor e a reconciliação periódica das doses (job).
            try (DoseReconciliationJob job = new DoseReconciliationJob(treatmentRepository, doseGenerationService)) {

                job.start();

                Servidor servidor = new Servidor(porta, router);
                servidor.aceitadorDeConexao();
            }

        }
    }
}
