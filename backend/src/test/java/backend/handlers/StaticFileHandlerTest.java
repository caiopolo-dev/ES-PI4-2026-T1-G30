package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticFileHandlerTest {

    private final byte[] binary = new byte[] {0, 1, 2, (byte) 0xff};
    private final StaticFileHandler handler = new StaticFileHandler(new ResourceClassLoader(Map.of(
            "web/index.html", "<main>DoseCerta</main>".getBytes(StandardCharsets.UTF_8),
            "web/assets/app.js", "console.log('ok')".getBytes(StandardCharsets.UTF_8),
            "web/assets/image.png", binary,
            "web/favicon.svg", "<svg/>".getBytes(StandardCharsets.UTF_8)
    )));

    @Test
    void servesIndexAssetsAndFaviconWithCorrectContentType() {
        assertResponse("/", "text/html; charset=utf-8");
        assertResponse("/assets/app.js", "application/javascript; charset=utf-8");
        assertResponse("/favicon.svg", "image/svg+xml; charset=utf-8");
    }

    @Test
    void preservesBinaryAssets() {
        HttpResponse response = handler.handle(new HttpRequest("GET", "/assets/image.png"));

        assertEquals(200, response.getStatusCode());
        assertEquals("image/png", response.getHeaders().get("Content-Type"));
        assertArrayEquals(binary, response.getBodyBytes());
    }

    @Test
    void returnsNotFoundForMissingOrUnsafeResources() {
        assertEquals(404, handler.handle(new HttpRequest("GET", "/assets/missing.js")).getStatusCode());
        assertEquals(404, handler.handle(new HttpRequest("GET", "/assets/../secret")).getStatusCode());
        assertEquals(404, handler.handle(new HttpRequest("GET", "/unknown")).getStatusCode());
    }

    private void assertResponse(String path, String contentType) {
        HttpResponse response = handler.handle(new HttpRequest("GET", path));
        assertEquals(200, response.getStatusCode());
        assertEquals(contentType, response.getHeaders().get("Content-Type"));
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, byte[]> resources;

        private ResourceClassLoader(Map<String, byte[]> resources) {
            super(null);
            this.resources = resources;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            byte[] value = resources.get(name);
            return value == null ? null : new ByteArrayInputStream(value);
        }
    }
}
