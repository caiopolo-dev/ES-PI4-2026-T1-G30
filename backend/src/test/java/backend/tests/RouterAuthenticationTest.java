package backend.tests;

import backend.auth.InMemorySessionAuthenticator;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.router.Router;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouterAuthenticationTest {
    @Test
    void publicRoutesBypassAuthentication() {
        Router router = new Router(request -> { throw new AssertionError(); });
        for (String[] route : new String[][] {{"GET", "/health"}, {"POST", "/auth/register"}, {"POST", "/auth/login"}}) {
            router.register(route[0], route[1], request -> HttpResponse.ok("{}"));
            assertEquals(200, router.route(new HttpRequest(route[0], route[1])).getStatusCode());
        }
    }

    @Test
    void privateRoutesDenyByDefaultBeforeExecutingHandler() {
        Router router = new Router();
        router.register("GET", "/new-private", request -> { throw new AssertionError(); });
        assertEquals(401, router.route(new HttpRequest("GET", "/new-private?userId=other")).getStatusCode());
    }

    @Test
    void contextUsesSessionAndPreservesRequest() {
        Router router = new Router(request -> Optional.of("user-a"));
        HttpRequest original = new HttpRequest("POST", "/private?userId=user-b", "HTTP/1.1", Map.of(), "user-b");
        router.register("POST", "/private", request -> {
            assertEquals("user-a", request.getAuthContext().orElseThrow().userId());
            assertEquals(original.getBody(), request.getBody());
            assertEquals(original.getRawQuery(), request.getRawQuery());
            return HttpResponse.ok("{}");
        });
        assertEquals(200, router.route(original).getStatusCode());
        assertTrue(original.getAuthContext().isEmpty());
    }

    @Test
    void expiredRevokedAndUnknownSessionsFail() {
        Instant now = Instant.parse("2026-11-15T12:00:00Z");
        var sessions = new InMemorySessionAuthenticator(Clock.fixed(now, ZoneOffset.UTC));
        sessions.registerSession("expired", "a", now.minusSeconds(1));
        sessions.registerSession("revoked", "a", now.plusSeconds(60));
        sessions.revokeSession("revoked");
        Router router = new Router(sessions);
        router.register("GET", "/private", request -> { throw new AssertionError(); });
        for (String token : List.of("expired", "revoked", "unknown")) {
            assertEquals(401, router.route(new HttpRequest("GET", "/private", Map.of("Authorization", "Bearer " + token))).getStatusCode());
        }
    }
}
