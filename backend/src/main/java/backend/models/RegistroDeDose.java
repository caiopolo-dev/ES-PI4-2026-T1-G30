package backend.models;

import java.time.Instant;

public class RegistroDeDose {

    public static final String TAKEN = "TAKEN";
    public static final String SKIPPED = "SKIPPED";

    private final String id;
    private final String plannedDoseId;
    private final String action;
    private final Instant actualTime;
    private final Instant recordedAt;

    public RegistroDeDose(
            String id,
            String plannedDoseId,
            String action,
            Instant actualTime,
            Instant recordedAt) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID do registro inválido");
        }

        if (plannedDoseId == null || plannedDoseId.isBlank()) {
            throw new IllegalArgumentException("ID da dose prevista inválido");
        }

        if (!TAKEN.equals(action) && !SKIPPED.equals(action)) {
            throw new IllegalArgumentException("Ação inválida");
        }

        if (recordedAt == null) {
            throw new IllegalArgumentException("Horário do registro não pode ser null");
        }

        // Se tomou, precisamos saber quando
        if (TAKEN.equals(action) && actualTime == null) {
            throw new IllegalArgumentException("TAKEN precisa de actualTime");
        }

        // Se não tomou, não existe horário de ingestão
        if (SKIPPED.equals(action) && actualTime != null) {
            throw new IllegalArgumentException("SKIPPED não deve ter actualTime");
        }

        this.id = id.trim();
        this.plannedDoseId = plannedDoseId.trim();
        this.action = action;
        this.actualTime = actualTime;
        this.recordedAt = recordedAt;
    }

    public String getId() {
        return id;
    }

    public String getPlannedDoseId() {
        return plannedDoseId;
    }

    public String getAction() {
        return action;
    }

    public Instant getActualTime() {
        return actualTime;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
