// Desenvolvido por Caio Ferreira Polo.
package backend.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Limita as tentativas de login por identificador e também no processo inteiro.
 * Essa proteção dificulta testes repetidos de senha durante uma janela de tempo.
 */
public final class LoginRateLimiter {
    private static final int DEFAULT_IDENTIFIER_LIMIT = 5;
    private static final int DEFAULT_GLOBAL_LIMIT = 100;
    private static final int DEFAULT_MAX_IDENTIFIERS = 1_000;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);

    private final int identifierLimit;
    private final int globalLimit;
    private final int maxIdentifiers;
    private final Duration window;
    private final Clock clock;
    private final Map<String, AttemptWindow> attemptsByIdentifier;

    private Instant globalWindowStartedAt;
    private int globalAttempts;

    public LoginRateLimiter() {
        this(
            DEFAULT_IDENTIFIER_LIMIT,
            DEFAULT_GLOBAL_LIMIT,
            DEFAULT_MAX_IDENTIFIERS,
            DEFAULT_WINDOW,
            Clock.systemUTC()
        );
    }

    public LoginRateLimiter(int identifierLimit, int globalLimit,
                            int maxIdentifiers, Duration window, Clock clock) {
        // Limites não positivos impediriam o controle correto das tentativas.
        if (identifierLimit <= 0 || globalLimit <= 0 || maxIdentifiers <= 0) {
            throw new IllegalArgumentException("Limites devem ser positivos.");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Janela deve ser positiva.");
        }

        this.identifierLimit = identifierLimit;
        this.globalLimit = globalLimit;
        this.maxIdentifiers = maxIdentifiers;
        this.window = window;
        this.clock = Objects.requireNonNull(clock);
        this.attemptsByIdentifier = new HashMap<>();
        this.globalWindowStartedAt = Instant.now(clock);
    }

    public synchronized boolean allow(String identifier) {
        // synchronized mantém as contagens consistentes quando vários logins chegam juntos.
        String normalizedIdentifier = normalize(identifier);
        Instant now = Instant.now(clock);

        resetGlobalWindowIfExpired(now);
        removeExpiredIdentifiers(now);

        // O limite global protege o servidor mesmo quando são usados vários identificadores.
        if (globalAttempts >= globalLimit) {
            return false;
        }

        AttemptWindow identifierWindow = attemptsByIdentifier.get(normalizedIdentifier);
        if (identifierWindow == null) {
            // Também limito quantos identificadores ficam guardados em memória.
            if (attemptsByIdentifier.size() >= maxIdentifiers) {
                return false;
            }
            identifierWindow = new AttemptWindow(now);
            attemptsByIdentifier.put(normalizedIdentifier, identifierWindow);
        }

        // Cada e-mail possui sua própria contagem dentro da janela atual.
        if (identifierWindow.attempts >= identifierLimit) {
            return false;
        }

        identifierWindow.attempts++;
        globalAttempts++;
        return true;
    }

    private String normalize(String identifier) {
        // Normalizar evita contagens separadas apenas por espaços ou letras maiúsculas.
        if (identifier == null || identifier.isBlank()) {
            return "<invalid>";
        }
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private void resetGlobalWindowIfExpired(Instant now) {
        if (!now.isBefore(globalWindowStartedAt.plus(window))) {
            globalWindowStartedAt = now;
            globalAttempts = 0;
        }
    }

    private void removeExpiredIdentifiers(Instant now) {
        // Removo janelas vencidas para liberar memória e permitir novas tentativas.
        Iterator<Map.Entry<String, AttemptWindow>> iterator =
            attemptsByIdentifier.entrySet().iterator();

        while (iterator.hasNext()) {
            AttemptWindow attemptWindow = iterator.next().getValue();
            if (!now.isBefore(attemptWindow.startedAt.plus(window))) {
                iterator.remove();
            }
        }
    }

    private static final class AttemptWindow {
        private final Instant startedAt;
        private int attempts;

        private AttemptWindow(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
