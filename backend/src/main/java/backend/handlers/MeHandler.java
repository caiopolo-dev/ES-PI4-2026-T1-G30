package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.AuthSession;
import backend.models.User;
import backend.repositories.UserRepository;
import backend.router.HttpHandler;
import backend.services.BearerTokenExtractor;
import backend.services.SessionService;
import org.bson.Document;

import java.util.Objects;
import java.util.Optional;

public final class MeHandler implements HttpHandler {
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final BearerTokenExtractor tokenExtractor;

    public MeHandler(SessionService sessionService, UserRepository userRepository,
                     BearerTokenExtractor tokenExtractor) {
        this.sessionService = Objects.requireNonNull(sessionService);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.tokenExtractor = Objects.requireNonNull(tokenExtractor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Optional<String> token = tokenExtractor.extract(request);
        if (token.isEmpty()) {
            return unauthorized();
        }

        try {
            Optional<AuthSession> session = sessionService.resolve(token.get());
            if (session.isEmpty()) {
                return unauthorized();
            }

            Optional<User> user = userRepository.findById(session.get().getUserId());
            if (user.isEmpty()) {
                return unauthorized();
            }

            User authenticatedUser = user.get();
            String responseJson = new Document()
                .append("id", authenticatedUser.getId())
                .append("name", authenticatedUser.getName())
                .append("email", authenticatedUser.getEmail())
                .toJson();

            return HttpResponse.ok(responseJson);
        } catch (RuntimeException e) {
            return HttpResponse.internalServerError("Erro interno ao autenticar usuário.");
        }
    }

    private HttpResponse unauthorized() {
        return new HttpResponse(
            401,
            "Unauthorized",
            "{\"error\":\"Autenticação necessária.\"}"
        );
    }
}
