package backend.tests;

import backend.models.AuthSession;
import backend.repositories.SessionRepository;
import backend.services.SessionService;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionServiceTest {
    private static final Instant START = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void deveGerarTokenNoFormatoEsperadoESalvarSomenteHash() {
        FakeSessionRepository repository = new FakeSessionRepository();
        SessionService service = serviceAt(repository, START);

        String token = service.create("user-1");
        AuthSession saved = repository.savedSession;

        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(token, saved.getTokenHash());
        assertTrue(saved.getTokenHash().matches("[0-9a-f]{64}"));
        assertEquals("user-1", saved.getUserId());
        assertEquals(START, saved.getCreatedAt());
        assertEquals(START.plusSeconds(24 * 60 * 60), saved.getExpiresAt());
    }

    @Test
    void deveResolverSessaoValida() {
        FakeSessionRepository repository = new FakeSessionRepository();
        SessionService service = serviceAt(repository, START);
        String token = service.create("user-1");

        assertEquals("user-1", service.resolve(token).orElseThrow().getUserId());
    }

    @Test
    void deveRejeitarSessaoExpirada() {
        FakeSessionRepository repository = new FakeSessionRepository();
        String token = serviceAt(repository, START).create("user-1");
        SessionService laterService = serviceAt(
            repository,
            START.plusSeconds(24 * 60 * 60)
        );

        assertTrue(laterService.resolve(token).isEmpty());
    }

    @Test
    void deveRejeitarSessaoRevogada() {
        FakeSessionRepository repository = new FakeSessionRepository();
        SessionService service = serviceAt(repository, START);
        String token = service.create("user-1");

        assertTrue(service.revoke(token));
        assertTrue(service.resolve(token).isEmpty());
    }

    @Test
    void deveRejeitarTokenInvalidoOuMalformado() {
        FakeSessionRepository repository = new FakeSessionRepository();
        SessionService service = serviceAt(repository, START);

        assertTrue(service.resolve("token-invalido").isEmpty());
        assertTrue(service.resolve(null).isEmpty());
        assertFalse(service.revoke("token-invalido"));
        assertEquals(0, repository.findCalls);
    }

    private SessionService serviceAt(FakeSessionRepository repository, Instant instant) {
        return new SessionService(
            repository,
            new SecureRandom(),
            Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private static final class FakeSessionRepository implements SessionRepository {
        private final Map<String, AuthSession> sessions = new HashMap<>();
        private AuthSession savedSession;
        private int findCalls;

        @Override
        public void save(AuthSession session) {
            savedSession = session;
            sessions.put(session.getTokenHash(), session);
        }

        @Override
        public Optional<AuthSession> findByTokenHash(String tokenHash) {
            findCalls++;
            return Optional.ofNullable(sessions.get(tokenHash));
        }

        @Override
        public boolean revoke(String tokenHash, Instant revokedAt) {
            AuthSession current = sessions.get(tokenHash);
            if (current == null || current.getRevokedAt() != null
                    || !current.getExpiresAt().isAfter(revokedAt)) {
                return false;
            }

            AuthSession revoked = new AuthSession(
                current.getTokenHash(),
                current.getUserId(),
                current.getCreatedAt(),
                current.getExpiresAt(),
                revokedAt
            );
            sessions.put(tokenHash, revoked);
            return true;
        }
    }
}
