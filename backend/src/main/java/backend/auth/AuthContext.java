package backend.auth;

// Identidade validada pelo servidor, sem guardar o token.
public record AuthContext(String userId) {
    public AuthContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Identidade obrigatoria.");
        }
    }
}
