package backend.repositories;

import backend.models.DoseAction;
import backend.models.RegistroDeDose;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação de DoseRecordRepository alinhada à modelagem física da collection dose_records no MongoDB.
 * Garante a restrição unique(plannedDoseId) e suporta consulta em lote (findByPlannedDoseIds)
 * para evitar o problema de N+1 queries ao montar a agenda diária.
 */
public class MongoDoseRecordRepository implements DoseRecordRepository {

    public static final String DEFAULT_COLLECTION = "dose_records";

    // Armazenamento de documentos (_id -> Document Map)
    private final Map<String, Map<String, Object>> documentCollection = new LinkedHashMap<>();
    // Índice único: plannedDoseId -> _id
    private final Map<String, String> uniquePlannedDoseIndex = new LinkedHashMap<>();

    @Override
    public synchronized RegistroDeDose save(RegistroDeDose registroDeDose) {
        Objects.requireNonNull(registroDeDose, "registroDeDose não pode ser null");

        String existingDocId = uniquePlannedDoseIndex.get(registroDeDose.getPlannedDoseId());
        if (existingDocId != null && !existingDocId.equals(registroDeDose.getId())) {
            throw new IllegalStateException(
                    "Já existe um registro ativo para a dose prevista: " + registroDeDose.getPlannedDoseId());
        }

        Map<String, Object> doc = toDocument(registroDeDose);
        String id = (String) doc.get("_id");
        documentCollection.put(id, doc);
        uniquePlannedDoseIndex.put(registroDeDose.getPlannedDoseId(), id);

        return fromDocument(doc);
    }

    @Override
    public synchronized Optional<RegistroDeDose> findByPlannedDoseId(String plannedDoseId) {
        if (plannedDoseId == null || plannedDoseId.trim().isEmpty()) {
            return Optional.empty();
        }
        String docId = uniquePlannedDoseIndex.get(plannedDoseId.trim());
        if (docId == null) {
            return Optional.empty();
        }
        Map<String, Object> doc = documentCollection.get(docId);
        return doc != null ? Optional.of(fromDocument(doc)) : Optional.empty();
    }

    @Override
    public synchronized Map<String, RegistroDeDose> findByPlannedDoseIds(Collection<String> plannedDoseIds) {
        if (plannedDoseIds == null || plannedDoseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, RegistroDeDose> result = new LinkedHashMap<>();
        for (String plannedDoseId : plannedDoseIds) {
            if (plannedDoseId != null) {
                String docId = uniquePlannedDoseIndex.get(plannedDoseId.trim());
                if (docId != null) {
                    Map<String, Object> doc = documentCollection.get(docId);
                    if (doc != null) {
                        result.put(plannedDoseId.trim(), fromDocument(doc));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public synchronized int countAll() {
        return documentCollection.size();
    }

    private Map<String, Object> toDocument(RegistroDeDose record) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("_id", record.getId());
        doc.put("plannedDoseId", record.getPlannedDoseId());
        doc.put("userId", record.getUserId());
        doc.put("action", record.getAction().name());
        doc.put("actualTime", record.getActualTime() != null ? record.getActualTime().toString() : null);
        doc.put("recordedAt", record.getRecordedAt().toString());
        return doc;
    }

    private RegistroDeDose fromDocument(Map<String, Object> doc) {
        String actualTimeStr = (String) doc.get("actualTime");
        return new RegistroDeDose(
                (String) doc.get("_id"),
                (String) doc.get("plannedDoseId"),
                (String) doc.get("userId"),
                DoseAction.valueOf((String) doc.get("action")),
                actualTimeStr != null ? Instant.parse(actualTimeStr) : null,
                Instant.parse((String) doc.get("recordedAt")));
    }
}
