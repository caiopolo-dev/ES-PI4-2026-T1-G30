package backend.repositories;

import backend.models.MedicamentoEmUso;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.conversions.Bson;

/** Atualiza somente o medicamento selecionado na mesma collection treatments. */
public final class MongoMedicationInUseRepository implements MedicationInUseRepository {
    private final MongoCollection<Document> treatments;

    public MongoMedicationInUseRepository(MongoCollection<Document> treatments) {
        this.treatments = Objects.requireNonNull(treatments);
    }

    @Override
    public boolean add(String treatmentId, String userId, MedicamentoEmUso medication) {
        Objects.requireNonNull(medication);
        if (!medication.getSchedules().isEmpty()) {
            throw new IllegalArgumentException("Use o fluxo de cronogramas para configurar horarios.");
        }
        Document document = fields(medication).append("id", medication.getId())
            .append("active", medication.isActive()).append("revision", 0L).append("schedules", List.of());
        if (medication.getRevision() != 0) { throw new IllegalArgumentException("Novo medicamento deve iniciar na revisao zero."); }
        Bson filter = Filters.and(owner(treatmentId, userId), Filters.ne("medications.id", medication.getId()));
        return treatments.updateOne(filter, Updates.push("medications", document)).getMatchedCount() == 1;
    }

    @Override
    public Optional<MedicamentoEmUso> find(
            String treatmentId,
            String userId,
            String medicationId) {

        Document treatment = treatments.find(ownedMedication(treatmentId, userId, medicationId)).first();

        if (treatment == null) {
            return Optional.empty();
        }

        // Utiliza a mesma collection MongoDB para reconstruir
        // o tratamento e localizar seu medicamento.
        MongoTreatmentRepository repository = new MongoTreatmentRepository(treatments);

        return repository.fromDocument(treatment).findMedicationById(medicationId);
    }

    @Override
    public boolean update(String treatmentId, String userId, MedicamentoEmUso medication) {
        Objects.requireNonNull(medication);
        Document changes = new Document();
        for (var field : fields(medication).entrySet()) {
            changes.append("medications.$." + field.getKey(), field.getValue());
        }
        // Preserva ID, estado e todos os cronogramas armazenados.
        Bson current = Filters.and(Filters.eq("id", medication.getId()), Filters.ne("active", false),
            revisionFilter(medication.getRevision()));
        Bson filter = Filters.and(owner(treatmentId, userId), Filters.elemMatch("medications", current));
        return treatments.updateOne(filter, new Document("$set", changes)
            .append("$inc", new Document("medications.$.revision", 1L))).getMatchedCount() == 1;
    }

    @Override
    public boolean deactivate(String treatmentId, String userId, String medicationId, long revision, int minimumRemainingActive) {
        if (minimumRemainingActive < 0 || minimumRemainingActive > 1) {
            throw new IllegalArgumentException("Minimo de medicamentos invalidos.");
        }
        ownedMedication(treatmentId, userId, medicationId);
        Bson current = Filters.and(Filters.eq("id", medicationId), Filters.ne("active", false), revisionFilter(revision));
        Document activeCount = new Document("$size", new Document("$filter",
            new Document("input", "$medications").append("as", "med")
                .append("cond", new Document("$ne", List.of(new Document("$ifNull", List.of("$$med.active", true)), false)))));
        // Quantidade e revisao sao conferidas no mesmo comando atomico.
        Bson filter = Filters.and(owner(treatmentId, userId), Filters.elemMatch("medications", current),
            new Document("$expr", new Document("$gt", List.of(activeCount, minimumRemainingActive))));
        return treatments.updateOne(filter, Updates.combine(Updates.set("medications.$.active", false),
            Updates.inc("medications.$.revision", 1L))).getMatchedCount() == 1;
    }

    private Bson revisionFilter(long revision) {
        if (revision < 0 || revision == Long.MAX_VALUE) { throw new IllegalArgumentException("Revisao invalida."); }
        // Documentos antigos sem revision equivalem a revisao zero.
        return revision == 0 ? Filters.or(Filters.exists("revision", false), Filters.eq("revision", 0L))
            : Filters.eq("revision", revision);
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

    private Document fields(MedicamentoEmUso medication) {
        return new Document("name", medication.getName()).append("dose", medication.getDose())
            .append("quantity", medication.getQuantity()).append("unit", medication.getUnit())
            .append("observations", medication.getObservations());
    }
}
