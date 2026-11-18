package backend.handlers;
import java.util.Map;
import java.util.Objects;

import backend.repositories.UserPersistenceException;
import backend.dto.RegisterRequest;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.router.HttpHandler;
import backend.services.AuthService;
import org.bson.Document;
import backend.models.User;

public class AuthHandler implements HttpHandler  {

    private final AuthService authService;

    public AuthHandler(AuthService authService) {
        this.authService = Objects.requireNonNull(authService);
    
       
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
        if (body == null || body.isBlank()) {  
            return HttpResponse.badRequest("Corpo da requisição vazio.");
        }


        
        // documento JSON a partir do corpo da requisição
        Document json;
        try {
            json = Document.parse(body);
        } catch (org.bson.json.JsonParseException e) {
            return HttpResponse.badRequest("JSON inválido.");
        }
        // Valida os campos do JSON
        if (!(json.get("name") instanceof String) || !(json.get("email") instanceof String) || !(json.get("password") instanceof String)) {
            return HttpResponse.badRequest("Nome, e-mail e senha devem ser textos.");
        }



        // Cria o objeto RegisterRequest a partir do JSON
        String name = json.getString("name");
        String email = json.getString("email");
        String password = json.getString("password");
        RegisterRequest registerRequest = new RegisterRequest(name, email, password);
        


        // Tenta cadastrar o usuário e retorna HTTP 400 para dados inválidos, 409 para e-mail duplicado e 500 para falhas de persistência.
        User user;
        try {
            user = authService.register(registerRequest);
        }catch (UserPersistenceException e) {
            if (e.getReason() == UserPersistenceException.Reason.DUPLICATE_USER) {
                return new HttpResponse(409,"Conflict","{\"error\":\"E-mail já cadastrado.\"}");
            }
            return HttpResponse.internalServerError("Erro interno ao cadastrar usuário.");
        }catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Dados de cadastro inválidos.");
        }catch (RuntimeException e) {
           return HttpResponse.internalServerError("Erro interno ao cadastrar usuário.");
        }

        String responseJson = new Document().append("id", user.getId()).append("name", user.getName()).append("email", user.getEmail()).toJson();
        return new HttpResponse(201, "Created", responseJson);
    }
}
