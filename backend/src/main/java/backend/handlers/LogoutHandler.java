// Desenvolvido por Caio Ferreira Polo.
package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.AuthSession;
import backend.router.HttpHandler;
import backend.services.BearerTokenExtractor;
import backend.services.SessionService;

import java.util.Objects;
import java.util.Optional;

/**
 * Encerra a sessão indicada pelo token de autenticação da requisição.
 * O handler identifica a sessão, solicita sua revogação ao SessionService
 * e transforma o resultado em uma resposta HTTP.
 */
public final class LogoutHandler implements HttpHandler {
    private final SessionService sessionService;
    private final BearerTokenExtractor tokenExtractor;

    public LogoutHandler(SessionService sessionService,
                         BearerTokenExtractor tokenExtractor) {
        this.sessionService = Objects.requireNonNull(sessionService);
        this.tokenExtractor = Objects.requireNonNull(tokenExtractor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        // O extrator procura o Bearer Token no cabeçalho de autorização.
        // Sem um token válido, não há como identificar a sessão que deve ser encerrada.
        Optional<String> token = tokenExtractor.extract(request);
        if (token.isEmpty()) {
            return new HttpResponse(
                401,
                "Unauthorized",
                "{\"error\":\"Autenticação necessária.\"}"
            );
        }

        try {
            // resolve confirma que a sessão existe, ainda não expirou e não foi revogada.
            Optional<AuthSession> session = sessionService.resolve(token.get());
            if (session.isPresent()) {
                // A revogação invalida o token para as próximas requisições autenticadas.
                sessionService.revoke(token.get());
            }
            // O corpo fica vazio porque o logout não precisa devolver dados ao cliente.
            return new HttpResponse(204, "No Content", "");
        } catch (RuntimeException e) {
            // Falhas internas não expõem detalhes da sessão ou do banco na resposta.
            return HttpResponse.internalServerError("Erro interno ao encerrar sessão.");
        }
    }
}
