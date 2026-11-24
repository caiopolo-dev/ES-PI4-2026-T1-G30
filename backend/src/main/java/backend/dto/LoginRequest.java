// Desenvolvido por Caio Ferreira Polo.
package backend.dto;

/**
 * Reúne o e-mail e a senha recebidos em uma tentativa de login.
 * Esse objeto transporta as credenciais do handler até o service,
 * onde os dados são validados e comparados com o usuário armazenado.
 */
public class LoginRequest {
    // O e-mail identifica a conta e a senha comprova a identidade do usuário.
    private final String email;
    private final String password;

    // Apenas guardo os valores recebidos; as regras de validação ficam no LoginService.
    public LoginRequest(String email, String password){
        this.email = email;
        this.password = password;

    }
    
    public String getEmail(){
        return this.email;
    }

    // O getter permite que o service consulte a senha sem expor outra forma de alterá-la.
    public String getPassword(){
        return this.password;
    }
}
