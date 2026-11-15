package backend.tests;

import backend.config.MongoConnection;
import backend.dto.RegisterRequest;
import backend.handlers.*;
import backend.http.HttpResponse;
import backend.models.Treatment;
import backend.repositories.*;
import backend.router.Router;
import backend.server.Servidor;
import backend.services.*;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
class AuthorizationMongoIntegrationTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private int port;

    @Test
    void twoUsersLoginAccessAndLogoutUsingMongoSessionsAndRealHttp() throws Exception {
        String database = "dosecerta_test_auth_" + UUID.randomUUID().toString().replace("-", "");
        // Nunca usa o Atlas ou o banco compartilhado.
        try (MongoConnection connection = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
            try {
                var users = new MongoUserRepository(connection.getCollection("users"));
                var registration = new AuthService(users);
                var userA = registration.register(new RegisterRequest("Demo A", "a@example.com", "demo-password-123"));
                var userB = registration.register(new RegisterRequest("Demo B", "b@example.com", "demo-password-123"));
                var sessions = new SessionService(new MongoSessionRepository(connection.getCollection("auth_sessions")));
                var extractor = new BearerTokenExtractor();
                Router router = new Router(request -> extractor.extract(request).flatMap(sessions::resolve)
                    .map(session -> session.getUserId()));
                router.register("POST", "/auth/login", new LoginHandler(new LoginService(users), sessions, new LoginRateLimiter()));
                router.register("GET", "/auth/me", new MeHandler(sessions, users, extractor));
                router.register("POST", "/auth/logout", new LogoutHandler(sessions, extractor));
                var treatments = new MongoTreatmentRepository();
                treatments.save(new Treatment("treatment-a", userA.getId(), "Demo treatment"));
                var access = new TreatmentAccessService(treatments);
                // Rota exclusiva do teste: nao representa endpoint de produto implementado.
                router.register("GET", "/test/owned", request -> {
                    access.find(request.getAuthContext().orElseThrow(), "treatment-a");
                    return HttpResponse.ok("{}");
                });
                try (ServerSocket available = new ServerSocket(0)) { port = available.getLocalPort(); }
                Servidor server = new Servidor(port, router);
                Thread thread = new Thread(server::aceitadorDeConexao);
                thread.setDaemon(true);
                thread.start();
                try {
                    awaitServer();
                    String tokenA = login("a@example.com");
                    String tokenB = login("b@example.com");
                    assertEquals(userA.getId(), Document.parse(send("GET", "/auth/me", tokenA, "").body()).getString("id"));
                    assertEquals(userB.getId(), Document.parse(send("GET", "/auth/me", tokenB, "").body()).getString("id"));
                    assertEquals(200, send("GET", "/test/owned", tokenA, "").statusCode());
                    assertEquals(404, send("GET", "/test/owned?userId=" + userA.getId(), tokenB, "").statusCode());
                    assertEquals(401, send("GET", "/test/owned", null, "").statusCode());
                    for (Document stored : connection.getCollection("auth_sessions").find()) {
                        assertFalse(stored.toJson().contains(tokenA));
                        assertFalse(stored.toJson().contains(tokenB));
                        assertNotNull(stored.getString("tokenHash"));
                    }
                    // Outra conexao confirma que a sessao vem do MongoDB.
                    try (MongoConnection second = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
                        var reopened = new SessionService(new MongoSessionRepository(second.getCollection("auth_sessions")));
                        assertEquals(userA.getId(), reopened.resolve(tokenA).orElseThrow().getUserId());
                    }
                    assertEquals(204, send("POST", "/auth/logout", tokenA, "").statusCode());
                    assertEquals(401, send("GET", "/auth/me", tokenA, "").statusCode());
                    assertEquals(200, send("GET", "/auth/me", tokenB, "").statusCode());
                    connection.getCollection("auth_sessions").updateMany(new Document("userId", userB.getId()),
                        new Document("$set", new Document("expiresAt", new java.util.Date(0))));
                    assertEquals(401, send("GET", "/auth/me", tokenB, "").statusCode());
                } finally {
                    server.parar();
                    thread.join(3000);
                    assertFalse(thread.isAlive());
                }
            } finally {
                connection.getDatabase().drop();
            }
        }
    }

    private String login(String email) throws Exception {
        var response = send("POST", "/auth/login", null,
            new Document("email", email).append("password", "demo-password-123").toJson());
        assertEquals(200, response.statusCode());
        return Document.parse(response.body()).getString("token");
    }

    private java.net.http.HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private void awaitServer() throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            try { send("GET", "/auth/me", null, ""); return; }
            catch (java.io.IOException error) {
                if (attempt == 29) { throw error; }
                Thread.sleep(100);
            }
        }
    }
}
