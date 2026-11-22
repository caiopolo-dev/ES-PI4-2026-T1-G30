package backend.tests;

import backend.handlers.StaticFileHandler;
import backend.http.HttpResponse;
import backend.router.Router;
import backend.server.Servidor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticFrontendHttpTest {
    private static final Pattern SCRIPT_SOURCE = Pattern.compile("<script[^>]+src=\"([^\"]+\\.js)\"");
    private static final Pattern STYLESHEET = Pattern.compile("<link[^>]+href=\"([^\"]+\\.css)\"");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private Servidor servidor;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        Router router = new Router();
        StaticFileHandler staticFiles = new StaticFileHandler();
        router.register("GET", "/", staticFiles);
        router.register("GET", "/favicon.svg", staticFiles);
        router.register("GET", "/assets/{file}", staticFiles);
        router.register("GET", "/treatments", request -> HttpResponse.ok("[]"));

        int port = availablePort();
        servidor = new Servidor(port, router);
        Thread thread = new Thread(servidor::aceitadorDeConexao, "static-frontend-http-test");
        thread.setDaemon(true);
        thread.start();
        baseUrl = "http://127.0.0.1:" + port;
        awaitServer();
    }

    @AfterEach
    void stopServer() {
        servidor.parar();
    }

    @Test
    void servesReactAndItsGeneratedAssetsThroughJava() throws Exception {
        java.net.http.HttpResponse<String> index = get("/");

        assertEquals(200, index.statusCode());
        assertTrue(index.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(index.body().contains("<div id=\"root\"></div>"));

        String script = match(index.body(), SCRIPT_SOURCE);
        String stylesheet = match(index.body(), STYLESHEET);
        assertContentType(script, "application/javascript");
        assertContentType(stylesheet, "text/css");
        assertContentType("/favicon.svg", "image/svg+xml");
    }

    @Test
    void keepsUnknownFilesAndProtectedApisSeparated() throws Exception {
        assertEquals(404, get("/assets/missing.js").statusCode());
        assertEquals(404, get("/unknown").statusCode());
        assertEquals(401, get("/treatments").statusCode());

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        assertEquals(405, client.send(request, BodyHandlers.ofString()).statusCode());
    }

    private java.net.http.HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return client.send(request, BodyHandlers.ofString());
    }

    private void assertContentType(String path, String expected) throws Exception {
        java.net.http.HttpResponse<String> response = get(path);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith(expected));
        assertTrue(response.headers().firstValueAsLong("Content-Length").orElse(0) > 0);
    }

    private String match(String html, Pattern pattern) {
        Matcher matcher = pattern.matcher(html);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private void awaitServer() throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                if (get("/").statusCode() == 200) {
                    return;
                }
            } catch (IOException error) {
                lastError = error;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("Servidor não iniciou a tempo.", lastError);
    }

    private int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
