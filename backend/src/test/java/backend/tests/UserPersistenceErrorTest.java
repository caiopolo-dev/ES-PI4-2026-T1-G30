package backend.tests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import backend.config.MongoConnection;
import backend.models.User;
import backend.repositories.MongoUserRepository;
import backend.repositories.UserPersistenceException;
import java.util.UUID;
import org.bson.Document;

/** Verifica erros usando somente um banco local temporário. */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
public class UserPersistenceErrorTest {
    @Test
    void validatesExistingScenarios() {
        String uri = "mongodb://127.0.0.1:27017";
        String database = "dosecerta_test_errors_" + UUID.randomUUID().toString().replace("-", "");
        try (MongoConnection connection = new MongoConnection(uri, database)) {
            try {
                var collection = connection.getCollection("users");
                MongoUserRepository repository = new MongoUserRepository(collection);
                repository.save(sampleUser("user-1", "first@example.com"));
                expectFailure(() -> repository.save(sampleUser("user-1", "other@example.com")),
                    UserPersistenceException.Reason.DUPLICATE_USER);
                expectFailure(() -> repository.save(sampleUser("user-2", "first@example.com")),
                    UserPersistenceException.Reason.DUPLICATE_USER);

                // Simula um documento incompleto no banco exclusivo deste teste.
                collection.insertOne(new Document("_id", "broken")
                    .append("emailNormalized", "broken@example.com"));
                expectFailure(() -> repository.findById("broken"), UserPersistenceException.Reason.INVALID_DOCUMENT);
                expectFailure(() -> repository.findByEmail("broken@example.com"),
                    UserPersistenceException.Reason.INVALID_DOCUMENT);
                check(repository.findById("missing").isEmpty(), "Ausência deve retornar vazio.");
                check(repository.findByEmail("missing@example.com").isEmpty(), "Ausência deve retornar vazio.");

                try (MongoConnection closing = new MongoConnection(uri, database)) {
                    var closedCollection = closing.getCollection("users");
                    MongoUserRepository closedRepository = new MongoUserRepository(closedCollection);
                    closing.close();
                    expectFailure(() -> closedRepository.findById("user-1"),
                        UserPersistenceException.Reason.DATABASE_UNAVAILABLE);
                    expectFailure(() -> closedRepository.findByEmail("first@example.com"),
                        UserPersistenceException.Reason.DATABASE_UNAVAILABLE);
                    expectFailure(() -> closedRepository.save(sampleUser("user-3", "third@example.com")),
                        UserPersistenceException.Reason.DATABASE_UNAVAILABLE);
                    expectFailure(() -> new MongoUserRepository(closedCollection),
                        UserPersistenceException.Reason.DATABASE_UNAVAILABLE);
                }

                // Um validador temporário provoca uma falha de escrita diferente de duplicidade.
                connection.getDatabase().runCommand(new Document("collMod", "users")
                    .append("validator", new Document("requiredForTest", new Document("$exists", true)))
                    .append("validationLevel", "strict"));
                expectFailure(() -> repository.save(sampleUser("rejected", "rejected@example.com")),
                    UserPersistenceException.Reason.DATABASE_ERROR);
                check(collection.countDocuments() == 2, "Operações rejeitadas não deveriam gravar dados.");
            } finally {
                // Remove somente o banco criado acima, incluindo seu validador.
                connection.getDatabase().drop();
            }
        }
    }

    private static User sampleUser(String id, String email) {
        return new User(id, "Usuário Teste", email, email,
            "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000);
    }

    private static void expectFailure(Runnable operation, UserPersistenceException.Reason reason) {
        UserPersistenceException expected = assertThrows(UserPersistenceException.class, operation::run);
            check(expected.getReason() == reason, "Classificação incorreta do erro.");
            check(expected.getCause() == null, "Erro não deve expor a causa do driver.");
            check(!expected.getMessage().contains("example.com"), "Erro não deve expor e-mail.");
    }

    private static void check(boolean condition, String message) {
        assertTrue(condition, message);
    }
}
