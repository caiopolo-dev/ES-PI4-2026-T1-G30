
package backend;

import backend.config.MongoConnection;

import backend.handlers.HealthHandler;
import backend.handlers.MedicationHandler;
import backend.repositories.InMemoryMedicationRepository;
import backend.repositories.MedicationRepository;
import backend.services.MedicationService;
import backend.repositories.MongoUserRepository;

import backend.repositories.UserRepository;

import backend.router.Router;
import backend.server.Servidor;

import backend.handlers.AuthHandler;
import backend.services.AuthService;

import backend.handlers.LoginHandler;
import backend.services.LoginService;

import backend.repositories.SessionRepository;
import backend.repositories.MongoSessionRepository;
import backend.services.SessionService;

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

            // Configuração do cadastro de usuários
            UserRepository userRepository = new MongoUserRepository(mongoConnection.getCollection("users"));

            AuthService authService = new AuthService(userRepository);
            AuthHandler authHandler = new AuthHandler(authService);

            
            LoginService loginService = new LoginService(userRepository);
            // Configuração das sessões
            SessionRepository sessionRepository = new MongoSessionRepository(mongoConnection.getCollection("auth_sessions"));
            SessionService sessionService = new SessionService(sessionRepository);
            LoginHandler loginHandler = new LoginHandler(loginService,sessionService);


            // Configuração das rotas HTTP
            Router router = new Router();

            router.register("GET", "/health", new HealthHandler());
            router.register("GET", "/medications", medicationHandler);
            router.register("POST", "/auth/register", authHandler);
            router.register("POST", "/auth/login", loginHandler);

            // Inicialização do servidor
            Servidor servidor = new Servidor(porta, router);
            servidor.aceitadorDeConexao();
        }
    }
}
