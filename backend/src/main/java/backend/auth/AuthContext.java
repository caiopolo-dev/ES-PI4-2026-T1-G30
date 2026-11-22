// Desenvolvido por Miguel Gallinucci.
package backend.auth;

// Identidade validada pelo servidor, sem guardar o token.
public record AuthContext(String userId) {
    // Contexto autenticado e a identidade ja confirmada pelo servidor; usei para conferir o dono dos dados.
    // Guardei apenas o ID confirmado pela sessao para os servicos saberem quem esta fazendo a operacao.
    public AuthContext {
        // Rejeitei identidade vazia para os servicos nunca receberem um contexto sem titular.
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Identidade obrigatoria.");
        }
    }
}
