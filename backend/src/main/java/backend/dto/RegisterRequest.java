// Desenvolvido por Caio Ferreira Polo.
package backend.dto;
import backend.dto.RegisterRequest;

/**
 * Transporta os dados recebidos no pedido de cadastro.
 * As validações ficam no service, para manter as regras de negócio
 * separadas da leitura da requisição HTTP.
 */
public class RegisterRequest {

    private final String name;
    private final String email;
    private final String password;

    // O objeto apenas reúne os valores enviados pelo cliente.
    // AuthService decide depois se eles são válidos para criar a conta.
    public RegisterRequest(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

}
