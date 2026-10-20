package backend.src.http;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Representa uma resposta HTTP devolvida por um HttpHandler ou pelo Router.
 * Contém statusCode, reasonPhrase, headers e body (alinhado com a tarefa #9).
 */
public class HttpResponse {
    private final int statusCode;
    private final String reasonPhrase;
    private final Map<String, String> headers;
    private final String body;

    public HttpResponse(int statusCode, String reasonPhrase, String body) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.body = body != null ? body : "";
        this.headers = new HashMap<>();
        this.headers.put("Content-Type", "application/json; charset=utf-8");
        this.headers.put("Content-Length", String.valueOf(this.body.getBytes(StandardCharsets.UTF_8).length));
    }

    public static HttpResponse ok(String jsonBody) {
        return new HttpResponse(200, "OK", jsonBody);
    }

    public static HttpResponse badRequest(String message) {
        return new HttpResponse(400, "Bad Request", "{\"error\":\"" + message + "\"}");
    }

    public static HttpResponse notFound(String message) {
        return new HttpResponse(404, "Not Found", "{\"error\":\"" + message + "\"}");
    }

    public static HttpResponse methodNotAllowed(String message) {
        return new HttpResponse(405, "Method Not Allowed", "{\"error\":\"" + message + "\"}");
    }

    public static HttpResponse internalServerError(String message) {
        return new HttpResponse(500, "Internal Server Error", "{\"error\":\"" + message + "\"}");
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "HTTP/1.1 " + statusCode + " " + reasonPhrase + "\n" +
               "Content-Type: " + headers.get("Content-Type") + "\n" +
               "Content-Length: " + headers.get("Content-Length") + "\n\n" +
               body;
    }
}
