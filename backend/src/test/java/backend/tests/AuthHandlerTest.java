
package backend.tests;

import backend.dto.RegisterRequest;
import backend.handlers.AuthHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.User;
import backend.repositories.UserPersistenceException;
import backend.repositories.UserRepository;
import backend.services.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthHandlerTest {

    private FakeAuthService authService;
    private AuthHandler authHandler;

    @BeforeEach
    void setUp() {
        authService = new FakeAuthService();
        authHandler = new AuthHandler(authService);
    }

    private HttpRequest criarRequest(String body) {
        return new HttpRequest(
            "POST",
            "/auth/register",
            "HTTP/1.1",
            Map.of("Content-Type", "application/json"),
            body
        );
    }

    @Test
    void deveRetornar201ParaCadastroValido() {
        HttpRequest request = criarRequest("""
            {
                "name": "Caio",
                "email": "caio@example.com",
                "password": "SenhaSegura123!"
            }
            """);

        HttpResponse response = authHandler.handle(request);

        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("caio@example.com"));
        assertTrue(response.getBody().contains("Caio"));
    }

    @Test
    void naoDeveExporDadosDaSenhaNaResposta() {
        HttpRequest request = criarRequest("""
            {
                "name": "Caio",
                "email": "caio@example.com",
                "password": "SenhaSegura123!"
            }
            """);

        HttpResponse response = authHandler.handle(request);

        String body = response.getBody();

        assertEquals(201, response.getStatusCode());
        assertFalse(body.contains("SenhaSegura123!"));
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains("passwordSalt"));
        assertFalse(body.contains("passwordIterations"));
        assertFalse(body.contains("passwordAlgorithm"));
        assertFalse(body.contains("password"));
    }

    @Test
    void deveRetornar400ParaJsonInvalido() {
        HttpResponse response = authHandler.handle(
            criarRequest("{json invalido")
        );

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void deveRetornar400ParaCorpoVazio() {
        HttpResponse response = authHandler.handle(
            criarRequest("")
        );

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void deveRetornar400ParaCamposAusentes() {
        HttpResponse response = authHandler.handle(
            criarRequest("""
                {
                    "name": "Caio",
                    "email": "caio@example.com"
                }
                """)
        );

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void deveRetornar400ParaTiposInvalidos() {
        HttpResponse response = authHandler.handle(
            criarRequest("""
                {
                    "name": 123,
                    "email": "caio@example.com",
                    "password": "SenhaSegura123!"
                }
                """)
        );

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void deveRetornar400QuandoServiceRejeitaDados() {
        authService.erro = new IllegalArgumentException(
            "Dados inválidos."
        );

        HttpResponse response = authHandler.handle(
            criarRequest("""
                {
                    "name": "Caio",
                    "email": "invalido",
                    "password": "12345678"
                }
                """)
        );

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void deveRetornar409ParaEmailDuplicado() {
        authService.erro = new UserPersistenceException(
            UserPersistenceException.Reason.DUPLICATE_USER
        );

        HttpResponse response = authHandler.handle(
            criarRequest("""
                {
                    "name": "Caio",
                    "email": "caio@example.com",
                    "password": "SenhaSegura123!"
                }
                """)
        );

        assertEquals(409, response.getStatusCode());
        assertTrue(response.getBody().contains(
            "E-mail já cadastrado"
        ));
    }

    @Test
    void deveRetornar500ParaFalhaNoBanco() {
        authService.erro = new UserPersistenceException(
            UserPersistenceException.Reason.DATABASE_UNAVAILABLE
        );

        HttpResponse response = authHandler.handle(
            criarRequest("""
                {
                    "name": "Caio",
                    "email": "caio@example.com",
                    "password": "SenhaSegura123!"
                }
                """)
        );

        assertEquals(500, response.getStatusCode());

        assertFalse(response.getBody().contains(
            "DATABASE_UNAVAILABLE"
        ));
    }

    @Test
    void deveRetornar500ParaErroInesperado() {
        authService.erro = new IllegalStateException(
            "Detalhes internos do servidor"
        );

        HttpResponse response = authHandler.handle(
            criarRequest("""
                {
                    "name": "Caio",
                    "email": "caio@example.com",
                    "password": "SenhaSegura123!"
                }
                """)
        );

        assertEquals(500, response.getStatusCode());

        assertFalse(response.getBody().contains(
            "Detalhes internos do servidor"
        ));
    }

    @Test
    void deveAceitarContentTypeComCharset() {
        HttpRequest request = new HttpRequest(
            "POST",
            "/auth/register",
            "HTTP/1.1",
            Map.of(
                "content-type",
                "application/json; charset=utf-8"
            ),
            """
            {
                "name": "Caio",
                "email": "caio@example.com",
                "password": "SenhaSegura123!"
            }
            """
        );

        HttpResponse response = authHandler.handle(request);

        assertEquals(201, response.getStatusCode());
    }

    // Simula o AuthService para testar apenas a camada HTTP.
    private static class FakeAuthService extends AuthService {

        private RuntimeException erro;

        FakeAuthService() {
            super(new DummyUserRepository());
        }

        @Override
        public User register(RegisterRequest request) {
            if (erro != null) {
                throw erro;
            }

            return new User(
                "id-teste-123",
                request.getName(),
                request.getEmail(),
                request.getEmail().trim().toLowerCase(
                    java.util.Locale.ROOT
                ),
                "hash-ficticio",
                "salt-ficticio",
                "PBKDF2WithHmacSHA256",
                600_000
            );
        }
    }

    // Necessário apenas para construir o AuthService falso.
    private static class DummyUserRepository implements UserRepository {

        @Override
        public void save(User user) {
        }

        @Override
        public Optional<User> findById(String id) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }
    }
}
