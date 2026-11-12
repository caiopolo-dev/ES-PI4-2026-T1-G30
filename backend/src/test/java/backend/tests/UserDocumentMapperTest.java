package backend.tests;

import backend.models.User;
import backend.repositories.UserDocumentMapper;
import org.bson.Document;

/** Testa a conversão dos dados sem conectar ao banco. */
public class UserDocumentMapperTest {
    public static void main(String[] args) {
        UserDocumentMapper mapper = new UserDocumentMapper();
        // Valores fictícios; o mapper não gera nem verifica senhas.
        User user = new User("user-1", "Usuário Teste", "Teste@example.com", "teste@example.com",
            "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000);

        Document document = mapper.toDocument(user);
        check(document.size() == 8, "Documento deve conter os oito campos.");
        check("user-1".equals(document.getString("_id")), "Mapeamento de id incorreto.");
        check(!document.containsKey("password"), "Documento não deve conter senha original.");
        check(document.get("passwordIterations") instanceof Integer, "Iterações devem ser Int32.");

        User restored = mapper.fromDocument(Document.parse(document.toJson()));
        check(user.getId().equals(restored.getId()), "ID alterado.");
        check(user.getName().equals(restored.getName()), "Nome alterado.");
        check(user.getEmail().equals(restored.getEmail()), "E-mail alterado.");
        check(user.getEmailNormalized().equals(restored.getEmailNormalized()), "E-mail normalizado alterado.");
        check(user.getPasswordHash().equals(restored.getPasswordHash()), "Hash alterado.");
        check(user.getPasswordSalt().equals(restored.getPasswordSalt()), "Salt alterado.");
        check(user.getPasswordAlgorithm().equals(restored.getPasswordAlgorithm()), "Algoritmo alterado.");
        check(user.getPasswordIterations() == restored.getPasswordIterations(), "Iterações alteradas.");

        expectInvalid(() -> mapper.toDocument(null));
        expectInvalid(() -> mapper.fromDocument(null));
        for (String field : new String[]{"_id", "name", "email", "emailNormalized",
                "passwordHash", "passwordSalt", "passwordAlgorithm"}) {
            for (Object invalid : new Object[]{null, " ", 123}) {
                Document broken = new Document(document);
                broken.put(field, invalid);
                expectInvalid(() -> mapper.fromDocument(broken));
            }
        }
        for (Object invalid : new Object[]{null, 0, -1, 600000L, 1.5, "600000"}) {
            Document broken = new Document(document);
            broken.put("passwordIterations", invalid);
            expectInvalid(() -> mapper.fromDocument(broken));
        }
        User invalid = new User("user-1", " ", "teste@example.com", "teste@example.com",
            "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000);
        expectInvalid(() -> mapper.toDocument(invalid));
        System.out.println("PASS: conversão completa, campos obrigatórios, tipos e iterações.");
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Entrada inválida deveria ser rejeitada.");
        } catch (IllegalArgumentException expected) {
            check(!expected.getMessage().contains("hash-ficticio"), "Erro não deve expor dados sensíveis.");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
