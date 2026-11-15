package backend.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Representa uma requisição HTTP recebida pelo servidor.
 * Contém method, path, rawQuery, httpVersion, headers e body.
 */
public class HttpRequest {
    private final String method;      // Ex: "GET", "POST"
    private final String path;        // Ex: "/health", "/medications", "/doses"
    private final String rawQuery;    // Ex: "date=2026-11-05" (ou "" quando ausente)
    private final String httpVersion; // Ex: "HTTP/1.1"
    private final Map<String, String> headers;
    private final String body;

    public HttpRequest(String method, String path, String httpVersion, Map<String, String> headers, String body) {
        this.method = method != null ? method.toUpperCase() : "";
        String rawTarget = path != null ? path : "";
        String[] targetParts = rawTarget.split("\\?", 2);
        this.path = targetParts[0];
        this.rawQuery = targetParts.length > 1 ? targetParts[1] : "";
        this.httpVersion = httpVersion != null ? httpVersion : "HTTP/1.1";
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.body = body != null ? body : "";
    }

    public HttpRequest(String method, String path) {
        this(method, path, "HTTP/1.1", new HashMap<>(), "");
    }

    public HttpRequest(String method, String path, Map<String, String> headers) {
        this(method, path, "HTTP/1.1", headers, "");
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String getBody() {
        return body;
    }
}
