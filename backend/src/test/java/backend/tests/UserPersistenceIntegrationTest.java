package backend.tests;

import backend.config.MongoConnection;
import backend.models.User;
import backend.repositories.MongoUserRepository;
import backend.repositories.UserDocumentMapper;
import backend.repositories.UserPersistenceException;
import backend.repositories.UserRepository;
import backend.services.UserService;
import java.util.UUID;

/** Testa o fluxo completo de persistência pelo serviço de usuários. */
public class UserPersistenceIntegrationTest {
    public static void main(String[] args) {
        // O teste não usa variáveis do Atlas nem o banco compartilhado.
        String uri = "mongodb://127.0.0.1:27017";
        String database = "dosecerta_test_service_" + UUID.randomUUID().toString().replace("-", "");
        User expected = new User("service-user", "Usuário Teste", "Teste@example.com", "teste@example.com",
            "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000);

        try (MongoConnection cleanup = new MongoConnection(uri, database)) {
            try {
                try (MongoConnection connection = new MongoConnection(uri, database)) {
                    UserRepository repository = new MongoUserRepository(connection.getCollection("users"));
                    UserService service = new UserService(repository);
                    service.save(expected);
                }

                // Recria a conexão, o repository e o serviço para consultar o usuário salvo.
                try (MongoConnection connection = new MongoConnection(uri, database)) {
                    UserRepository repository = new MongoUserRepository(connection.getCollection("users"));
                    UserService service = new UserService(repository);
                    UserDocumentMapper mapper = new UserDocumentMapper();
                    User byId = service.findById("service-user").orElseThrow();
                    User byEmail = service.findByEmail("  TESTE@EXAMPLE.COM  ").orElseThrow();
                    check(mapper.toDocument(expected).equals(mapper.toDocument(byId)),
                        "Consulta por ID não preservou os campos.");
                    check(mapper.toDocument(expected).equals(mapper.toDocument(byEmail)),
                        "Consulta por e-mail não preservou os campos.");
                    check(service.findById("missing").isEmpty(), "ID inexistente deveria retornar vazio.");
                    check(service.findByEmail("missing@example.com").isEmpty(),
                        "E-mail inexistente deveria retornar vazio.");

                    try {
                        service.save(new User("duplicate", "Outro Usuário", "TESTE@example.com",
                            "teste@example.com", "outro-hash", "outro-salt", "PBKDF2WithHmacSHA256", 600000));
                        throw new AssertionError("E-mail duplicado deveria ser rejeitado pelo serviço.");
                    } catch (UserPersistenceException error) {
                        check(error.getReason() == UserPersistenceException.Reason.DUPLICATE_USER,
                            "Serviço deveria preservar a classificação do erro.");
                        check(error.getCause() == null, "Erro não deveria expor detalhes do driver.");
                    }
                    check(connection.getCollection("users").countDocuments() == 1,
                        "Tentativa duplicada não deveria alterar o número de usuários.");
                }
                System.out.println("PASS: serviço -> repository -> MongoDB, recuperação após reabrir conexão e conflito.");
            } finally {
                // Limpa apenas o banco temporário criado por esta execução.
                cleanup.getDatabase().drop();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
