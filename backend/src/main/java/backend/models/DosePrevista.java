package backend.models;

import java.time.Instant;

public class DosePrevista {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String CANCELLED = "CANCELLED";

    private final String id;
    private final String userId;
    private final String treatmentId;
    private final String medicationInUseId;
    private final int scheduleVersion;
    private final Instant scheduledAt;  // Instant representa o instante exato da administração prevista
    // É mais adequado do que uma String porque o MongoDB armazena datas como instantes
    private final String scheduleTimezone;
    private final int plannedQuantity;
    private final String status;

    public DosePrevista(
            String id,
            String userId,
            String treatmentId,
            String medicationInUseId,
            int scheduleVersion,
            Instant scheduledAt,
            String scheduleTimezone,
            int plannedQuantity,
            String status) {
        // isBlank() é para verificar vazio ou contém apenas espaços em branco
        if (id == null || id.isBlank()
                || userId == null || userId.isBlank()
                || treatmentId == null || treatmentId.isBlank()
                || medicationInUseId == null || medicationInUseId.isBlank()) {
            throw new IllegalArgumentException("IDs não podem ser nulos ou vazios");
        }

        if (scheduleVersion < 1) {
            throw new IllegalArgumentException("Versão do cronograma inválida");
        }

        if (scheduledAt == null) {
            throw new IllegalArgumentException("Horário previsto não pode ser null");
        }

        if (scheduleTimezone == null || scheduleTimezone.isBlank()) {
            throw new IllegalArgumentException("Timezone inválido");
        }

        if (plannedQuantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }

        if (!SCHEDULED.equals(status) && !CANCELLED.equals(status)) {
            throw new IllegalArgumentException("Status inválido");
        }

        this.id = id.trim();
        this.userId = userId.trim();
        this.treatmentId = treatmentId.trim();
        this.medicationInUseId = medicationInUseId.trim();
        this.scheduleVersion = scheduleVersion;
        this.scheduledAt = scheduledAt;
        this.scheduleTimezone = scheduleTimezone.trim();
        this.plannedQuantity = plannedQuantity;
        this.status = status;
    }

    // Cancela a ocorrência sem alterar os dados históricos dela
    public DosePrevista cancel() {
        if (CANCELLED.equals(status)) {
            return this;
        }

        return new DosePrevista(
                id, userId, treatmentId, medicationInUseId,
                scheduleVersion, scheduledAt, scheduleTimezone,
                plannedQuantity, CANCELLED
        );
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

    public int getScheduleVersion() {
        return scheduleVersion;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getScheduleTimezone() {
        return scheduleTimezone;
    }

    public int getPlannedQuantity() {
        return plannedQuantity;
    }

    public String getStatus() {
        return status;
    }
}
