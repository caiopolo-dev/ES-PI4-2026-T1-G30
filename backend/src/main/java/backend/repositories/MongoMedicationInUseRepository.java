package backend.repositories;

import backend.models.MedicamentoEmUso;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;

/** Usa a mesma collection treatments, sem colecao ou cache paralelo. */
public final class MongoMedicationInUseRepository implements MedicationInUseRepository {
    private final MongoCollection<Document> treatments;

    public MongoMedicationInUseRepository(MongoCollection<Document> treatments) {
        this.treatments = Objects.requireNonNull(treatments);
    }

    @Override
    public Optional<MedicamentoEmUso> find(String treatmentId, String userId, String medicationId) {
        Document treatment = treatments.find(ownedMedication(treatmentId, userId, medicationId)).first();
        if (treatment == null) { return Optional.empty(); }
        for (Document medication : treatment.getList("medications", Document.class)) {
            if (medicationId.equals(medication.getString("id"))) {
                return Optional.of(fromDocument(medication));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean add(String treatmentId, String userId, MedicamentoEmUso medication) {
        return addInState(treatmentId, userId, medication, null);
    }

    @Override
    public boolean addInState(String treatmentId, String userId, MedicamentoEmUso medication, String status) {
        Objects.requireNonNull(medication);
        // Um novo medicamento nao recebe cronogramas por esta operacao.
        if (!medication.getSchedules().isEmpty()) {
            throw new IllegalArgumentException("Cronogramas devem ser configurados pelo fluxo proprio.");
        }
        Document document = editableFields(medication).append("id", medication.getId())
            .append("active", medication.isActive()).append("schedules", List.of());
        Bson filter = Filters.and(inState(owner(treatmentId, userId), status), Filters.ne("medications.id", medication.getId()));
        return treatments.updateOne(filter, Updates.push("medications", document)).getMatchedCount() == 1;
    }

    @Override
    public boolean update(String treatmentId, String userId, MedicamentoEmUso medication) {
        return updateInState(treatmentId, userId, medication, null);
    }

    @Override
    public boolean updateInState(String treatmentId, String userId, MedicamentoEmUso medication, String status) {
        Objects.requireNonNull(medication);
        Document fields = editableFields(medication);
        Document changes = new Document();
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            changes.append("medications.$." + field.getKey(), field.getValue());
        }
        // Nao substitui o array, ID, estado ativo ou versoes dos cronogramas.
        return treatments.updateOne(inState(ownedMedication(treatmentId, userId, medication.getId()), status),
            new Document("$set", changes)).getMatchedCount() == 1;
    }

    @Override
    public Optional<String> findTreatmentStatus(String treatmentId, String userId) {
        Document treatment = treatments.find(owner(treatmentId, userId)).first();
        if (treatment == null) { return Optional.empty(); }
        Object status = treatment.get("status");
        return Optional.of(status instanceof String value ? value : "UNDEFINED");
    }

    @Override
    public boolean deactivateInState(String treatmentId, String userId, String medicationId, String status) {
        Bson filter = inState(ownedMedication(treatmentId, userId, medicationId), status);
        if ("ACTIVE".equals(status)) {
            // Confere a quantidade de ativos na mesma atualizacao atomica.
            Document activeCount = new Document("$size", new Document("$filter",
                new Document("input", "$medications").append("as", "med")
                    .append("cond", new Document("$ne", List.of("$$med.active", false)))));
            filter = Filters.and(filter, new Document("$expr", new Document("$gt", List.of(activeCount, 1))));
        }
        return treatments.updateOne(filter, Updates.set("medications.$.active", false)).getMatchedCount() == 1;
    }

    private Bson inState(Bson filter, String status) {
        return status == null ? filter : Filters.and(filter, Filters.eq("status", status));
    }

    private Bson owner(String treatmentId, String userId) {
        if (treatmentId == null || treatmentId.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Tratamento e titular obrigatorios.");
        }
        return Filters.and(Filters.eq("_id", treatmentId), Filters.eq("userId", userId));
    }

    private Bson ownedMedication(String treatmentId, String userId, String medicationId) {
        if (medicationId == null || medicationId.isBlank()) {
            throw new IllegalArgumentException("Medicamento obrigatorio.");
        }
        return Filters.and(owner(treatmentId, userId), Filters.eq("medications.id", medicationId));
    }

    private Document editableFields(MedicamentoEmUso medication) {
        BigDecimal quantity = medication.getQuantity();
        // A conversao exata rejeita valores fora da representacao Decimal128.
        Decimal128 storedQuantity = quantity == null ? null : new Decimal128(quantity);
        return new Document("name", medication.getName()).append("dose", medication.getDose())
            .append("quantity", storedQuantity).append("unit", medication.getUnit())
            .append("observations", medication.getObservations());
    }

    private MedicamentoEmUso fromDocument(Document document) {
        Object quantity = document.get("quantity");
        BigDecimal decimal;
        if (quantity == null) { decimal = null; }
        else if (quantity instanceof Decimal128 value) { decimal = value.bigDecimalValue(); }
        else if (quantity instanceof Integer || quantity instanceof Long || quantity instanceof BigDecimal) {
            decimal = new BigDecimal(quantity.toString());
        } else { throw new IllegalArgumentException("Quantidade armazenada invalida."); }
        // Reutiliza o mapeamento de cronogramas ja existente no projeto.
        Document copy = new Document(document).append("quantity", decimal);
        var treatment = new MongoTreatmentRepository().fromDocument(new Document("_id", "mapping")
            .append("userId", "mapping").append("name", "mapping").append("medications", List.of(copy)));
        return treatment.getMedications().get(0);
    }
}
