package backend.src.handlers;

import backend.src.http.HttpRequest;
import backend.src.http.HttpResponse;
import backend.src.router.HttpHandler;

/**
 * Handler responsável pelo endpoint GET /health.
 * Retorna status 200 OK com {"status":"ok"}.
 */
public class HealthHandler implements HttpHandler {

    @Override
    public HttpResponse handle(HttpRequest request) {
        return HttpResponse.ok("{\"status\":\"ok\"}");
    }
}

