
package backend.tests;

import backend.dto.RegisterRequest;
import backend.models.User;
import backend.repositories.UserPersistenceException;
import backend.repositories.UserRepository;
import backend.services.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private FakeUserRepository repository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        repository = new FakeUserRepository();
        authService = new AuthService(repository);
    }

    @Test
    void deveCadastrarUsuarioValido() {
        RegisterRequest request = new RegisterRequest(
            "Caio",
            "caio@example.com",
            "SenhaSegura123!"
        );

        User user = authService.register(request);

        assertNotNull(user);
        assertNotNull(user.getId());
        assertEquals("Caio", user.getName());
        assertEquals("caio@example.com", user.getEmail());
        assertEquals("caio@example.com", user.getEmailNormalized());

        assertTrue(repository.findById(user.getId()).isPresent());
    }

    @Test
    void deveNormalizarEmail() {
        RegisterRequest request = new RegisterRequest(
            "Caio",
            "  CAIO@EXAMPLE.COM  ",
            "SenhaSegura123!"
        );

        User user = authService.register(request);

        assertEquals(
            "caio@example.com",
            user.getEmailNormalized()
        );
    }

    @Test
    void deveRejeitarEmailDuplicado() {
        authService.register(new RegisterRequest(
            "Primeiro",
            "teste@example.com",
            "SenhaSegura123!"
        ));

        UserPersistenceException exception = assertThrows(
            UserPersistenceException.class,
            () -> authService.register(new RegisterRequest(
                "Segundo",
                "TESTE@EXAMPLE.COM",
                "OutraSenha123!"
            ))
        );

        assertEquals(
            UserPersistenceException.Reason.DUPLICATE_USER,
            exception.getReason()
        );
    }

    @Test
    void deveRejeitarNomeInvalido() {
        assertThrows(IllegalArgumentException.class,
            () -> authService.register(new RegisterRequest(
                null, "teste@example.com", "SenhaSegura123!"
            ))
        );

        assertThrows(IllegalArgumentException.class,
            () -> authService.register(new RegisterRequest(
                "   ", "teste@example.com", "SenhaSegura123!"
            ))
        );

        assertThrows(IllegalArgumentException.class,
            () -> authService.register(new RegisterRequest(
                "A".repeat(101),
                "teste@example.com",
                "SenhaSegura123!"
            ))
        );
    }

    @Test
    void deveRejeitarEmailInvalido() {
        String[] invalidos = {
            null,
            "",
            "email-invalido",
            "teste@",
            "@example.com",
            "teste@example",
            "A".repeat(250) + "@example.com"
        };

        for (String email : invalidos) {
            assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(new RegisterRequest(
                    "Caio",
                    email,
                    "SenhaSegura123!"
                ))
            );
        }
    }

    @Test
    void deveRejeitarSenhaInvalida() {
        String[] senhasInvalidas = {
            null,
            "",
            "   ",
            "1234567",
            "A".repeat(129)
        };

        for (String senha : senhasInvalidas) {
            assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(new RegisterRequest(
                    "Caio",
                    "teste@example.com",
                    senha
                ))
            );
        }
    }

    @Test
    void deveArmazenarHashEmVezDaSenhaOriginal() {
        String senha = "SenhaSegura123!";

        User user = authService.register(new RegisterRequest(
            "Caio",
            "teste@example.com",
            senha
        ));

        assertNotNull(user.getPasswordHash());
        assertNotNull(user.getPasswordSalt());

        assertNotEquals(senha, user.getPasswordHash());

        byte[] hash = Base64.getDecoder().decode(
            user.getPasswordHash()
        );

        byte[] salt = Base64.getDecoder().decode(
            user.getPasswordSalt()
        );

        assertEquals(32, hash.length);
        assertEquals(16, salt.length);
        assertEquals("PBKDF2WithHmacSHA256",
            user.getPasswordAlgorithm());
        assertEquals(600_000, user.getPasswordIterations());
    }

    @Test
    void deveGerarSaltDiferenteParaCadaUsuario() {
        User primeiro = authService.register(new RegisterRequest(
            "Primeiro",
            "primeiro@example.com",
            "SenhaIgual123!"
        ));

        User segundo = authService.register(new RegisterRequest(
            "Segundo",
            "segundo@example.com",
            "SenhaIgual123!"
        ));

        assertNotEquals(
            primeiro.getPasswordSalt(),
            segundo.getPasswordSalt()
        );

        assertNotEquals(
            primeiro.getPasswordHash(),
            segundo.getPasswordHash()
        );
    }

    // Repositório falso para testar sem MongoDB.
    private static class FakeUserRepository implements UserRepository {

        private final Map<String, User> usuarios = new HashMap<>();

        @Override
        public void save(User user) {
            usuarios.put(user.getId(), user);
        }

        @Override
        public Optional<User> findById(String id) {
            return Optional.ofNullable(usuarios.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            String normalizado = email.trim().toLowerCase(Locale.ROOT);

            return usuarios.values().stream()
                .filter(user -> user.getEmailNormalized()
                    .equals(normalizado))
                .findFirst();
        }
    }
}
