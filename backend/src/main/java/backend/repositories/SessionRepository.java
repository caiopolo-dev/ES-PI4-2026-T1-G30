// Desenvolvido por Caio Ferreira Polo.
package backend.repositories;

import java.time.Instant;
import java.util.Optional;

import backend.models.AuthSession;

/**
 * Define as operações necessárias para persistir e encerrar sessões.
 * O SessionService utiliza esta interface sem depender diretamente do MongoDB.
 */
public interface SessionRepository {

    // Armazena a sessão criada depois que o usuário conclui o login.
    void save(AuthSession session);

    // A busca usa o hash do token e retorna Optional porque a sessão pode não existir.
    Optional<AuthSession> findByTokenHash(String tokenHash);

    // Registra o momento do encerramento sem precisar apagar imediatamente o documento.
    boolean revoke(String tokenHash, Instant revokedAt);
}
