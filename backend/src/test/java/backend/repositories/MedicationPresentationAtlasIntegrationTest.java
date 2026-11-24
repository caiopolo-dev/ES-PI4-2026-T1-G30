package backend.repositories;

import backend.config.MongoConnection;
import backend.models.MedicationPresentation;
import backend.services.MedicationPresentationService;
import com.mongodb.client.model.Filters;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MedicationPresentationAtlasIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "MONGODB_DATABASE", matches = ".+")
    void queriesAStoredPresentationUsingTheJavaRepository() {
        try (MongoConnection connection = new MongoConnection()) {
            Document source = connection.getCollection("medication_presentations")
                    .find(Filters.ne("catalogEntryType", "CANONICAL"))
                    .first();
            assertNotNull(source, "O catálogo experimental deve conter apresentações.");

            String query = searchText(source);
            List<MedicationPresentation> presentations = new MedicationPresentationService(
                    new MongoMedicationPresentationRepository(
                            connection.getCollection("medication_presentations")))
                    .search(query);

            assertFalse(presentations.isEmpty());
            assertNotNull(presentations.getFirst().getName());
        }
    }

    private String searchText(Document document) {
        for (String field : List.of("name", "productName", "brandName", "activeIngredient")) {
            Object value = document.get(field);
            if (value instanceof String text && text.trim().length() >= 2) {
                String normalized = text.trim();
                return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
            }
        }
        throw new IllegalStateException("Apresentação sem nome ou princípio ativo pesquisável.");
    }
}
