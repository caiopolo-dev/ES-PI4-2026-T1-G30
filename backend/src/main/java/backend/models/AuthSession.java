// Desenvolvido por Caio Ferreira Polo.
package backend.models;

import java.time.Instant;

/**
 * Representa uma sessão autenticada vinculada a um usuário.
 * O banco guarda o hash do token, e não o token original recebido pelo cliente.
 */
public final class AuthSession {

    // tokenHash identifica a sessão com segurança, e userId indica seu titular.
    private final String tokenHash;
    private final String userId;

    // Estes instantes registram quando a sessão começou, quando perde a validade
    // e, se houver logout, quando foi revogada.
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant revokedAt;

    // Esta classe apenas representa os dados persistidos. A verificação de
    // expiração e revogação é realizada pelo SessionService ao resolver o token.
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
