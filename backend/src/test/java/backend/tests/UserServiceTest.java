package backend.tests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import backend.models.User;
import backend.repositories.UserRepository;
import backend.services.UserService;
import java.util.Optional;

/** Verifica que o serviço utiliza o repository recebido no construtor. */
public class UserServiceTest {
    @Test
    void validatesExistingScenarios() {
        User expected = new User("user-1", "Usuário Teste", "test@example.com", "test@example.com",
            "hash-ficticio", "salt-ficticio", "PBKDF2WithHmacSHA256", 600000);

        // Um armazenamento simples permite testar o serviço sem conexão ao banco.
        UserRepository repository = new UserRepository() {
            private User saved;

            @Override
            public void save(User user) {
                saved = user;
            }

            @Override
            public Optional<User> findById(String id) {
                if (saved != null && saved.getId().equals(id)) {
                    return Optional.of(saved);
                }
                return Optional.empty();
            }

            @Override
            public Optional<User> findByEmail(String email) {
                if (saved != null && saved.getEmailNormalized().equals(email)) {
                    return Optional.of(saved);
                }
                return Optional.empty();
            }
        };

        UserService service = new UserService(repository);
        check(service.findById("user-1").isEmpty(), "Usuário não deveria existir antes da gravação.");
        service.save(expected);
        check(service.findById("user-1").orElseThrow() == expected, "Consulta por ID não usou o repository.");
        check(service.findByEmail("test@example.com").orElseThrow() == expected,
            "Consulta por e-mail não usou o repository.");
        check(service.findById("missing").isEmpty(), "Usuário inexistente deveria retornar vazio.");
        assertThrows(NullPointerException.class, () -> new UserService(null));
    }

    private static void check(boolean condition, String message) {
        assertTrue(condition, message);
    }
}
