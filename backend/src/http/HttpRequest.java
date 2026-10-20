package backend.src.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Representa uma requisição HTTP recebida pelo servidor.
 * Contém method, path, httpVersion, headers e body (alinhado com a tarefa #9).
 */
public class HttpRequest {
    private final String method;      // Ex: "GET", "POST"
    private final String path;        // Ex: "/health", "/medications"
    private final String httpVersion; // Ex: "HTTP/1.1"
    private final Map<String, String> headers;
    private final String body;

    public HttpRequest(String method, String path, String httpVersion, Map<String, String> headers, String body) {
        this.method = method != null ? method.toUpperCase() : "";
        this.path = path != null ? path : "";
        this.httpVersion = httpVersion != null ? httpVersion : "HTTP/1.1";
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.body = body != null ? body : "";
    }

    public HttpRequest(String method, String path) {
        this(method, path, "HTTP/1.1", new HashMap<>(), "");
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public String getBody() {
        return body;
    }
}
