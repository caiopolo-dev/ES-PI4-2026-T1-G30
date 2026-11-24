// Desenvolvido por Caio Ferreira Polo.
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

/**
 * Identifica o usuário da sessão atual e devolve seus dados públicos.
 * O handler usa o token da requisição em vez de confiar em um ID informado
 * pelo cliente, garantindo que a consulta corresponda ao usuário autenticado.
 */
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
        // Primeiro extraio o Bearer Token enviado no cabeçalho de autorização.
        Optional<String> token = tokenExtractor.extract(request);
        if (token.isEmpty()) {
            return unauthorized();
        }

        try {
            // O service verifica se a sessão existe, não expirou e não foi revogada.
            Optional<AuthSession> session = sessionService.resolve(token.get());
            if (session.isEmpty()) {
                return unauthorized();
            }

            // O ID vem da sessão validada, ligando a consulta ao titular do token.
            Optional<User> user = userRepository.findById(session.get().getUserId());
            if (user.isEmpty()) {
                return unauthorized();
            }

            // A resposta contém somente os dados públicos necessários para identificar a conta.
            User authenticatedUser = user.get();
            String responseJson = new Document()
                .append("id", authenticatedUser.getId())
                .append("name", authenticatedUser.getName())
                .append("email", authenticatedUser.getEmail())
                .toJson();

            return HttpResponse.ok(responseJson);
        } catch (RuntimeException e) {
            // Uma falha interna não deve expor detalhes da sessão ou do banco ao cliente.
            return HttpResponse.internalServerError("Erro interno ao autenticar usuário.");
        }
    }

    // Centralizo a resposta para manter o mesmo resultado quando o token,
    // a sessão ou o usuário não puder ser identificado.
    private HttpResponse unauthorized() {
        return new HttpResponse(
            401,
            "Unauthorized",
            "{\"error\":\"Autenticação necessária.\"}"
        );
    }
}
