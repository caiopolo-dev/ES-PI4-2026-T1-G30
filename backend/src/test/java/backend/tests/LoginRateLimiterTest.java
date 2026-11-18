package backend.tests;

import backend.services.LoginRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Test
    void tentativasAbaixoDoLimiteSaoPermitidasEAcimaSaoBloqueadas() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = limiter(3, 100, 100, clock);

        assertTrue(limiter.allow("User@example.com"));
        assertTrue(limiter.allow(" user@example.com "));
        assertTrue(limiter.allow("USER@EXAMPLE.COM"));
        assertFalse(limiter.allow("user@example.com"));
    }

    @Test
    void limiteGlobalImpedeContornoComIdentificadoresDiferentes() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = limiter(10, 2, 100, clock);

        assertTrue(limiter.allow("first@example.com"));
        assertTrue(limiter.allow("second@example.com"));
        assertFalse(limiter.allow("third@example.com"));
    }

    @Test
    void limiteSeRecuperaDepoisDaJanela() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = limiter(1, 10, 100, clock);

        assertTrue(limiter.allow("user@example.com"));
        assertFalse(limiter.allow("user@example.com"));

        clock.advance(WINDOW);

        assertTrue(limiter.allow("user@example.com"));
    }

    @Test
    void quantidadeDeIdentificadoresMantidosEmMemoriaETerminada() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = limiter(10, 100, 1, clock);

        assertTrue(limiter.allow("first@example.com"));
        assertFalse(limiter.allow("second@example.com"));

        clock.advance(WINDOW);

        assertTrue(limiter.allow("second@example.com"));
    }

    @Test
    void chamadasConcorrentesNaoUltrapassamOLimite() throws Exception {
        MutableClock clock = new MutableClock();
        final LoginRateLimiter limiter = limiter(5, 100, 100, clock);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                results.add(executor.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        start.await();
                        return limiter.allow("user@example.com");
                    }
                }));
            }

            start.countDown();
            int allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }

            assertEquals(5, allowed);
        } finally {
            executor.shutdownNow();
        }
    }

    private LoginRateLimiter limiter(int identifierLimit, int globalLimit,
                                     int maxIdentifiers, Clock clock) {
        return new LoginRateLimiter(
            identifierLimit,
            globalLimit,
            maxIdentifiers,
            WINDOW,
            clock
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-14T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
