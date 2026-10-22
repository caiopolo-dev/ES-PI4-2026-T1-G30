package backend.src.handlers;

import backend.src.http.HttpRequest;
import backend.src.http.HttpResponse;
import backend.src.router.HttpHandler;

/**
 * Handler responsável pelo endpoint GET /medications.
 * Atua como fronteira HTTP e está preparado para delegar a busca ao MedicationService.
 */
public class MedicationHandler implements HttpHandler {

    // Quando o MedicationService for integrado (tarefa #11), basta injetá-lo no construtor:
    // private final MedicationService medicationService;

    @Override
    public HttpResponse handle(HttpRequest request) {
        // Resposta inicial preparada para ser populada pelo MedicationService
        String jsonBody = "[{\"id\":\"1\",\"name\":\"Losartana 50 mg\"},{\"id\":\"2\",\"name\":\"Sinvastatina 20 mg\"}]";
        return HttpResponse.ok(jsonBody);
    }
}

