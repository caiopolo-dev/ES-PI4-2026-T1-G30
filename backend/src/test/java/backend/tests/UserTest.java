package backend.tests;

import backend.models.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    @Test
    void preservesUserAndCredentialFields() {
        // São dados fictícios; gerar e verificar senhas pertence à issue #22.
        User user = new User("user-test", "Usuário Teste", " Teste@example.com ",
            "teste@example.com", "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000);
        assertAll(
            () -> assertEquals("user-test", user.getId()),
            () -> assertEquals("Usuário Teste", user.getName()),
            () -> assertEquals(" Teste@example.com ", user.getEmail()),
            () -> assertEquals("teste@example.com", user.getEmailNormalized()),
            () -> assertEquals("hash-ficticio", user.getPasswordHash()),
            () -> assertEquals("salt-ficticio", user.getPasswordSalt()),
            () -> assertEquals("PBKDF2WithHmacSHA256", user.getPasswordAlgorithm()),
            () -> assertEquals(600000, user.getPasswordIterations())
        );
    }
}
