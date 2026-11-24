package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.router.HttpHandler;

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

