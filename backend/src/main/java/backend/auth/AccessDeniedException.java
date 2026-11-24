// Desenvolvido por Miguel Gallinucci.
package backend.auth;

// Representa uma permissao conhecida que foi negada.
public final class AccessDeniedException extends RuntimeException {
    public AccessDeniedException() {
        // O Router transforma esta falha em 403, sem expor a regra interna que negou a permissao.
        super("Acesso negado.");
    }
}
