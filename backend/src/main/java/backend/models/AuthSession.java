
package backend.models;

import java.time.Instant;

// Representa uma sessão persistida sem armazenar o token original.
public final class AuthSession {

    private final String tokenHash;
    private final String userId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant revokedAt;

    public AuthSession(String tokenHash, String userId, Instant createdAt, Instant expiresAt, Instant revokedAt){
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
