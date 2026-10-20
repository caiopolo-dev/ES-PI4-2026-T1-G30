package backend.src.router;

import backend.src.http.HttpRequest;
import backend.src.http.HttpResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Mecanismo de roteamento HTTP do backend.
 * Responsável apenas por decidir qual HttpHandler atende uma requisição
 * com base no método HTTP (GET, POST, etc.) e no caminho (path).
 */
public class Router {

    // Mapa que relaciona: path -> (method -> HttpHandler)
    // Ex: "/health" -> { "GET" -> HealthHandler }
    private final Map<String, Map<String, HttpHandler>> routes = new HashMap<>();

    /**
     * Registra centralizadamente uma rota relacionando método HTTP + path a um handler.
     * Exemplo: router.register("GET", "/health", healthHandler);
     */
    public void register(String method, String path, HttpHandler handler) {
        routes.computeIfAbsent(path, k -> new HashMap<>())
              .put(method.toUpperCase(), handler);
    }

    /**
     * Decide qual handler atende a requisição recebida:
     * - Se o path não existir -> retorna 404 Not Found
     * - Se o path existir, mas o método HTTP não for suportado -> retorna 405 Method Not Allowed
     * - Se encontrar o handler -> executa handler.handle(request) e retorna a resposta (ex: 200 OK)
     */
    public HttpResponse route(HttpRequest request) {
        String path = request.getPath();
        String method = request.getMethod().toUpperCase();

        // 1. Verifica se a rota (path) existe
        Map<String, HttpHandler> handlersByMethod = routes.get(path);
        if (handlersByMethod == null) {
            return HttpResponse.notFound("Route not found: " + path);
        }

        // 2. Verifica se o método HTTP é permitido para essa rota
        HttpHandler handler = handlersByMethod.get(method);
        if (handler == null) {
            return HttpResponse.methodNotAllowed(
                "Method " + method + " not allowed for route " + path
            );
        }

        // 3. Delega o processamento para o handler correspondente
        return handler.handle(request);
    }
}
