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
            .append("active", medication.isActive()).append("schedules", List.of());
        Bson filter = Filters.and(owner(treatmentId, userId), Filters.ne("medications.id", medication.getId()));
        return treatments.updateOne(filter, Updates.push("medications", document)).getMatchedCount() == 1;
    }

    @Override
    public Optional<MedicamentoEmUso> find(String treatmentId, String userId, String medicationId) {
        Document treatment = treatments.find(ownedMedication(treatmentId, userId, medicationId)).first();
        if (treatment == null) { return Optional.empty(); }
        for (Document medication : treatment.getList("medications", Document.class)) {
            if (medicationId.equals(medication.getString("id"))) {
                // Reutiliza o mapeamento existente, inclusive para cronogramas historicos.
                return Optional.of(new MongoTreatmentRepository().fromDocument(
                    new Document("_id", treatmentId).append("userId", userId).append("name", "mapping")
                        .append("medications", List.of(medication))).getMedications().get(0));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean update(String treatmentId, String userId, MedicamentoEmUso medication) {
        Objects.requireNonNull(medication);
        Document changes = new Document();
        for (var field : fields(medication).entrySet()) {
            changes.append("medications.$." + field.getKey(), field.getValue());
        }
        // Preserva ID, estado e todos os cronogramas armazenados.
        return treatments.updateOne(ownedMedication(treatmentId, userId, medication.getId()),
            new Document("$set", changes)).getMatchedCount() == 1;
    }

    @Override
    public boolean deactivate(String treatmentId, String userId, String medicationId) {
        return treatments.updateOne(ownedMedication(treatmentId, userId, medicationId),
            Updates.set("medications.$.active", false)).getMatchedCount() == 1;
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
