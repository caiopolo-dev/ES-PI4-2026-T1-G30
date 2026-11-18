package backend.auth;

// Representa uma permissao conhecida que foi negada.
public final class AccessDeniedException extends RuntimeException {
    public AccessDeniedException() {
        super("Acesso negado.");
    }
}
