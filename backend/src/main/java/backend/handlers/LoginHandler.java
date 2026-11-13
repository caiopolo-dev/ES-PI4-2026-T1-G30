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


public class LoginHandler implements HttpHandler {
    private final LoginService loginService;

    public LoginHandler(LoginService loginService) {
        this.loginService = Objects.requireNonNull(loginService);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        
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

        //pega o corpo da requisição
        String body = request.getBody();
        if(body == null || body.isBlank()){
            return HttpResponse.badRequest("Corpo da requisição vazio.");
        }

        Document json;
        try{
            json = Document.parse(body);
        }catch(org.bson.json.JsonParseException e){
            return HttpResponse.badRequest("Json inválido");
        }

        if(!(json.get("email") instanceof String) || !(json.get("password")instanceof String)){
            return HttpResponse.badRequest("e-mail ou senha devem ser textos.");
        }

        // Cria objeto LoginRequest a partir do JSON
        String email = json.getString("email");
        String password = json.getString("password");
        LoginRequest loginRequest = new LoginRequest(email, password);

        User user;
        try {
            user = loginService.login(loginRequest);

        } catch (InvalidCredentialsException e) {
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


        String responseJson = new Document()
            .append("id", user.getId())
            .append("name", user.getName())
            .append("email", user.getEmail())
            .toJson();

        return HttpResponse.ok(responseJson);
    }
}
