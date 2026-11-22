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
        // Sem autenticador configurado, nenhuma requisicao consegue acessar uma rota privada.
        this(request -> java.util.Optional.empty());
    }

    public Router(backend.auth.SessionAuthenticator authenticator) {
        this.authenticator = java.util.Objects.requireNonNull(authenticator);
    }


    // Mapa que relaciona: path -> (method -> HttpHandler)
    // Ex: "/health" -> { "GET" -> HealthHandler }
    private final Map<String, Map<String, HttpHandler>> routes = new java.util.LinkedHashMap<>();

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


        // Se não houver correspondência exata,
        // procura uma rota dinâmica como /treatments/{id}.
        if (handlersByMethod == null) {
            for (var route : routes.entrySet()) {
                if (matches(route.getKey(), path)) {
                    handlersByMethod = route.getValue();
                    break;
                }
            }
        }

        // Nenhuma rota encontrada: HTTP 404.
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
        if (RouteAccessPolicy.requiresAuthentication(method, path)) {
            // Centralizei a autenticacao aqui para os handlers receberem a identidade ja validada.
            try {
                var userId = authenticator.resolveAuthenticatedUserId(request);
                if (userId.isEmpty() || userId.get().isBlank()) {
                    AccessAudit.record(AccessAudit.Event.AUTHENTICATION_REQUIRED);
                    return HttpResponse.unauthorized("Invalid, expired, or missing session");
                }
                // O contexto usa o ID da sessao, nao um userId escolhido no corpo ou na query.
                return execute(handler, request.withAuthContext(new backend.auth.AuthContext(userId.get())));
            } catch (RuntimeException error) {
                return HttpResponse.internalServerError("Internal server error");
            }
        }
        return execute(handler, request);
    }

    // Permite IDs no caminho e preserva a prioridade das rotas exatas.
    private boolean matches(String template, String path) {
        if (!template.contains("{")) { return false; }
        String[] expected = template.split("/", -1);
        String[] actual = path.split("/", -1);
        if (expected.length != actual.length) { return false; }
        for (int index = 0; index < expected.length; index++) {
            if (expected[index].startsWith("{") && expected[index].endsWith("}")) {
                if (actual[index].isEmpty()) { return false; }
            } else if (!expected[index].equals(actual[index])) { return false; }
        }
        return true;
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
            // Recurso inexistente e recurso alheio recebem a mesma resposta para nao revelar sua existencia.
            AccessAudit.record(AccessAudit.Event.RESOURCE_UNAVAILABLE);
            return HttpResponse.notFound("Recurso nao encontrado.");
        } catch (backend.auth.AccessDeniedException error) {
            // Permissao negada e diferente de sessao ausente; por isso a resposta e 403.
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
