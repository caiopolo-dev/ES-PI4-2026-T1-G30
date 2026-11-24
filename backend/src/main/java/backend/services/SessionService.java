// Desenvolvido por Caio Ferreira Polo.
package backend.services;

import backend.models.AuthSession;
import backend.repositories.SessionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Cria, resolve e revoga as sessões usadas nas rotas autenticadas.
 * O token original fica com o cliente; o repository recebe somente seu hash.
 */
public class SessionService {

    private static final Duration SESSION_DURATION = Duration.ofHours(24);
    private static final int TOKEN_SIZE = 32;// tamanho em 32 bits para armazenar token

    private final SessionRepository sessionRepository;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public SessionService(SessionRepository sessionRepository) {
        this(sessionRepository, new SecureRandom(), Clock.systemUTC());
    }

    public SessionService(SessionRepository sessionRepository,SecureRandom secureRandom,Clock clock
    ) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.secureRandom = Objects.requireNonNull(secureRandom);
        this.clock = Objects.requireNonNull(clock);
    }

    public String create(String userId) {

        // Toda sessão precisa estar vinculada a um usuário identificado.
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Usuário inválido.");
        }

        byte[] randomBytes = new byte[TOKEN_SIZE];
        secureRandom.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        // Armazenar apenas o hash reduz o impacto caso os documentos sejam expostos.
        String tokenHash = hashToken(token);

        Instant createdAt = Instant.now(clock);
        Instant expiresAt = createdAt.plus(SESSION_DURATION);

        AuthSession session = new AuthSession(tokenHash, userId, createdAt, expiresAt, null );
        // A persistência permite validar o mesmo token nas próximas requisições.
        sessionRepository.save(session);

        return token;
    }

    public Optional<AuthSession> resolve(String token) {

        // Rejeito formatos inesperados antes de consultar o repository.
        if (!isValidTokenFormat(token)) {
            return Optional.empty();
        }

        String tokenHash = hashToken(token);

        Optional<AuthSession> session = sessionRepository.findByTokenHash(tokenHash);

        if (session.isEmpty()) {
            return Optional.empty();
        }

        AuthSession found = session.get();
        Instant now = Instant.now(clock);

        if (found.getRevokedAt() != null) {
            // Uma sessão encerrada por logout não pode autenticar novamente.
            return Optional.empty();
        }

        if (!found.getExpiresAt().isAfter(now)) {
            // Mesmo que o documento ainda exista no banco, a data limita sua validade.
            return Optional.empty();
        }

        return Optional.of(found);
    }

    public boolean revoke(String token) {

        // Um token malformado não identifica uma sessão que possa ser revogada.
        if (!isValidTokenFormat(token)) {
            return false;
        }

        String tokenHash = hashToken(token);

        return sessionRepository.revoke(
            tokenHash,
            Instant.now(clock)
        );
    }

    private boolean isValidTokenFormat(String token) {

        if (token == null || token.length() != 43) {
            return false;
        }

        return token.matches("[A-Za-z0-9_-]{43}");
    }

    private String hashToken(String token) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // SHA-256 produz a identificação persistida sem guardar o token original.
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.US_ASCII));//pega os bytes do token e aplica o sha-256 fazendo o hash
            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "Algoritmo SHA-256 indisponível.", e
            );
        }
    }
}
