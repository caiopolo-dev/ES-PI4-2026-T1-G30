// Desenvolvido por Miguel Gallinucci.
package backend.auth;

import java.util.NoSuchElementException;

// Nao informa se o recurso existe para outro titular.
public final class ResourceNotFoundException extends NoSuchElementException {
    public ResourceNotFoundException() {
        // Usei a mesma mensagem para nao revelar se um ID pertence a outro usuario.
        super("Recurso nao encontrado.");
    }
}
