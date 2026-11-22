
//Caio Ferreira Polo issue 40
package backend.repositories;

import backend.models.Medication;
import backend.models.MedicationPresentation;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;

public class MongoMedicationRepository implements MedicationRepository {
    public static final String COLLECTION_NAME = "medication_presentations";
    public static final String RENAME_SOURCE = "RENAME_2024_DCB";
    public static final String RENAME_SOURCE_REFERENCE =
            "https://bvsms.saude.gov.br/bvs/publicacoes/relacao_nacional_medicamentos_2024.pdf";

    private final MongoCollection<Document> collection;

    public MongoMedicationRepository(MongoDatabase database) {
        this(requireDatabase(database).getCollection(COLLECTION_NAME));
    }

    public MongoMedicationRepository(MongoCollection<Document> collection) {
        this.collection = Objects.requireNonNull(collection, "collection nao pode ser null");
        this.collection.createIndex(Indexes.ascending("name"));
    }

    private static MongoDatabase requireDatabase(MongoDatabase database) {
        return Objects.requireNonNull(database, "database nao pode ser null");
    }

    /** Insere somente documentos ausentes; nunca substitui ou remove dados existentes. */
    public void ensureDefaultCatalog() {
        for (Document seed : defaultCatalogDocuments()) {
            String id = seed.getString("_id");
            Document values = new Document(seed);
            values.remove("_id");
            values.append("createdAt", new Date());
            collection.updateOne(
                    Filters.eq("_id", id),
                    new Document("$setOnInsert", values),
                    new UpdateOptions().upsert(true));
        }
    }

    @Override
    public List<Medication> findAll() {
        List<Medication> medications = new ArrayList<>();
        for (Document document : collection.find().sort(Sorts.ascending("_id"))) {
            Medication medication = fromDocument(document);
            if (medication != null) {
                medications.add(medication);
            }
        }
        return Collections.unmodifiableList(medications);
    }

    @Override
    public Optional<Medication> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Document document = collection.find(Filters.eq("_id", id.trim())).first();
        return Optional.ofNullable(fromDocument(document));
    }

    private static Medication fromDocument(Document document) {
        if (document == null) {
            return null;
        }
        String id = document.getString("_id");
        String name = document.getString("name");
        if (name == null || name.isBlank()) {
            name = presentationName(document);
        }
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            return null;
        }
        return new Medication(id.trim(), name.trim());
    }

    private static String presentationName(Document document) {
        String ingredient = document.getString("activeIngredient");
        String concentration = document.getString("concentration");
        String manufacturer = document.getString("manufacturer");
        Integer units = document.getInteger("unitCount");
        String dosageForm = document.getString("dosageForm");
        if (ingredient == null || concentration == null || manufacturer == null
                || units == null || dosageForm == null) {
            return null;
        }
        return ingredient + " " + concentration + " " + manufacturer
                + " - " + units + " " + dosageForm;
    }

    static List<Document> defaultCatalogDocuments() {
        List<Document> catalog = new ArrayList<>();
        addOriginalPresentations(catalog);
        addCanonicalMedications(catalog);
        return Collections.unmodifiableList(catalog);
    }

    private static void addOriginalPresentations(List<Document> catalog) {
        catalog.add(presentation("P001", "Losartana potássica", "50 mg", "comprimido", 30, "EMS"));
        catalog.add(presentation("P002", "Losartana potássica", "50 mg", "comprimido", 30, "Medley"));
        catalog.add(presentation("P003", "Omeprazol", "20 mg", "cápsula", 28, "EMS"));
        catalog.add(presentation("P004", "Sinvastatina", "20 mg", "comprimido", 30, "EMS"));
        catalog.add(presentation("P005", "Besilato de anlodipino", "5 mg", "comprimido", 30, "Neo Química"));
        catalog.add(presentation("P006", "Atenolol", "25 mg", "comprimido", 30, "EMS"));
        catalog.add(presentation("P007", "Paracetamol", "750 mg", "comprimido", 20, "Neo Química"));
        catalog.add(presentation("P008", "Cloridrato de metformina", "850 mg", "comprimido", 30, "EMS"));
        catalog.add(presentation("P009", "Loratadina", "10 mg", "comprimido", 12, "Neo Química"));
        catalog.add(presentation("P010", "Dipirona monoidratada", "500 mg", "comprimido", 10, "Prati Donaduzzi"));
    }

    private static Document presentation(
            String id,
            String ingredient,
            String concentration,
            String dosageForm,
            int units,
            String manufacturer) {
        MedicationPresentation presentation = new MedicationPresentation(
                id,
                ingredient,
                concentration,
                dosageForm,
                units,
                "caixa",
                manufacturer,
                Collections.<String, String>emptyMap());
        return new Document("_id", presentation.getId())
                .append("name", presentation.getDisplayName())
                .append("catalogEntryType", "PRESENTATION")
                .append("activeIngredient", presentation.getActiveIngredient())
                .append("concentration", presentation.getConcentration())
                .append("dosageForm", presentation.getDosageForm())
                .append("unitCount", presentation.getUnitCount())
                .append("packageType", presentation.getPackageType())
                .append("manufacturer", presentation.getManufacturer())
                .append("regulatoryIdentifiers", new Document())
                .append("catalogSource", "ISSUE_6_EXPERIMENTAL_SAMPLE");
    }

    private static void addCanonicalMedications(List<Document> catalog) {
        String[] names = new String[] {
            "Aciclovir",
            "Ácido acetilsalicílico",
            "Ácido fólico",
            "Albendazol",
            "Alopurinol",
            "Amoxicilina",
            "Ampicilina",
            "Azitromicina",
            "Dipropionato de beclometasona",
            "Benzilpenicilina benzatina",
            "Budesonida",
            "Captopril",
            "Carbamazepina",
            "Carbonato de lítio",
            "Cefalexina",
            "Ceftriaxona",
            "Cloridrato de ciprofloxacino",
            "Claritromicina",
            "Clindamicina",
            "Clonazepam",
            "Cloreto de sódio",
            "Dexametasona",
            "Diazepam",
            "Doxiciclina",
            "Maleato de enalapril",
            "Espironolactona",
            "Fenitoína",
            "Fenobarbital",
            "Fluconazol",
            "Cloridrato de fluoxetina",
            "Furosemida",
            "Glibenclamida",
            "Hidroclorotiazida",
            "Ibuprofeno",
            "Insulina humana NPH",
            "Brometo de ipratrópio",
            "Isoniazida",
            "Itraconazol",
            "Mebendazol",
            "Levotiroxina sódica",
            "Metildopa",
            "Cloridrato de metoclopramida",
            "Metronidazol",
            "Nistatina",
            "Nitrofurantoína",
            "Prednisona",
            "Cloridrato de propranolol",
            "Sulfato de salbutamol",
            "Sulfametoxazol + trimetoprima",
            "Varfarina sódica"
        };
        for (int index = 0; index < names.length; index++) {
            String id = String.format("P%03d", index + 11);
            catalog.add(new Document("_id", id)
                    .append("name", names[index])
                    .append("catalogEntryType", "CANONICAL")
                    .append("catalogSource", RENAME_SOURCE)
                    .append("sourceReference", RENAME_SOURCE_REFERENCE));
        }
    }
}
