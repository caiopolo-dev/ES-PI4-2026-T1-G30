
package backend.repositories;

import java.time.Instant;
import java.util.Optional;

import backend.models.AuthSession;

public interface SessionRepository {

    void save(AuthSession session);
    // Busca uma sessao pelo has do token e retorna um option, pois pode retornar ou não uma sessão.
    Optional<AuthSession> findByTokenHash(String tokenHash);

    boolean revoke(String tokenHash, Instant revokedAt);
}
