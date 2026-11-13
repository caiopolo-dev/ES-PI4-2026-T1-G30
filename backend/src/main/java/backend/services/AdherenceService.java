package backend.services;

import backend.models.AdherenceSummary;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.repositories.DoseRecordRepository;
import backend.repositories.PlannedDoseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço de domínio responsável pelo cálculo dos indicadores de adesão (execução) e pontualidade.
 *
 * <p>Política aplicada:
 * <ul>
 *   <li>Separa execução (TAKEN / elegíveis) de pontualidade (pontuais / TAKEN);</li>
 *   <li>Considera elegíveis apenas doses do usuário autenticado, não canceladas (SCHEDULED)
 *       e já vencidas no instante de referência capturado no início da avaliação;</li>
 *   <li>Exclui doses futuras do denominador;</li>
 *   <li>Retorna null (estado NO_DATA) quando não houver doses elegíveis ou doses tomadas;</li>
 *   <li>Totalmente independente de HTTP, sockets e classes específicas do driver MongoDB.</li>
 * </ul>
 */
public class AdherenceService {

    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";
    public static final Duration DEFAULT_PUNCTUALITY_TOLERANCE = Duration.ofMinutes(30);
    public static final Duration DEFAULT_EARLY_TOLERANCE = Duration.ofMinutes(30);

    private final PlannedDoseRepository plannedDoseRepository;
    private final DoseRecordRepository doseRecordRepository;
    private final ZoneId defaultZoneId;
    private final Clock clock;
    private final Duration punctualityTolerance;
    private final Duration earlyTolerance;

    public AdherenceService(
            PlannedDoseRepository plannedDoseRepository,
            DoseRecordRepository doseRecordRepository) {
        this(
                plannedDoseRepository,
                doseRecordRepository,
                ZoneId.of(DEFAULT_TIMEZONE),
                Clock.systemUTC(),
                DEFAULT_PUNCTUALITY_TOLERANCE,
                DEFAULT_EARLY_TOLERANCE);
    }

    public AdherenceService(
            PlannedDoseRepository plannedDoseRepository,
            DoseRecordRepository doseRecordRepository,
            ZoneId defaultZoneId,
            Clock clock,
            Duration punctualityTolerance,
            Duration earlyTolerance) {
        this.plannedDoseRepository = Objects.requireNonNull(
                plannedDoseRepository, "plannedDoseRepository não pode ser null");
        this.doseRecordRepository = Objects.requireNonNull(
                doseRecordRepository, "doseRecordRepository não pode ser null");
        this.defaultZoneId = Objects.requireNonNull(defaultZoneId, "defaultZoneId não pode ser null");
        this.clock = Objects.requireNonNull(clock, "clock não pode ser null");
        this.punctualityTolerance = Objects.requireNonNull(
                punctualityTolerance, "punctualityTolerance não pode ser null");
        this.earlyTolerance = Objects.requireNonNull(
                earlyTolerance, "earlyTolerance não pode ser null");
        if (punctualityTolerance.isNegative() || earlyTolerance.isNegative()) {
            throw new IllegalArgumentException("Tolerâncias de pontualidade não podem ser negativas");
        }
    }

    public AdherenceSummary calculateSummary(String authenticatedUserId, LocalDate from, LocalDate to) {
        return calculateSummary(authenticatedUserId, from, to, null, null, defaultZoneId);
    }

    public AdherenceSummary calculateSummary(
            String authenticatedUserId,
            LocalDate from,
            LocalDate to,
            String treatmentId,
            String medicationInUseId) {
        return calculateSummary(authenticatedUserId, from, to, treatmentId, medicationInUseId, defaultZoneId);
    }

    /**
     * Calcula o resumo de execução e pontualidade para o usuário autenticado no período civil [from, to].
     */
    public AdherenceSummary calculateSummary(
            String authenticatedUserId,
            LocalDate from,
            LocalDate to,
            String treatmentId,
            String medicationInUseId,
            ZoneId referenceZone) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId não pode ser null");
        if (authenticatedUserId.trim().isEmpty()) {
            throw new IllegalArgumentException("authenticatedUserId não pode ser vazio");
        }
        Objects.requireNonNull(from, "from não pode ser null");
        Objects.requireNonNull(to, "to não pode ser null");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Data inicial 'from' não pode ser posterior a 'to'");
        }

        // Captura um único instante de referência no início do cálculo para consistência
        Instant referenceTime = clock.instant();

        String normalizedUserId = authenticatedUserId.trim();
        String normalizedTreatmentId = normalizeFilter(treatmentId);
        String normalizedMedicationId = normalizeFilter(medicationInUseId);
        ZoneId effectiveZone = referenceZone != null ? referenceZone : defaultZoneId;

        Instant startInclusive = from.atStartOfDay(effectiveZone).toInstant();
        Instant endExclusive = to.plusDays(1).atStartOfDay(effectiveZone).toInstant();

        // Deduplica por ID de ocorrência para evitar contagem dupla
        Map<String, DosePrevista> eligibleById = new LinkedHashMap<>();
        for (DosePrevista dose : plannedDoseRepository.findByUserAndPeriod(
                normalizedUserId, startInclusive, endExclusive)) {
            if (isEligible(dose, normalizedUserId, normalizedTreatmentId, normalizedMedicationId, referenceTime)) {
                eligibleById.putIfAbsent(dose.getId(), dose);
            }
        }

        int eligiblePlannedDoses = eligibleById.size();
        if (eligiblePlannedDoses == 0) {
            return new AdherenceSummary(
                    from,
                    to,
                    normalizedTreatmentId,
                    normalizedMedicationId,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    punctualityTolerance.toMinutes());
        }

        Map<String, RegistroDeDose> recordsByDoseId =
                doseRecordRepository.findByPlannedDoseIds(eligibleById.keySet());

        int takenDoses = 0;
        int skippedDoses = 0;
        int unrecordedOverdueDoses = 0;
        int onTimeTakenDoses = 0;
        int lateTakenDoses = 0;

        for (DosePrevista dose : eligibleById.values()) {
            RegistroDeDose record = recordsByDoseId != null ? recordsByDoseId.get(dose.getId()) : null;
            if (record != null && !normalizedUserId.equals(record.getUserId())) {
                record = null;
            }

            if (record == null) {
                unrecordedOverdueDoses++;
                continue;
            }

            if (record.getAction() == DoseAction.TAKEN) {
                takenDoses++;
                Instant effectiveActualTime = record.getActualTime() != null
                        ? record.getActualTime()
                        : record.getRecordedAt();
                if (isOnTime(dose.getScheduledAt(), effectiveActualTime)) {
                    onTimeTakenDoses++;
                } else {
                    lateTakenDoses++;
                }
            } else if (record.getAction() == DoseAction.SKIPPED) {
                skippedDoses++;
            }
        }

        Double executionPercent = calculatePercent(takenDoses, eligiblePlannedDoses);
        Double punctualityPercent = takenDoses > 0 ? calculatePercent(onTimeTakenDoses, takenDoses) : null;

        return new AdherenceSummary(
                from,
                to,
                normalizedTreatmentId,
                normalizedMedicationId,
                eligiblePlannedDoses,
                takenDoses,
                skippedDoses,
                unrecordedOverdueDoses,
                onTimeTakenDoses,
                lateTakenDoses,
                executionPercent,
                punctualityPercent,
                punctualityTolerance.toMinutes());
    }

    private boolean isEligible(
            DosePrevista dose,
            String authenticatedUserId,
            String treatmentIdFilter,
            String medicationIdFilter,
            Instant referenceTime) {
        if (dose == null || !authenticatedUserId.equals(dose.getUserId())) {
            return false;
        }
        if (dose.getStatus() != ScheduleStatus.SCHEDULED) {
            return false;
        }
        if (treatmentIdFilter != null && !treatmentIdFilter.equals(dose.getTreatmentId())) {
            return false;
        }
        if (medicationIdFilter != null && !medicationIdFilter.equals(dose.getMedicationInUseId())) {
            return false;
        }
        // Doses futuras (scheduledAt > referenceTime) não entram no denominador
        return !dose.getScheduledAt().isAfter(referenceTime);
    }

    private boolean isOnTime(Instant scheduledAt, Instant actualTime) {
        Instant lowerBound = scheduledAt.minus(earlyTolerance);
        Instant upperBound = scheduledAt.plus(punctualityTolerance);
        return !actualTime.isBefore(lowerBound) && !actualTime.isAfter(upperBound);
    }

    private Double calculatePercent(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String normalizeFilter(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
