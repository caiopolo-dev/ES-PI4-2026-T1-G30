package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.AuthSession;
import backend.router.HttpHandler;
import backend.services.BearerTokenExtractor;
import backend.services.SessionService;

import java.util.Objects;
import java.util.Optional;

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
        Optional<String> token = tokenExtractor.extract(request);
        if (token.isEmpty()) {
            return new HttpResponse(
                401,
                "Unauthorized",
                "{\"error\":\"Autenticação necessária.\"}"
            );
        }

        try {
            Optional<AuthSession> session = sessionService.resolve(token.get());
            if (session.isPresent()) {
                sessionService.revoke(token.get());
            }
            return new HttpResponse(204, "No Content", "");
        } catch (RuntimeException e) {
            return HttpResponse.internalServerError("Erro interno ao encerrar sessão.");
        }
    }
}
