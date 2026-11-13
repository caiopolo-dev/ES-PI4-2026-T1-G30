package backend.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Representa um item de dose estruturado na resposta da agenda diária.
 * Combina os dados da DosePrevista com seu RegistroDeDose (se existir) e o DisplayStatus derivado.
 */
public class DailyAgendaItem {
    private final String id;
    private final String treatmentId;
    private final String medicationInUseId;
    private final String medicationName;
    private final BigDecimal plannedQuantity;
    private final Instant scheduledAt;
    private final String scheduledLocalTime;
    private final ScheduleStatus scheduleStatus;
    private final RegistroDeDose record;
    private final DisplayStatus displayStatus;

    public DailyAgendaItem(
            String id,
            String treatmentId,
            String medicationInUseId,
            String medicationName,
            BigDecimal plannedQuantity,
            Instant scheduledAt,
            String scheduledLocalTime,
            ScheduleStatus scheduleStatus,
            RegistroDeDose record,
            DisplayStatus displayStatus) {
        this.id = Objects.requireNonNull(id, "id não pode ser null");
        this.treatmentId = Objects.requireNonNull(treatmentId, "treatmentId não pode ser null");
        this.medicationInUseId = Objects.requireNonNull(medicationInUseId, "medicationInUseId não pode ser null");
        this.medicationName = Objects.requireNonNull(medicationName, "medicationName não pode ser null");
        this.plannedQuantity = Objects.requireNonNull(plannedQuantity, "plannedQuantity não pode ser null");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt não pode ser null");
        this.scheduledLocalTime = Objects.requireNonNull(scheduledLocalTime, "scheduledLocalTime não pode ser null");
        this.scheduleStatus = Objects.requireNonNull(scheduleStatus, "scheduleStatus não pode ser null");
        this.record = record;
        this.displayStatus = Objects.requireNonNull(displayStatus, "displayStatus não pode ser null");
    }

    public String getId() {
        return id;
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

    public BigDecimal getPlannedQuantity() {
        return plannedQuantity;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getScheduledLocalTime() {
        return scheduledLocalTime;
    }

    public ScheduleStatus getScheduleStatus() {
        return scheduleStatus;
    }

    public RegistroDeDose getRecord() {
        return record;
    }

    public DisplayStatus getDisplayStatus() {
        return displayStatus;
    }
}
