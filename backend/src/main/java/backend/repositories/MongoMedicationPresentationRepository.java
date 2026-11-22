package backend.repositories;

import backend.models.MedicationPresentation;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;

/**
 * Consulta somente leitura da collection medication_presentations.
 */
public final class MongoMedicationPresentationRepository
        implements MedicationPresentationRepository {

    private final MongoCollection<Document> collection;

    public MongoMedicationPresentationRepository(MongoCollection<Document> collection) {
        this.collection = Objects.requireNonNull(collection);
    }

    @Override
    public List<MedicationPresentation> search(String query, int limit) {
        List<Document> documents = collection.find(searchFilter(query))
                .sort(Sorts.ascending("activeIngredient", "concentration", "manufacturer"))
                .limit(limit)
                .into(new ArrayList<>());

        List<MedicationPresentation> presentations = new ArrayList<>();
        for (Document document : documents) {
            MedicationPresentation presentation = fromDocument(document);
            if (presentation != null) {
                presentations.add(presentation);
            }
        }
        return List.copyOf(presentations);
    }

    static Bson searchFilter(String query) {
        String expression = Pattern.quote(query);
        return Filters.and(
                Filters.ne("catalogEntryType", "CANONICAL"),
                Filters.or(
                        Filters.regex("name", expression, "i"),
                        Filters.regex("productName", expression, "i"),
                        Filters.regex("brandName", expression, "i"),
                        Filters.regex("activeIngredient", expression, "i")
                ));
    }

    static MedicationPresentation fromDocument(Document document) {
        Objects.requireNonNull(document);

        String activeIngredient = text(document, "activeIngredient");
        String name = firstText(document, "name", "productName", "brandName");
        if (name == null) {
            name = activeIngredient;
        }
        if (activeIngredient == null) {
            activeIngredient = name;
        }
        if (name == null) {
            return null;
        }

        Object rawId = document.get("_id");
        String id = rawId instanceof org.bson.types.ObjectId objectId
                ? objectId.toHexString()
                : rawId == null ? null : rawId.toString();

        return new MedicationPresentation(
                id,
                name,
                activeIngredient,
                text(document, "concentration"),
                text(document, "dosageForm"),
                text(document, "manufacturer"),
                text(document, "packageType"),
                integer(document.get("unitCount"))
        );
    }

    private static String firstText(Document document, String... fields) {
        for (String field : fields) {
            String value = text(document, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(Document document, String field) {
        Object value = document.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private static Integer integer(Object value) {
        try {
            Integer result = null;
            if (value instanceof Decimal128 decimal) {
                result = decimal.bigDecimalValue().intValueExact();
            }
            if (value instanceof Number number) {
                result = java.math.BigDecimal.valueOf(number.doubleValue()).intValueExact();
            }
            return result != null && result > 0 ? result : null;
        } catch (ArithmeticException ignored) {
            // Valores não inteiros não são convertidos silenciosamente.
        }
        return null;
    }
}
