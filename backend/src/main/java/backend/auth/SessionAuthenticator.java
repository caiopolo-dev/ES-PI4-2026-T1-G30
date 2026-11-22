// Desenvolvido por Miguel Gallinucci.
package backend.auth;

import backend.http.HttpRequest;
import java.util.Optional;

/**
 * Contrato para extrair e validar a identidade do usuário autenticado a partir da requisição HTTP.
 * Garante que o titular da consulta venha exclusivamente da sessão autenticada.
 */
@FunctionalInterface
// Interface funcional tem um unico metodo abstrato; permite fornecer a validacao como uma lambda na Main.
public interface SessionAuthenticator {
    // Separei o contrato da implementacao para o Router usar a sessao existente sem cuidar do login.
    // Optional vazio significa que a requisicao nao forneceu uma identidade autenticada valida.
    Optional<String> resolveAuthenticatedUserId(HttpRequest request);
}
