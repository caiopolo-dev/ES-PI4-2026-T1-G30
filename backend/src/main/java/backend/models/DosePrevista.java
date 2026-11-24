// Autor: Samuel Campovilla
package backend.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

// Representa uma ocorrência concreta de administração de um medicamento,
// gerada a partir de um CronogramaDose.
// Armazena quando e quanto deve ser tomado, permitindo organizar a agenda
// e acompanhar as doses previstas de um tratamento.

public class DosePrevista {

    private final String id;
    private final String userId;
    private final String treatmentId;
    private final String medicationInUseId;
    private final String medicationName; // Armazena o nome do medicamento associado à dose
    private final int scheduleVersion;
    private final Instant scheduledAt;  // Instant representa o instante exato da administração prevista
    // É mais adequado do que uma String porque o MongoDB armazena datas como instantes
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

        // Gera um ID automaticamente quando não for informado
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        if (userId == null || userId.isBlank()
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

        // BigDecimal é um objeto; compareTo compara seu valor numérico
        if (plannedQuantity == null
                || plannedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }

        if (status == null) {
            throw new IllegalArgumentException("Status inválido");
        }

        this.id = id.trim();
        this.userId = userId.trim();
        this.treatmentId = treatmentId.trim();
        this.medicationInUseId = medicationInUseId.trim();
        this.medicationName = medicationName == null ? null : medicationName.trim();
        this.scheduleVersion = scheduleVersion;
        this.scheduledAt = scheduledAt;
        this.scheduleTimezone = scheduleTimezone.trim();
        this.plannedQuantity = plannedQuantity;
        this.status = status;
    }

    // Cancela a ocorrência sem alterar o objeto original
    public DosePrevista cancel() {
        if (status == ScheduleStatus.CANCELLED) {
            return this;
        }

        return new DosePrevista(
                id,
                userId,
                treatmentId,
                medicationInUseId,
                medicationName,
                scheduleVersion,
                scheduledAt,
                scheduleTimezone,
                plannedQuantity,
                ScheduleStatus.CANCELLED
        );
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;

        if (!(o instanceof DosePrevista)) return false;

        DosePrevista outraDose = (DosePrevista) o;

        // Duas doses com o mesmo ID representam a mesma ocorrência.
        return Objects.equals(id, outraDose.id);
    }

    @Override
    public int hashCode() {
        int hash = 7;

        hash = 31 * hash + (id == null ? 0 : id.hashCode());

        return hash;
    }

    @Override
    public String toString() {
        return "DosePrevista{" +
                "id='" + id + '\'' +
                ", treatmentId='" + treatmentId + '\'' +
                ", medicationInUseId='" + medicationInUseId + '\'' +
                ", scheduleVersion=" + scheduleVersion +
                ", scheduledAt=" + scheduledAt +
                ", scheduleTimezone='" + scheduleTimezone + '\'' +
                ", plannedQuantity=" + plannedQuantity +
                ", status=" + status +
                '}';
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
}