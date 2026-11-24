// Autor: Samuel Campovilla
package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.MedicationPresentation;
import backend.router.HttpHandler;
import backend.services.MedicationPresentationService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import org.bson.Document;


// Handler responsável por GET /medication-presentations?q=texto.

public final class MedicationPresentationHandler implements HttpHandler {
    private final MedicationPresentationService service;

    public MedicationPresentationHandler(MedicationPresentationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (!request.getBody().isBlank()) {
            return HttpResponse.badRequest("Corpo não suportado.");
        }

        try {
            String query = query(request.getRawQuery());
            ArrayList<Document> items = new ArrayList<>();
            for (MedicationPresentation presentation : service.search(query)) {
                items.add(new Document("id", presentation.getId())
                        .append("name", presentation.getName())
                        .append("activeIngredient", presentation.getActiveIngredient())
                        .append("concentration", presentation.getConcentration())
                        .append("dosageForm", presentation.getDosageForm())
                        .append("manufacturer", presentation.getManufacturer())
                        .append("packageType", presentation.getPackageType())
                        .append("unitCount", presentation.getUnitCount()));
            }

            return HttpResponse.ok(new Document("catalog", "EXPERIMENTAL")
                    .append("items", items)
                    .toJson());
        } catch (IllegalArgumentException error) {
            return HttpResponse.badRequest("Busca de apresentação inválida.");
        } catch (RuntimeException error) {
            return HttpResponse.internalServerError("Erro ao consultar apresentações.");
        }
    }

    private String query(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IllegalArgumentException("Busca ausente.");
        }

        String value = null;
        for (String pair : rawQuery.split("&", -1)) {
            String[] parts = pair.split("=", -1);
            if (parts.length != 2 || !"q".equals(parts[0]) || value != null) {
                throw new IllegalArgumentException("Query inválida.");
            }
            value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
        return value;
    }
}
