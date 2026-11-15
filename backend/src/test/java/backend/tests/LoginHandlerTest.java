package backend.tests;

import backend.dto.LoginRequest;
import backend.handlers.LoginHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.User;
import backend.services.InvalidCredentialsException;
import backend.services.LoginRateLimiter;
import backend.services.LoginService;
import backend.services.SessionService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginHandlerTest {
    private static final String TOKEN = "A".repeat(43);
    private LoginService loginService;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        sessionService = mock(SessionService.class);
    }

    @Test
    void credenciaisCorretasGeramSessaoERetornamToken() {
        User user = user();
        when(loginService.login(any(LoginRequest.class))).thenReturn(user);
        when(sessionService.create("user-1")).thenReturn(TOKEN);
        LoginHandler handler = handlerWithHighLimit();

        HttpResponse response = handler.handle(request("user@example.com", "Senha123!"));
        Document body = Document.parse(response.getBody());

        assertEquals(200, response.getStatusCode());
        assertEquals(TOKEN, body.getString("token"));
        assertFalse(body.containsKey("password"));
        assertFalse(body.containsKey("passwordHash"));
        verify(sessionService).create("user-1");
    }

    @Test
    void senhaIncorretaRetorna401ComMensagemGenerica() {
        when(loginService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException());
        LoginHandler handler = handlerWithHighLimit();

        HttpResponse response = handler.handle(request("user@example.com", "SenhaErrada!"));

        assertEquals(401, response.getStatusCode());
        assertEquals("{\"error\":\"Credenciais inválidas.\"}", response.getBody());
    }

    @Test
    void usuarioInexistenteRetornaAMesmaRespostaGenerica() {
        when(loginService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException());
        LoginHandler handler = handlerWithHighLimit();

        HttpResponse response = handler.handle(request("missing@example.com", "Senha123!"));

        assertEquals(401, response.getStatusCode());
        assertEquals("{\"error\":\"Credenciais inválidas.\"}", response.getBody());
    }

    @Test
    void tentativaAcimaDoLimiteRetorna429SemConsultarCredenciais() {
        LoginRateLimiter limiter = new LoginRateLimiter(
            1,
            10,
            10,
            Duration.ofMinutes(1),
            Clock.systemUTC()
        );
        LoginHandler handler = new LoginHandler(loginService, sessionService, limiter);
        when(loginService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException());

        assertEquals(401, handler.handle(request("user@example.com", "SenhaErrada!")).getStatusCode());
        assertEquals(429, handler.handle(request("user@example.com", "SenhaErrada!")).getStatusCode());
        verify(loginService, times(1)).login(any(LoginRequest.class));
    }

    private LoginHandler handlerWithHighLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter(
            100,
            100,
            100,
            Duration.ofMinutes(1),
            Clock.systemUTC()
        );
        return new LoginHandler(loginService, sessionService, limiter);
    }

    private HttpRequest request(String email, String password) {
        String body = new Document()
            .append("email", email)
            .append("password", password)
            .toJson();
        return new HttpRequest(
            "POST",
            "/auth/login",
            "HTTP/1.1",
            Map.of("Content-Type", "application/json"),
            body
        );
    }

    private User user() {
        return new User(
            "user-1",
            "Usuário Um",
            "user@example.com",
            "user@example.com",
            "hash",
            "salt",
            "PBKDF2WithHmacSHA256",
            600_000
        );
    }
}
