package backend.services;

import backend.models.DailyAgendaItem;
import backend.models.DailyAgendaResponse;
import backend.models.DisplayStatus;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.MedicamentoEmUso;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.models.Treatment;
import backend.repositories.DoseRecordRepository;
import backend.repositories.PlannedDoseRepository;
import backend.repositories.TreatmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Serviço de domínio responsável pela consulta da agenda diária de doses do usuário autenticado.
 *
 * <p>Totalmente desacoplado da camada HTTP e de classes específicas do driver MongoDB.
 */
public class AgendaService {

    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";
    public static final Duration DEFAULT_DUE_WINDOW = Duration.ofMinutes(30);
    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final PlannedDoseRepository plannedDoseRepository;
    private final DoseRecordRepository doseRecordRepository;
    private final TreatmentRepository treatmentRepository;
    private final ZoneId defaultZoneId;
    private final Clock clock;
    private final Duration dueWindow;

    public AgendaService(
            PlannedDoseRepository plannedDoseRepository,
            DoseRecordRepository doseRecordRepository) {
        this(
                plannedDoseRepository,
                doseRecordRepository,
                null,
                ZoneId.of(DEFAULT_TIMEZONE),
                Clock.systemUTC(),
                DEFAULT_DUE_WINDOW);
    }

    public AgendaService(
            PlannedDoseRepository plannedDoseRepository,
            DoseRecordRepository doseRecordRepository,
            TreatmentRepository treatmentRepository,
            ZoneId defaultZoneId,
            Clock clock,
            Duration dueWindow) {
        this.plannedDoseRepository = Objects.requireNonNull(
                plannedDoseRepository, "plannedDoseRepository não pode ser null");
        this.doseRecordRepository = Objects.requireNonNull(
                doseRecordRepository, "doseRecordRepository não pode ser null");
        this.treatmentRepository = treatmentRepository;
        this.defaultZoneId = Objects.requireNonNull(defaultZoneId, "defaultZoneId não pode ser null");
        this.clock = Objects.requireNonNull(clock, "clock não pode ser null");
        this.dueWindow = Objects.requireNonNull(dueWindow, "dueWindow não pode ser null");
    }

    /**
     * Consulta as doses previstas para o usuário autenticado na data civil solicitada,
     * utilizando a zona de referência padrão configurada para a agenda.
     */
    public DailyAgendaResponse getDailyAgenda(String authenticatedUserId, LocalDate date) {
        return getDailyAgenda(authenticatedUserId, date, defaultZoneId);
    }

    /**
     * Consulta as doses previstas para o usuário autenticado na data civil e fuso de referência informados.
     *
     * <p>Regras aplicadas:
     * <ul>
     *   <li>Calcula [início do dia, início do dia seguinte) no fuso aplicável convertido para UTC;</li>
     *   <li>Restringe estritamente aos dados do titular autenticado;</li>
     *   <li>Omitir ocorrências com status CANCELLED da visão de doses devidas;</li>
     *   <li>Ordena por scheduledAt crescente com desempate estável por ID;</li>
     *   <li>Combina em lote com os registros de DoseRecordRepository sem criar novos documentos;</li>
     *   <li>Deriva o DisplayStatus distinguindo ausência de ação (OVERDUE/DUE/UPCOMING) de SKIPPED.</li>
     * </ul>
     */
    public DailyAgendaResponse getDailyAgenda(String authenticatedUserId, LocalDate date, ZoneId referenceZone) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId não pode ser null");
        if (authenticatedUserId.trim().isEmpty()) {
            throw new IllegalArgumentException("authenticatedUserId não pode ser vazio");
        }
        Objects.requireNonNull(date, "date não pode ser null");
        ZoneId effectiveZone = referenceZone != null ? referenceZone : defaultZoneId;

        String normalizedUserId = authenticatedUserId.trim();

        // Calcula o intervalo civil [início do dia, início do dia seguinte) em UTC respeitando regras de fuso/DST
        Instant startInclusive = date.atStartOfDay(effectiveZone).toInstant();
        Instant endExclusive = date.plusDays(1).atStartOfDay(effectiveZone).toInstant();

        List<DosePrevista> rawDoses = plannedDoseRepository.findByUserAndPeriod(
                normalizedUserId, startInclusive, endExclusive);

        // Filtra estritamente por titularidade e exclui doses canceladas (CANCELLED)
        List<DosePrevista> validScheduledDoses = new ArrayList<>();
        for (DosePrevista dose : rawDoses) {
            if (dose != null
                    && normalizedUserId.equals(dose.getUserId())
                    && dose.getStatus() == ScheduleStatus.SCHEDULED) {
                validScheduledDoses.add(dose);
            }
        }

        if (validScheduledDoses.isEmpty()) {
            return new DailyAgendaResponse(date, effectiveZone.getId(), Collections.emptyList());
        }

        // Ordena por scheduledAt crescente, com desempate estável por ID
        validScheduledDoses.sort(
                Comparator.comparing(DosePrevista::getScheduledAt)
                        .thenComparing(DosePrevista::getId));

        // Coleta os IDs para carregamento em lote dos registros (evita N+1 queries)
        List<String> plannedDoseIds = new ArrayList<>(validScheduledDoses.size());
        for (DosePrevista dose : validScheduledDoses) {
            plannedDoseIds.add(dose.getId());
        }
        Map<String, RegistroDeDose> recordsByDoseId =
                doseRecordRepository.findByPlannedDoseIds(plannedDoseIds);

        Instant now = clock.instant();
        List<DailyAgendaItem> items = new ArrayList<>(validScheduledDoses.size());

        for (DosePrevista dose : validScheduledDoses) {
            RegistroDeDose record = recordsByDoseId != null ? recordsByDoseId.get(dose.getId()) : null;
            if (record != null && !normalizedUserId.equals(record.getUserId())) {
                record = null;
            }

            DisplayStatus displayStatus = deriveDisplayStatus(dose, record, now);
            String medicationName = resolveMedicationName(dose, normalizedUserId);

            ZoneId doseZone = ZoneId.of(dose.getScheduleTimezone());
            ZonedDateTime localDateTime = dose.getScheduledAt().atZone(doseZone);
            String scheduledLocalTime = LOCAL_TIME_FORMATTER.format(localDateTime);

            items.add(new DailyAgendaItem(
                    dose.getId(),
                    dose.getTreatmentId(),
                    dose.getMedicationInUseId(),
                    medicationName,
                    dose.getPlannedQuantity(),
                    dose.getScheduledAt(),
                    scheduledLocalTime,
                    dose.getStatus(),
                    record,
                    displayStatus));
        }

        return new DailyAgendaResponse(date, effectiveZone.getId(), items);
    }

    private DisplayStatus deriveDisplayStatus(DosePrevista dose, RegistroDeDose record, Instant now) {
        if (record != null) {
            if (record.getAction() == DoseAction.TAKEN) {
                return DisplayStatus.TAKEN;
            }
            if (record.getAction() == DoseAction.SKIPPED) {
                return DisplayStatus.SKIPPED;
            }
        }

        Instant scheduledAt = dose.getScheduledAt();
        if (now.isBefore(scheduledAt)) {
            return DisplayStatus.UPCOMING;
        }
        Instant dueLimit = scheduledAt.plus(dueWindow);
        if (!now.isAfter(dueLimit)) {
            return DisplayStatus.DUE;
        }
        // Ausência de registro após o horário/janela é OVERDUE (vencida/pendente), nunca SKIPPED
        return DisplayStatus.OVERDUE;
    }

    private String resolveMedicationName(DosePrevista dose, String authenticatedUserId) {
        if (dose.getMedicationName() != null && !dose.getMedicationName().isEmpty()) {
            return dose.getMedicationName();
        }
        if (treatmentRepository != null) {
            Optional<Treatment> treatmentOpt = treatmentRepository.findByIdAndUserId(
                    dose.getTreatmentId(), authenticatedUserId);
            if (treatmentOpt.isPresent()) {
                Optional<MedicamentoEmUso> medOpt = treatmentOpt.get()
                        .findMedicationById(dose.getMedicationInUseId());
                if (medOpt.isPresent()) {
                    return medOpt.get().getName();
                }
            }
        }
        return dose.getMedicationInUseId();
    }
}
