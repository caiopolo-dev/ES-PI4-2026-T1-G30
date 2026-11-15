package backend.tests;

import backend.handlers.MeHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.AuthSession;
import backend.models.User;
import backend.repositories.UserRepository;
import backend.services.BearerTokenExtractor;
import backend.services.SessionService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeHandlerTest {
    private static final String TOKEN = "A".repeat(43);
    private SessionService sessionService;
    private UserRepository userRepository;
    private MeHandler handler;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        userRepository = mock(UserRepository.class);
        handler = new MeHandler(
            sessionService,
            userRepository,
            new BearerTokenExtractor()
        );
    }

    @Test
    void tokenValidoRetornaSomenteDadosPublicosDoUsuarioCorreto() {
        AuthSession session = session("user-1");
        User user = user("user-1", "Usuário Um", "um@example.com");
        when(sessionService.resolve(TOKEN)).thenReturn(Optional.of(session));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        HttpResponse response = handler.handle(request(TOKEN, "{\"userId\":\"user-2\"}"));
        Document body = Document.parse(response.getBody());

        assertEquals(200, response.getStatusCode());
        assertEquals("user-1", body.getString("id"));
        assertEquals("Usuário Um", body.getString("name"));
        assertEquals("um@example.com", body.getString("email"));
        assertFalse(body.containsKey("passwordHash"));
        verify(userRepository).findById("user-1");
        verify(userRepository, never()).findById("user-2");
    }

    @Test
    void ausenciaDeTokenRetorna401() {
        HttpRequest request = new HttpRequest("GET", "/auth/me");

        assertEquals(401, handler.handle(request).getStatusCode());
        verify(sessionService, never()).resolve(TOKEN);
    }

    @Test
    void tokenInvalidoRetorna401() {
        assertEquals(401, handler.handle(request("inválido", "")).getStatusCode());
    }

    @Test
    void sessaoExpiradaRetorna401() {
        when(sessionService.resolve(TOKEN)).thenReturn(Optional.empty());

        assertEquals(401, handler.handle(request(TOKEN, "")).getStatusCode());
    }

    @Test
    void sessaoRevogadaRetorna401() {
        when(sessionService.resolve(TOKEN)).thenReturn(Optional.empty());

        assertEquals(401, handler.handle(request(TOKEN, "")).getStatusCode());
    }

    @Test
    void usuarioDaSessaoInexistenteRetorna401() {
        when(sessionService.resolve(TOKEN)).thenReturn(Optional.of(session("missing")));
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertEquals(401, handler.handle(request(TOKEN, "")).getStatusCode());
    }

    private HttpRequest request(String token, String body) {
        return new HttpRequest(
            "GET",
            "/auth/me",
            "HTTP/1.1",
            Map.of("Authorization", "Bearer " + token),
            body
        );
    }

    private AuthSession session(String userId) {
        Instant now = Instant.parse("2026-11-14T12:00:00Z");
        return new AuthSession("hash", userId, now, now.plusSeconds(3600), null);
    }

    private User user(String id, String name, String email) {
        return new User(
            id,
            name,
            email,
            email,
            "hash",
            "salt",
            "PBKDF2WithHmacSHA256",
            600_000
        );
    }
}
