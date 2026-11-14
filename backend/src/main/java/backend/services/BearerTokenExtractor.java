package backend.services;

import backend.http.HttpRequest;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Extrai uma única credencial Bearer no formato emitido pelo SessionService. */
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

        if (authorizationHeaders != 1 || authorization == null) {
            return Optional.empty();
        }

        int separator = authorization.indexOf(' ');
        if (separator <= 0) {
            return Optional.empty();
        }

        String scheme = authorization.substring(0, separator);
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
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }

        return Optional.of(token);
    }
}
