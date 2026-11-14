package backend.tests;

import backend.handlers.LogoutHandler;
import backend.handlers.MeHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.AuthSession;
import backend.models.User;
import backend.repositories.SessionRepository;
import backend.repositories.UserRepository;
import backend.services.BearerTokenExtractor;
import backend.services.SessionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogoutHandlerTest {
    private static final String TOKEN = "A".repeat(43);

    @Test
    void tokenValidoERevogadoERetorna204SemCorpo() {
        SessionService sessionService = mock(SessionService.class);
        AuthSession session = session("user-1");
        when(sessionService.resolve(TOKEN)).thenReturn(Optional.of(session));
        LogoutHandler handler = handler(sessionService);

        HttpResponse response = handler.handle(request(TOKEN));

        assertEquals(204, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
        verify(sessionService).revoke(TOKEN);
    }

    @Test
    void tokenValidoMasInexistenteExpiradoOuRevogadoRetorna204() {
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.resolve(TOKEN)).thenReturn(Optional.empty());
        LogoutHandler handler = handler(sessionService);

        assertEquals(204, handler.handle(request(TOKEN)).getStatusCode());
        verify(sessionService, never()).revoke(TOKEN);
    }

    @Test
    void bearerAusenteOuMalformadoRetorna401() {
        SessionService sessionService = mock(SessionService.class);
        LogoutHandler handler = handler(sessionService);

        assertEquals(401, handler.handle(new HttpRequest("POST", "/auth/logout")).getStatusCode());
        assertEquals(401, handler.handle(request("inválido")).getStatusCode());
    }

    @Test
    void logoutInvalidaTokenImediatamenteSemRevogarOutraSessao() {
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        SessionService sessionService = new SessionService(sessions);
        String firstToken = sessionService.create("user-1");
        String secondToken = sessionService.create("user-1");
        BearerTokenExtractor extractor = new BearerTokenExtractor();
        LogoutHandler logoutHandler = new LogoutHandler(sessionService, extractor);

        UserRepository users = mock(UserRepository.class);
        when(users.findById("user-1")).thenReturn(Optional.of(user()));
        MeHandler meHandler = new MeHandler(sessionService, users, extractor);

        assertEquals(204, logoutHandler.handle(request(firstToken)).getStatusCode());
        assertEquals(401, meHandler.handle(meRequest(firstToken)).getStatusCode());
        assertEquals(200, meHandler.handle(meRequest(secondToken)).getStatusCode());
    }

    private LogoutHandler handler(SessionService sessionService) {
        return new LogoutHandler(sessionService, new BearerTokenExtractor());
    }

    private HttpRequest request(String token) {
        return new HttpRequest(
            "POST",
            "/auth/logout",
            "HTTP/1.1",
            Map.of("Authorization", "Bearer " + token),
            ""
        );
    }

    private HttpRequest meRequest(String token) {
        return new HttpRequest(
            "GET",
            "/auth/me",
            "HTTP/1.1",
            Map.of("Authorization", "Bearer " + token),
            ""
        );
    }

    private AuthSession session(String userId) {
        Instant now = Instant.now();
        return new AuthSession("hash", userId, now, now.plusSeconds(3600), null);
    }

    private User user() {
        return new User(
            "user-1",
            "Usuário Um",
            "um@example.com",
            "um@example.com",
            "hash",
            "salt",
            "PBKDF2WithHmacSHA256",
            600_000
        );
    }

    private static final class InMemorySessionRepository implements SessionRepository {
        private final Map<String, AuthSession> sessions = new HashMap<>();

        @Override
        public void save(AuthSession session) {
            sessions.put(session.getTokenHash(), session);
        }

        @Override
        public Optional<AuthSession> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(sessions.get(tokenHash));
        }

        @Override
        public boolean revoke(String tokenHash, Instant revokedAt) {
            AuthSession current = sessions.get(tokenHash);
            if (current == null || current.getRevokedAt() != null
                    || !current.getExpiresAt().isAfter(revokedAt)) {
                return false;
            }
            sessions.put(tokenHash, new AuthSession(
                current.getTokenHash(),
                current.getUserId(),
                current.getCreatedAt(),
                current.getExpiresAt(),
                revokedAt
            ));
            return true;
        }
    }
}
