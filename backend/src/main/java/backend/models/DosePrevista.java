package backend.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa uma ocorrência concreta de dose prevista (collection planned_doses).
 * Preserva o contexto histórico do agendamento em UTC e o fuso de referência.
 */
public class DosePrevista {
    private final String id;
    private final String userId;
    private final String treatmentId;
    private final String medicationInUseId;
    private final String medicationName;
    private final int scheduleVersion;
    private final Instant scheduledAt;
    private final String scheduleTimezone;
    private final BigDecimal plannedQuantity;
    private final ScheduleStatus status;

    public DosePrevista(
            String id,
            String userId,
            String treatmentId,
            String medicationInUseId,
            String medicationName,
            int scheduleVersion,
            Instant scheduledAt,
            String scheduleTimezone,
            BigDecimal plannedQuantity,
            ScheduleStatus status) {
        Objects.requireNonNull(userId, "userId não pode ser null");
        if (userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId não pode ser vazio");
        }
        Objects.requireNonNull(treatmentId, "treatmentId não pode ser null");
        if (treatmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("treatmentId não pode ser vazio");
        }
        Objects.requireNonNull(medicationInUseId, "medicationInUseId não pode ser null");
        if (medicationInUseId.trim().isEmpty()) {
            throw new IllegalArgumentException("medicationInUseId não pode ser vazio");
        }
        if (scheduleVersion < 1) {
            throw new IllegalArgumentException("scheduleVersion deve ser >= 1");
        }
        Objects.requireNonNull(scheduledAt, "scheduledAt não pode ser null");
        Objects.requireNonNull(scheduleTimezone, "scheduleTimezone não pode ser null");
        ZoneId.of(scheduleTimezone.trim());
        Objects.requireNonNull(plannedQuantity, "plannedQuantity não pode ser null");
        if (plannedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("plannedQuantity deve ser maior que zero");
        }
        Objects.requireNonNull(status, "status não pode ser null");

        this.id = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id.trim();
        this.userId = userId.trim();
        this.treatmentId = treatmentId.trim();
        this.medicationInUseId = medicationInUseId.trim();
        this.medicationName = (medicationName != null && !medicationName.trim().isEmpty())
                ? medicationName.trim()
                : null;
        this.scheduleVersion = scheduleVersion;
        this.scheduledAt = scheduledAt;
        this.scheduleTimezone = scheduleTimezone.trim();
        this.plannedQuantity = plannedQuantity.stripTrailingZeros();
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTreatmentId() {
        return treatmentId;
    }

    public String getMedicationInUseId() {
        return medicationInUseId;
    }

    public String getMedicationName() {
        return medicationName;
    }

    public int getScheduleVersion() {
        return scheduleVersion;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getScheduleTimezone() {
        return scheduleTimezone;
    }

    public BigDecimal getPlannedQuantity() {
        return plannedQuantity;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DosePrevista)) {
            return false;
        }
        DosePrevista that = (DosePrevista) o;
        return scheduleVersion == that.scheduleVersion
                && Objects.equals(id, that.id)
                && Objects.equals(userId, that.userId)
                && Objects.equals(treatmentId, that.treatmentId)
                && Objects.equals(medicationInUseId, that.medicationInUseId)
                && Objects.equals(medicationName, that.medicationName)
                && Objects.equals(scheduledAt, that.scheduledAt)
                && Objects.equals(scheduleTimezone, that.scheduleTimezone)
                && (plannedQuantity != null && that.plannedQuantity != null
                        ? plannedQuantity.compareTo(that.plannedQuantity) == 0
                        : Objects.equals(plannedQuantity, that.plannedQuantity))
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                userId,
                treatmentId,
                medicationInUseId,
                medicationName,
                scheduleVersion,
                scheduledAt,
                scheduleTimezone,
                plannedQuantity != null ? plannedQuantity.stripTrailingZeros() : null,
                status);
    }
}
