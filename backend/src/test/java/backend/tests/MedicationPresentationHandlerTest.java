package backend.tests;

import backend.handlers.MedicationPresentationHandler;
import backend.http.HttpRequest;
import backend.models.MedicationPresentation;
import backend.services.MedicationPresentationService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicationPresentationHandlerTest {

    @Test
    void returnsExperimentalCatalogAndCompletePresentation() {
        String[] receivedQuery = {null};
        int[] receivedLimit = {0};
        MedicationPresentationService service = new MedicationPresentationService((query, limit) -> {
            receivedQuery[0] = query;
            receivedLimit[0] = limit;
            return List.of(new MedicationPresentation(
                    "presentation-1",
                    "Losartana potássica",
                    "Losartana potássica",
                    "50 mg",
                    "comprimido revestido",
                    "Fabricante experimental",
                    "caixa",
                    30
            ));
        });

        var response = new MedicationPresentationHandler(service)
                .handle(new HttpRequest("GET", "/medication-presentations?q=losartana+potassica"));

        assertEquals(200, response.getStatusCode());
        assertEquals("losartana potassica", receivedQuery[0]);
        assertEquals(20, receivedLimit[0]);
        assertTrue(response.getBody().contains("\"catalog\": \"EXPERIMENTAL\""));
        assertTrue(response.getBody().contains("\"dosageForm\": \"comprimido revestido\""));
        assertTrue(response.getBody().contains("\"unitCount\": 30"));
        assertFalse(response.getBody().contains("quantityPerAdministration"));
    }

    @Test
    void rejectsMissingShortRepeatedOrUnknownQueriesAndBodies() {
        MedicationPresentationHandler handler = new MedicationPresentationHandler(
                new MedicationPresentationService((query, limit) -> List.of()));

        assertEquals(400, handler.handle(new HttpRequest("GET", "/medication-presentations")).getStatusCode());
        assertEquals(400, handler.handle(new HttpRequest("GET", "/medication-presentations?q=a")).getStatusCode());
        assertEquals(400, handler.handle(new HttpRequest("GET", "/medication-presentations?q=abc&q=def")).getStatusCode());
        assertEquals(400, handler.handle(new HttpRequest("GET", "/medication-presentations?name=abc")).getStatusCode());
        assertEquals(400, handler.handle(new HttpRequest(
                "GET", "/medication-presentations?q=abc", "HTTP/1.1", java.util.Map.of(), "{}"
        )).getStatusCode());
    }
}
