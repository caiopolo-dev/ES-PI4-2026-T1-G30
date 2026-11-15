package backend.auth;

import backend.http.HttpRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação em memória de SessionAuthenticator baseada no header "Authorization: Bearer <token>".
 * Suporta registro de sessões ativas com tempo de expiração para validar cenários de
 * sessão ausente, inválida ou expirada (401 Unauthorized).
 */
public class InMemorySessionAuthenticator implements SessionAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemorySessionAuthenticator() {
        this(Clock.systemUTC());
    }

    public InMemorySessionAuthenticator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock não pode ser null");
    }

    public void registerSession(String token, String userId) {
        registerSession(token, userId, null);
    }

    public void registerSession(String token, String userId, Instant expiresAt) {
        Objects.requireNonNull(token, "token não pode ser null");
        Objects.requireNonNull(userId, "userId não pode ser null");
        if (token.trim().isEmpty() || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("token e userId não podem ser vazios");
        }
        sessions.put(token.trim(), new SessionEntry(userId.trim(), expiresAt));
    }

    public void revokeSession(String token) {
        if (token != null) {
            sessions.remove(token.trim());
        }
    }

    @Override
    public Optional<String> resolveAuthenticatedUserId(HttpRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String authHeader = request.getHeader("authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        SessionEntry entry = sessions.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt != null && clock.instant().isAfter(entry.expiresAt)) {
            return Optional.empty();
        }
        return Optional.of(entry.userId);
    }

    private static final class SessionEntry {
        private final String userId;
        private final Instant expiresAt;

        private SessionEntry(String userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
