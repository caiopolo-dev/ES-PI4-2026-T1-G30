package backend.auth;

import java.util.NoSuchElementException;

// Nao informa se o recurso existe para outro titular.
public final class ResourceNotFoundException extends NoSuchElementException {
    public ResourceNotFoundException() {
        super("Recurso nao encontrado.");
    }
}
