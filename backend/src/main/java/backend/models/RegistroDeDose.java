package backend.models;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa uma ação explícita do usuário sobre uma DosePrevista (collection dose_records).
 * A ausência de ação na agenda diária é representada pela inexistência de um RegistroDeDose,
 * nunca por um registro automático de SKIPPED.
 */
public class RegistroDeDose {
    private final String id;
    private final String plannedDoseId;
    private final String userId;
    private final DoseAction action;
    private final Instant actualTime;
    private final Instant recordedAt;

    public RegistroDeDose(
            String id,
            String plannedDoseId,
            String userId,
            DoseAction action,
            Instant actualTime,
            Instant recordedAt) {
        Objects.requireNonNull(plannedDoseId, "plannedDoseId não pode ser null");
        if (plannedDoseId.trim().isEmpty()) {
            throw new IllegalArgumentException("plannedDoseId não pode ser vazio");
        }
        Objects.requireNonNull(userId, "userId não pode ser null");
        if (userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId não pode ser vazio");
        }
        Objects.requireNonNull(action, "action não pode ser null");
        Objects.requireNonNull(recordedAt, "recordedAt não pode ser null");

        this.id = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id.trim();
        this.plannedDoseId = plannedDoseId.trim();
        this.userId = userId.trim();
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

    public String getUserId() {
        return userId;
    }

    public DoseAction getAction() {
        return action;
    }

    public Instant getActualTime() {
        return actualTime;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
