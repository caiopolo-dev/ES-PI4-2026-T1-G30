package backend.src.handlers;

import backend.src.http.HttpRequest;
import backend.src.http.HttpResponse;
import backend.src.models.Medication;
import backend.src.router.HttpHandler;
import backend.src.services.MedicationService;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Handler responsável pelo endpoint GET /medications.
 * Delega a busca ao serviço e converte os medicamentos em resposta HTTP.
 */
public class MedicationHandler implements HttpHandler {

    private final MedicationService medicationService;

    public MedicationHandler(MedicationService medicationService) {
        this.medicationService = Objects.requireNonNull(
            medicationService, "medicationService não pode ser null"
        );
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        StringJoiner medicationsJson = new StringJoiner(",", "[", "]");
        for (Medication medication : medicationService.listMedications()) {
            medicationsJson.add(
                "{\"id\":" + toJsonString(medication.getId())
                + ",\"name\":" + toJsonString(medication.getName()) + "}"
            );
        }
        return HttpResponse.ok(medicationsJson.toString());
    }

    private static String toJsonString(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                default:
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
            }
        }
        return json.append('"').toString();
    }
}

