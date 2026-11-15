package backend.repositories;

import backend.models.DosePrevista;
import backend.models.ScheduleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação de PlannedDoseRepository alinhada à modelagem física da collection planned_doses no MongoDB.
 * Garante idempotência lógica pela chave composta (medicationInUseId + scheduleVersion + scheduledAt)
 * e filtragem eficiente por titular (userId) e janela de tempo UTC [startInclusive, endExclusive).
 */
public class MongoPlannedDoseRepository implements PlannedDoseRepository {

    public static final String DEFAULT_COLLECTION = "planned_doses";

    // Armazenamento de documentos no formato da collection planned_doses (_id -> Document Map)
    private final Map<String, Map<String, Object>> documentCollection = new LinkedHashMap<>();
    // Índice de unicidade lógica: (medicationInUseId|scheduleVersion|scheduledAt) -> _id
    private final Map<String, String> logicalIdentityIndex = new LinkedHashMap<>();

    @Override
    public synchronized DosePrevista save(DosePrevista dosePrevista) {
        Objects.requireNonNull(dosePrevista, "dosePrevista não pode ser null");

        String logicalKey = buildLogicalKey(
                dosePrevista.getMedicationInUseId(),
                dosePrevista.getScheduleVersion(),
                dosePrevista.getScheduledAt());

        String existingId = logicalIdentityIndex.get(logicalKey);
        if (existingId != null && !existingId.equals(dosePrevista.getId())) {
            Map<String, Object> existingDoc = documentCollection.get(existingId);
            if (existingDoc != null) {
                return fromDocument(existingDoc);
            }
        }

        Map<String, Object> document = toDocument(dosePrevista);
        String id = (String) document.get("_id");
        documentCollection.put(id, document);
        logicalIdentityIndex.put(logicalKey, id);

        return fromDocument(document);
    }

    @Override
    public synchronized Optional<DosePrevista> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> doc = documentCollection.get(id.trim());
        return doc != null ? Optional.of(fromDocument(doc)) : Optional.empty();
    }

    @Override
    public synchronized List<DosePrevista> findByUserAndPeriod(
            String userId, Instant startInclusive, Instant endExclusive) {
        if (userId == null || userId.trim().isEmpty() || startInclusive == null || endExclusive == null) {
            return new ArrayList<>();
        }
        String normalizedUserId = userId.trim();
        List<DosePrevista> result = new ArrayList<>();

        for (Map<String, Object> doc : documentCollection.values()) {
            String docUserId = (String) doc.get("userId");
            Instant scheduledAt = Instant.parse((String) doc.get("scheduledAt"));
            if (normalizedUserId.equals(docUserId)
                    && !scheduledAt.isBefore(startInclusive)
                    && scheduledAt.isBefore(endExclusive)) {
                result.add(fromDocument(doc));
            }
        }
        return result;
    }

    @Override
    public synchronized int countAll() {
        return documentCollection.size();
    }

    private String buildLogicalKey(String medicationInUseId, int scheduleVersion, Instant scheduledAt) {
        return medicationInUseId + "|" + scheduleVersion + "|" + scheduledAt.toString();
    }

    private Map<String, Object> toDocument(DosePrevista dose) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("_id", dose.getId());
        doc.put("userId", dose.getUserId());
        doc.put("treatmentId", dose.getTreatmentId());
        doc.put("medicationInUseId", dose.getMedicationInUseId());
        doc.put("medicationName", dose.getMedicationName());
        doc.put("scheduleVersion", dose.getScheduleVersion());
        doc.put("scheduledAt", dose.getScheduledAt().toString());
        doc.put("scheduleTimezone", dose.getScheduleTimezone());
        doc.put("plannedQuantity", dose.getPlannedQuantity().toPlainString());
        doc.put("status", dose.getStatus().name());
        return doc;
    }

    private DosePrevista fromDocument(Map<String, Object> doc) {
        return new DosePrevista(
                (String) doc.get("_id"),
                (String) doc.get("userId"),
                (String) doc.get("treatmentId"),
                (String) doc.get("medicationInUseId"),
                (String) doc.get("medicationName"),
                (Integer) doc.get("scheduleVersion"),
                Instant.parse((String) doc.get("scheduledAt")),
                (String) doc.get("scheduleTimezone"),
                new BigDecimal((String) doc.get("plannedQuantity")),
                ScheduleStatus.valueOf((String) doc.get("status")));
    }
}
