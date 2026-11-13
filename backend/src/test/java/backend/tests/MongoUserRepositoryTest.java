package backend.tests;

import backend.config.MongoConnection;
import backend.models.User;
import backend.repositories.MongoUserRepository;
import backend.repositories.UserDocumentMapper;
import java.util.UUID;

/** Verifica a persistência em um banco local exclusivo para a execução. */
public class MongoUserRepositoryTest {
    public static void main(String[] args) {
        // Não utiliza a URI do Atlas nem o banco compartilhado da equipe.
        String uri = "mongodb://127.0.0.1:27017";
        String database = "dosecerta_test_users_" + UUID.randomUUID().toString().replace("-", "");
        User expected = new User("test-user", "Usuário Teste", "Teste@example.com", "teste@example.com",
            "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000);

        try (MongoConnection connection = new MongoConnection(uri, database)) {
            try {
                MongoUserRepository repository = new MongoUserRepository(connection.getCollection("users"));
                check(repository.findById("test-user").isEmpty(), "Banco de teste deveria começar vazio.");
                repository.save(expected);

                UserDocumentMapper mapper = new UserDocumentMapper();
                User byId = repository.findById("test-user").orElseThrow();
                check(mapper.toDocument(expected).equals(mapper.toDocument(byId)),
                    "Consulta por ID deve preservar todos os campos.");
                User byEmail = repository.findByEmail("teste@example.com").orElseThrow();
                check("test-user".equals(byEmail.getId()), "Consulta por e-mail retornou usuário incorreto.");
                check(repository.findById("missing").isEmpty(), "ID inexistente deve retornar vazio.");
                check(repository.findByEmail("missing@example.com").isEmpty(), "E-mail inexistente deve retornar vazio.");

                // Uma nova conexão confirma que a consulta vem do MongoDB.
                try (MongoConnection secondConnection = new MongoConnection(uri, database)) {
                    MongoUserRepository secondRepository = new MongoUserRepository(secondConnection.getCollection("users"));
                    check(secondRepository.findById("test-user").isPresent(), "Usuário não foi persistido.");
                }
                check(connection.getCollection("users").countDocuments() == 1, "Quantidade de usuários inesperada.");
                System.out.println("PASS: gravação, busca por ID/e-mail, campos preservados e usuário inexistente.");
            } finally {
                // Remove somente o banco de teste gerado acima.
                connection.getDatabase().drop();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
