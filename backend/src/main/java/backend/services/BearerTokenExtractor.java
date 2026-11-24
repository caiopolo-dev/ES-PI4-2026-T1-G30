// Desenvolvido por Caio Ferreira Polo.
package backend.services;

import backend.http.HttpRequest;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Extrai o Bearer Token enviado no cabeçalho de autorização da requisição.
 * O token encontrado pode ser encaminhado ao SessionService para identificar
 * a sessão e, por consequência, o usuário autenticado.
 */
public final class BearerTokenExtractor {

    public Optional<String> extract(HttpRequest request) {
        Objects.requireNonNull(request);

        String authorization = null;
        int authorizationHeaders = 0;

        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            if ("Authorization".equalsIgnoreCase(header.getKey())) {
                authorizationHeaders++;
                authorization = header.getValue();
            }
        }

        // Exijo exatamente um cabeçalho Authorization para evitar uma autenticação ambígua.
        if (authorizationHeaders != 1 || authorization == null) {
            return Optional.empty();
        }

        // A credencial deve possuir o esquema e o token separados por espaço.
        int separator = authorization.indexOf(' ');
        if (separator <= 0) {
            return Optional.empty();
        }

        String scheme = authorization.substring(0, separator);
        // Somente o esquema Bearer é aceito neste fluxo de autenticação.
        if (!scheme.equalsIgnoreCase("Bearer")) {
            return Optional.empty();
        }

        int tokenStart = separator;
        while (tokenStart < authorization.length()
                && authorization.charAt(tokenStart) == ' ') {
            tokenStart++;
        }

        if (tokenStart == authorization.length()) {
            return Optional.empty();
        }

        String token = authorization.substring(tokenStart);
        // O formato precisa ser igual ao token de 32 bytes codificado pelo SessionService.
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }

        return Optional.of(token);
    }
}
