// Desenvolvido por Caio Ferreira Polo.
package backend.services;

/**
 * Indica que as credenciais recebidas não permitem autenticar o usuário.
 * A mensagem não revela se o problema estava no e-mail ou na senha,
 * evitando confirmar quais contas existem no sistema.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciais inválidas.");
    }
}
