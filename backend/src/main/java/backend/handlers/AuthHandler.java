// Desenvolvido por Caio Ferreira Polo.
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

/**
 * Recebe a requisição HTTP de cadastro e devolve a resposta adequada.
 * O handler valida o formato da entrada, enquanto o AuthService aplica
 * as regras de cadastro e utiliza o repository para persistir o usuário.
 */
public class AuthHandler implements HttpHandler  {

    private final AuthService authService;

    public AuthHandler(AuthService authService) {
        this.authService = Objects.requireNonNull(authService);
    
       
    }


    @Override
    public HttpResponse handle(HttpRequest request) {

        // Confiro o Content-Type antes de interpretar o corpo, pois este endpoint
        // aceita somente os dados de cadastro enviados como JSON.
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

        // Um corpo ausente não contém os dados mínimos para tentar o cadastro.
        String body = request.getBody();     
        if (body == null || body.isBlank()) {  
            return HttpResponse.badRequest("Corpo da requisição vazio.");
        }


        
        // Document.parse transforma o texto JSON em um objeto que permite
        // consultar cada campo pelo nome.
        Document json;
        try {
            json = Document.parse(body);
        } catch (org.bson.json.JsonParseException e) {
            return HttpResponse.badRequest("JSON inválido.");
        }
        // Verifico também os tipos para não aceitar números, listas ou objetos
        // onde o fluxo de cadastro espera textos.
        if (!(json.get("name") instanceof String) || !(json.get("email") instanceof String) || !(json.get("password") instanceof String)) {
            return HttpResponse.badRequest("Nome, e-mail e senha devem ser textos.");
        }



        // O DTO leva os dados ao service sem acoplar a regra de negócio ao JSON.
        String name = json.getString("name");
        String email = json.getString("email");
        String password = json.getString("password");
        RegisterRequest registerRequest = new RegisterRequest(name, email, password);
        


        // Cada tipo de falha é convertido em um status HTTP apropriado:
        // dados inválidos geram 400, e-mail duplicado gera 409 e falhas internas geram 500.
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

        // A resposta expõe somente os dados públicos da conta; hash e salt da senha
        // permanecem no servidor e nunca são enviados ao cliente.
        String responseJson = new Document().append("id", user.getId()).append("name", user.getName()).append("email", user.getEmail()).toJson();
        return new HttpResponse(201, "Created", responseJson);
    }
}
