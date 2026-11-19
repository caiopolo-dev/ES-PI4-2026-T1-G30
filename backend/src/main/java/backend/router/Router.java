package backend.router;

import backend.auth.AccessAudit;
import backend.http.HttpRequest;
import backend.http.HttpResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Mecanismo de roteamento HTTP do backend.
 * Responsável apenas por decidir qual HttpHandler atende uma requisição
 * com base no método HTTP (GET, POST, etc.) e no caminho (path).
 */
public class Router {
    private final backend.auth.SessionAuthenticator authenticator;

    public Router() {
        this(request -> java.util.Optional.empty());
    }

    public Router(backend.auth.SessionAuthenticator authenticator) {
        this.authenticator = java.util.Objects.requireNonNull(authenticator);
    }


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

        // 1. Primeiro procura uma rota com caminho exatamente igual
        Map<String, HttpHandler> handlersByMethod = routes.get(path);
        // Map.Entry<String, HtttpHandler>: representa uma entrada do mapa, com chave String e valor HttpHandler.


        // Se não encontrar, procura uma rota com parâmetros, como /treatments/{id}
        if (handlersByMethod == null) {
            for (Map.Entry<String, Map<String, HttpHandler>> entry : routes.entrySet()) {

                if (caminhoCorrespondente(entry.getKey(), path)) {
                    handlersByMethod = entry.getValue();
                    break;
                }
            }
        }

        // 2. Verifica se o método HTTP é permitido para essa rota
        HttpHandler handler = handlersByMethod.get(method);
        if (handler == null) {
            return HttpResponse.methodNotAllowed(
                "Method " + method + " not allowed for route " + path
            );
        }

        // 3. Delega o processamento para o handler correspondente
        if (RouteAccessPolicy.requiresAuthentication(method, path)) {
            try {
                var userId = authenticator.resolveAuthenticatedUserId(request);
                if (userId.isEmpty() || userId.get().isBlank()) {
                    AccessAudit.record(AccessAudit.Event.AUTHENTICATION_REQUIRED);
                    return HttpResponse.unauthorized("Invalid, expired, or missing session");
                }
                return execute(handler, request.withAuthContext(new backend.auth.AuthContext(userId.get())));
            } catch (RuntimeException error) {
                return HttpResponse.internalServerError("Internal server error");
            }
        }
        return execute(handler, request);
    }

    private HttpResponse execute(HttpHandler handler, HttpRequest request) {
        try {
            HttpResponse response = handler.handle(request);
            if (response.getStatusCode() == 401) {
                AccessAudit.record(AccessAudit.Event.AUTHENTICATION_REQUIRED);
            } else if (response.getStatusCode() == 403) {
                AccessAudit.record(AccessAudit.Event.PERMISSION_DENIED);
            }
            return response;
        } catch (backend.auth.ResourceNotFoundException error) {
            AccessAudit.record(AccessAudit.Event.RESOURCE_UNAVAILABLE);
            return HttpResponse.notFound("Recurso nao encontrado.");
        } catch (backend.auth.AccessDeniedException error) {
            AccessAudit.record(AccessAudit.Event.PERMISSION_DENIED);
            return HttpResponse.forbidden("Acesso negado.");
        } catch (RuntimeException error) {
            return HttpResponse.internalServerError("Internal server error");
        }
    }


    // Compara uma rota registrada com o caminho real da requisição.
    // Exemplo:
    // /treatments/{id} corresponde a /treatments/123
    // /treatments/{id}/close corresponde a /treatments/123/close
    private boolean caminhoCorrespondente(String rota, String caminho) {

        String[] partesRota = rota.split("/", -1);
        String[] partesCaminho = caminho.split("/", -1);

        // Caminhos com quantidades diferentes de segmentos não correspondem
        if (partesRota.length != partesCaminho.length) {
            return false;
        }

        for (int i = 0; i < partesRota.length; i++) {

            String parteRota = partesRota[i];
            String parteCaminho = partesCaminho[i];

            // Uma parte entre chaves representa um parâmetro dinâmico
            if (parteRota.startsWith("{") && parteRota.endsWith("}")
                    && parteRota.length() > 2) {

                // O parâmetro precisa possuir algum valor
                if (parteCaminho.isEmpty()) {
                    return false;
                }

            } else if (!parteRota.equals(parteCaminho)) {
                return false;
            }
        }

        return true;
    }

}
