// Autor: Samuel Campovilla
package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.router.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;

// Serve o build do React empacotado sob o diretório web do classpat
public final class StaticFileHandler implements HttpHandler {
    private static final String WEB_ROOT = "web/";

    private final ClassLoader classLoader;

    public StaticFileHandler() {
        this(StaticFileHandler.class.getClassLoader());
    }

    StaticFileHandler(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        String resource = resourceFor(request.getPath());
        if (resource == null) {
            return HttpResponse.notFound("Arquivo não encontrado.");
        }

        try (InputStream input = classLoader.getResourceAsStream(WEB_ROOT + resource)) {
            if (input == null) {
                return HttpResponse.notFound("Arquivo não encontrado.");
            }

            HttpResponse response = new HttpResponse(
                    200,
                    "OK",
                    input.readAllBytes(),
                    contentType(resource)
            );
            if (resource.startsWith("assets/")) {
                response.getHeaders().put("Cache-Control", "public, max-age=31536000, immutable");
            } else {
                response.getHeaders().put("Cache-Control", "no-cache");
            }
            return response;
        } catch (IOException error) {
            return HttpResponse.internalServerError("Erro ao carregar a interface.");
        }
    }

    private String resourceFor(String path) {
        if ("/".equals(path)) {
            return "index.html";
        }
        if ("/favicon.svg".equals(path)) {
            return "favicon.svg";
        }
        if (path != null && path.startsWith("/assets/") && path.length() > "/assets/".length()) {
            String resource = path.substring(1);
            if (!resource.contains("..") && resource.indexOf('\\') < 0 && resource.indexOf('%') < 0) {
                return resource;
            }
        }
        return null;
    }

    private String contentType(String resource) {
        String normalized = resource.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (normalized.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (normalized.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (normalized.endsWith(".svg")) {
            return "image/svg+xml; charset=utf-8";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        if (normalized.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "application/octet-stream";
    }
}
