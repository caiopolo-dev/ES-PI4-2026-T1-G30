package backend.auth;

import backend.http.HttpRequest;
import java.util.Optional;

/**
 * Contrato para extrair e validar a identidade do usuário autenticado a partir da requisição HTTP.
 * Garante que o titular da consulta venha exclusivamente da sessão autenticada.
 */
@FunctionalInterface
public interface SessionAuthenticator {
    Optional<String> resolveAuthenticatedUserId(HttpRequest request);
}
