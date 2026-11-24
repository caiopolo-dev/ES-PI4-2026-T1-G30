// Desenvolvido por Caio Ferreira Polo.
package backend.handlers;
import java.util.Map;
import backend.dto.LoginRequest;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.router.HttpHandler;
import backend.services.LoginService;
import backend.services.InvalidCredentialsException;
import backend.repositories.UserPersistenceException;
import java.util.Objects;

import org.bson.Document;
import backend.models.User;

import backend.services.SessionService;
import backend.services.LoginRateLimiter;


/**
 * Recebe a requisição HTTP de login, encaminha as credenciais ao LoginService
 * e cria uma sessão quando a autenticação é concluída com sucesso.
 */
public class LoginHandler implements HttpHandler {
    private final LoginService loginService;
    private final SessionService sessionService;
    private final LoginRateLimiter rateLimiter;

    public LoginHandler(LoginService loginService, SessionService sessionService) {
        this(loginService, sessionService, new LoginRateLimiter());
    }

    public LoginHandler(LoginService loginService, SessionService sessionService,
                        LoginRateLimiter rateLimiter) {
        this.loginService = Objects.requireNonNull(loginService);
        this.sessionService = Objects.requireNonNull(sessionService);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
    }   

    @Override
    public HttpResponse handle(HttpRequest request) {
        
        // Antes de ler o corpo, confirmo que o cliente enviou os dados como JSON.
        Map<String, String> headers = request.getHeaders();
        String contentType = headers.entrySet()
            .stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase("Content-Type"))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);

        if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            return HttpResponse.badRequest("Content-Type inválido. Esperado: application/json");
        }

        // Sem corpo não existem credenciais que possam ser verificadas.
        String body = request.getBody();
        if(body == null || body.isBlank()){
            return HttpResponse.badRequest("Corpo da requisição vazio.");
        }

        Document json;
        try{
            // Transformo o texto recebido em um documento para acessar os campos do login.
            json = Document.parse(body);
        }catch(org.bson.json.JsonParseException e){
            return HttpResponse.badRequest("Json inválido");
        }

        if(!(json.get("email") instanceof String) || !(json.get("password")instanceof String)){
            return HttpResponse.badRequest("e-mail ou senha devem ser textos.");
        }

        // O DTO leva as credenciais ao service sem deixar a regra de autenticação no handler.
        String email = json.getString("email");
        String password = json.getString("password");

        if (!rateLimiter.allow(email)) {
            // O limite de tentativas dificulta ataques que testam muitas senhas em sequência.
            return new HttpResponse(
                429,
                "Too Many Requests",
                "{\"error\":\"Muitas tentativas de login. Tente novamente mais tarde.\"}"
            );
        }

        LoginRequest loginRequest = new LoginRequest(email, password);

        User user;
        String token;

        try {
            // O service verifica as credenciais. Somente depois disso uma sessão é criada
            // e associada ao identificador do usuário autenticado.
            user = loginService.login(loginRequest);
            token = sessionService.create(user.getId());

        } catch (InvalidCredentialsException e) {
            // A resposta não informa se o erro estava no e-mail ou na senha.
            return new HttpResponse(401,"Unauthorized","{\"error\":\"Credenciais inválidas.\"}");

        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Dados de login inválidos.");

        } catch (UserPersistenceException e) {
            return HttpResponse.internalServerError(
                "Erro interno ao realizar login."
            );

        } catch (RuntimeException e) {
            return HttpResponse.internalServerError(
                "Erro interno ao realizar login."
            );
        }


       // A resposta reúne os dados públicos do usuário e o token usado nas próximas requisições.
       String responseJson = new Document()
            .append("id", user.getId())
            .append("name", user.getName())
            .append("email", user.getEmail())
            .append("token", token)
            .toJson();

        return HttpResponse.ok(responseJson);
    }
}
