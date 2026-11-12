package backend.tests;

import backend.config.MongoConnection;
import backend.models.User;
import backend.repositories.MongoUserRepository;
import backend.repositories.UserPersistenceException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

/** Verifica normalização e unicidade em um banco local temporário. */
public class UserEmailUniquenessTest {
    public static void main(String[] args) throws Exception {
        String database = "dosecerta_test_email_" + UUID.randomUUID().toString().replace("-", "");
        try (MongoConnection connection = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
            try {
                var collection = connection.getCollection("users");
                MongoUserRepository repository = new MongoUserRepository(collection);
                // A inicialização repetida deve reutilizar o mesmo índice.
                new MongoUserRepository(collection);
                Document uniqueIndex = null;
                for (Document index : collection.listIndexes()) {
                    if ("users_email_normalized_unique".equals(index.getString("name"))) {
                        uniqueIndex = index;
                    }
                }
                check(uniqueIndex != null && Boolean.TRUE.equals(uniqueIndex.getBoolean("unique")),
                    "Índice único não foi criado.");
                check(new Document("emailNormalized", 1).equals(uniqueIndex.get("key")),
                    "Índice criado no campo incorreto.");

                repository.save(sampleUser("first", "Teste@example.com"));
                check("first".equals(repository.findByEmail("  TESTE@EXAMPLE.COM  ").orElseThrow().getId()),
                    "Consulta deveria normalizar o e-mail.");
                try {
                    repository.save(sampleUser("duplicate", "  TESTE@example.com  "));
                    throw new AssertionError("E-mail duplicado deveria ser rejeitado.");
                } catch (UserPersistenceException expected) {
                    check(expected.getReason() == UserPersistenceException.Reason.DUPLICATE_USER,
                        "Erro inesperado na duplicidade.");
                }
                check(collection.countDocuments() == 1, "Duplicidade não deveria inserir outro usuário.");
                repository.save(sampleUser("different", "other@example.com"));

                // O índice deve proteger também contra duas inserções simultâneas.
                CountDownLatch start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> saveAfterSignal(repository, start, "race-1"));
                    var second = executor.submit(() -> saveAfterSignal(repository, start, "race-2"));
                    start.countDown();
                    int saved = first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS);
                    check(saved == 1, "Apenas uma inserção simultânea deveria vencer.");
                }
                check(collection.countDocuments() == 3, "Quantidade final incorreta.");
                try {
                    repository.save(new User("invalid", "Teste", "valid@example.com", "wrong@example.com",
                        "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000));
                    throw new AssertionError("E-mail normalizado inconsistente deveria ser rejeitado.");
                } catch (IllegalArgumentException expected) {
                    check(collection.countDocuments() == 3, "Entrada inválida não deveria ser inserida.");
                }
                System.out.println("PASS: normalização, índice único, duplicidade, concorrência e consistência.");
            } finally {
                // Limpa somente o banco gerado por esta execução.
                connection.getDatabase().drop();
            }
        }
    }

    private static int saveAfterSignal(MongoUserRepository repository, CountDownLatch start, String id)
            throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Sinal de início não recebido.");
        }
        try {
            repository.save(sampleUser(id, "race@example.com"));
            return 1;
        } catch (UserPersistenceException expected) {
            check(expected.getReason() == UserPersistenceException.Reason.DUPLICATE_USER,
                "Erro inesperado na inserção simultânea.");
            return 0;
        }
    }

    private static User sampleUser(String id, String email) {
        return new User(id, "Usuário Teste", email, email.trim().toLowerCase(Locale.ROOT),
            "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
