package backend.models;

import java.time.LocalDate;
import java.util.Objects;

/**
 * DTO de resumo dos indicadores de adesão (execução) e pontualidade em um período.
 * Separa explicitamente as contagens brutas, os percentuais derivados e os estados NO_DATA.
 */
public class AdherenceSummary {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_NO_DATA = "NO_DATA";

    private final LocalDate from;
    private final LocalDate to;
    private final String treatmentId;
    private final String medicationInUseId;
    private final int eligiblePlannedDoses;
    private final int takenDoses;
    private final int skippedDoses;
    private final int unrecordedOverdueDoses;
    private final int onTimeTakenDoses;
    private final int lateTakenDoses;
    private final Double executionPercent;
    private final Double punctualityPercent;
    private final long punctualityToleranceMinutes;
    private final String executionStatus;
    private final String punctualityStatus;

    public AdherenceSummary(
            LocalDate from,
            LocalDate to,
            String treatmentId,
            String medicationInUseId,
            int eligiblePlannedDoses,
            int takenDoses,
            int skippedDoses,
            int unrecordedOverdueDoses,
            int onTimeTakenDoses,
            int lateTakenDoses,
            Double executionPercent,
            Double punctualityPercent,
            long punctualityToleranceMinutes) {
        this.from = Objects.requireNonNull(from, "from não pode ser null");
        this.to = Objects.requireNonNull(to, "to não pode ser null");
        this.treatmentId = treatmentId;
        this.medicationInUseId = medicationInUseId;
        this.eligiblePlannedDoses = eligiblePlannedDoses;
        this.takenDoses = takenDoses;
        this.skippedDoses = skippedDoses;
        this.unrecordedOverdueDoses = unrecordedOverdueDoses;
        this.onTimeTakenDoses = onTimeTakenDoses;
        this.lateTakenDoses = lateTakenDoses;
        this.executionPercent = executionPercent;
        this.punctualityPercent = punctualityPercent;
        this.punctualityToleranceMinutes = punctualityToleranceMinutes;
        this.executionStatus = executionPercent != null ? STATUS_OK : STATUS_NO_DATA;
        this.punctualityStatus = punctualityPercent != null ? STATUS_OK : STATUS_NO_DATA;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }

    public String getTreatmentId() {
        return treatmentId;
    }

    public String getMedicationInUseId() {
        return medicationInUseId;
    }

    public int getEligiblePlannedDoses() {
        return eligiblePlannedDoses;
    }

    public int getTakenDoses() {
        return takenDoses;
    }

    public int getSkippedDoses() {
        return skippedDoses;
    }

    public int getUnrecordedOverdueDoses() {
        return unrecordedOverdueDoses;
    }

    public int getOnTimeTakenDoses() {
        return onTimeTakenDoses;
    }

    public int getLateTakenDoses() {
        return lateTakenDoses;
    }

    public Double getExecutionPercent() {
        return executionPercent;
    }

    public Double getPunctualityPercent() {
        return punctualityPercent;
    }

    public long getPunctualityToleranceMinutes() {
        return punctualityToleranceMinutes;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public String getPunctualityStatus() {
        return punctualityStatus;
    }
}
