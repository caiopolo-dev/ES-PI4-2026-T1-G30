package backend.services;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.models.MedicamentoEmUso;
import backend.repositories.MedicationInUseRepository;
import java.util.List;
import java.util.Objects;
import java.time.Instant;
import backend.models.CronogramaDose;
import java.util.ArrayList;

/** Operacoes de medicamentos vinculados ao titular autenticado. */
public final class MedicationInUseService {
    private final MedicationInUseRepository repository;
    private final TreatmentMedicationRules treatmentRules;
    private final ScheduleService schedules;

    public MedicationInUseService(MedicationInUseRepository repository, TreatmentMedicationRules treatmentRules) {
        this(repository, treatmentRules, null);
    }

    public MedicationInUseService(MedicationInUseRepository repository, TreatmentMedicationRules treatmentRules,
            ScheduleService schedules) {
        this.repository = Objects.requireNonNull(repository);
        this.treatmentRules = Objects.requireNonNull(treatmentRules);
        this.schedules = schedules;
    }

    // Resultado de preparacao, sem gravacao. Mantem os dados anteriores para a integracao da persistencia.
    public record PreparedMedicationChange(MedicamentoEmUso previous, MedicamentoEmUso updated, Instant validFrom) {}

    public PreparedMedicationChange prepareVersionedUpdate(AuthContext context, String treatmentId, String medicationId,
            String name, String dose, Integer quantity, String unit, String notes, long expectedRevision, Instant validFrom) {
        if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE) { throw new IllegalArgumentException("Revisao invalida."); }
        Objects.requireNonNull(validFrom, "Inicio de vigencia obrigatorio.");
        if (validFrom.isBefore(Instant.now())) { throw new IllegalArgumentException("A mudanca nao pode alterar o passado."); }
        MedicamentoEmUso previous = find(context, treatmentId, medicationId);
        if (previous.getRevision() != expectedRevision || !previous.isActive()) {
            throw new MedicationOperationConflictException("Medicamento alterado ou inativo.");
        }
        var updated = new MedicamentoEmUso(previous.getId(), name, dose, quantity, unit, notes,
            true, previous.getSchedules(), previous.getRevision());
        treatmentRules.validateUpdate(context, treatmentId, updated);
        if (Objects.equals(previous.getQuantity(), updated.getQuantity())
                && Objects.equals(previous.getUnit(), updated.getUnit())) {
            throw new IllegalArgumentException("Nao houve alteracao de quantidade ou unidade.");
        }
        if (schedules == null) { throw new MedicationOperationConflictException("Servico de cronogramas nao conectado."); }
        try {
            previous.validateForActivation();
            updated.validateForActivation();
            var current = previous.getActiveSchedule().orElseThrow(
                () -> new MedicationOperationConflictException("Medicamento sem cronograma ativo."));
            if (current.getValidFrom() == null || !validFrom.isAfter(current.getValidFrom())) {
                throw new MedicationOperationConflictException("Nova vigencia deve ser posterior ao inicio conhecido da versao atual.");
            }
            // Reutiliza o versionamento da #28 sem duplicar suas regras ou persistir separadamente.

            var history = schedules.prepararTransicao(
                    context, treatmentId, medicationId, current, validFrom);

            List<CronogramaDose> historicoComSnapshot = new ArrayList<>();

            for (CronogramaDose cronograma : history) {

                // A nova versão recebe a nova posologia.
                if (cronograma.isActive()
                        && cronograma.getVersion() > current.getVersion()
                        && Objects.equals(cronograma.getValidFrom(), validFrom)) {

                    cronograma = cronograma.withPosologySnapshot(
                            updated.getQuantity(),
                            updated.getUnit()
                    );

                } else if (cronograma.getVersion() == current.getVersion()
                        && !cronograma.hasPosologySnapshot()) {

                    // Preserva a posologia anterior antes da atualização.
                    cronograma = cronograma.withPosologySnapshot(
                            previous.getQuantity(),
                            previous.getUnit()
                    );
                }

                historicoComSnapshot.add(cronograma);
            }

            var prepared = new MedicamentoEmUso(updated.getId(), updated.getName(), updated.getDose(), updated.getQuantity(),
                updated.getUnit(), updated.getObservations(), true, historicoComSnapshot, expectedRevision);
            return new PreparedMedicationChange(previous, prepared, validFrom);
        } catch (MedicationOperationConflictException conflict) {
            throw conflict;
        } catch (IllegalStateException conflict) {
            throw new MedicationOperationConflictException("Historico nao permite preparar esta alteracao.");
        }
    }

    public MedicamentoEmUso saveVersionedUpdate(AuthContext context, String treatmentId, String medicationId,
            String name, String dose, Integer quantity, String unit, String notes, long expectedRevision, Instant validFrom) {
        var change = prepareVersionedUpdate(context, treatmentId, medicationId, name, dose, quantity, unit,
            notes, expectedRevision, validFrom);
        var updated = change.updated();
        if (!repository.updateWithSchedules(treatmentId, context.userId(), updated)) {
            find(context, treatmentId, medicationId);
            treatmentRules.validateUpdate(context, treatmentId, updated);
            throw new MedicationOperationConflictException("Medicamento alterado durante a gravacao conjunta.");
        }
        return updated.withRevision(expectedRevision + 1);
    }

    public MedicamentoEmUso add(AuthContext context, String treatmentId, String name, String dose,
            Integer quantity, String unit, String notes) {
        Objects.requireNonNull(context);
        var medication = new MedicamentoEmUso(null, name, dose, quantity, unit, notes, true, List.of());
        treatmentRules.validateAddition(context, treatmentId, medication);
        if (!repository.add(treatmentId, context.userId(), medication)) {
            // Revalida para distinguir encerramento concorrente de recurso inacessivel.
            treatmentRules.validateAddition(context, treatmentId, medication);
            throw new ResourceNotFoundException();
        }
        return medication;
    }

    public MedicamentoEmUso find(AuthContext context, String treatmentId, String medicationId) {
        String userId = Objects.requireNonNull(context).userId();
        return repository.find(treatmentId, userId, medicationId).orElseThrow(ResourceNotFoundException::new);
    }

    public MedicamentoEmUso update(AuthContext context, String treatmentId, String medicationId,
            String name, String dose, Integer quantity, String unit, String notes) {
        return updateInternal(context, treatmentId, medicationId, name, dose, quantity, unit, notes, null);
    }

    public MedicamentoEmUso update(AuthContext context, String treatmentId, String medicationId,
            String name, String dose, Integer quantity, String unit, String notes, long expectedRevision) {
        if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE) { throw new IllegalArgumentException("Revisao invalida."); }
        return updateInternal(context, treatmentId, medicationId, name, dose, quantity, unit, notes, expectedRevision);
    }

    private MedicamentoEmUso updateInternal(AuthContext context, String treatmentId, String medicationId,
            String name, String dose, Integer quantity, String unit, String notes, Long expectedRevision) {
        MedicamentoEmUso previous = find(context, treatmentId, medicationId);
        if (expectedRevision != null && previous.getRevision() != expectedRevision) {
            throw new MedicationOperationConflictException("Medicamento alterado. Consulte os dados novamente.");
        }
        if (!previous.isActive()) { throw new MedicationOperationConflictException("Medicamento inativo."); }
        var medication = new MedicamentoEmUso(previous.getId(), name, dose, quantity,
            unit, notes, previous.isActive(), previous.getSchedules(), previous.getRevision());
        // A mudanca com impacto no cronograma depende do versionamento da #28.
        if (!previous.getSchedules().isEmpty()
                && (!Objects.equals(previous.getQuantity(), medication.getQuantity())
                    || !Objects.equals(previous.getUnit(), medication.getUnit()))) {
            throw new MedicationOperationConflictException("Alteracao exige integracao com o versionamento do cronograma.");
        }
        treatmentRules.validateUpdate(context, treatmentId, medication);
        if (!repository.update(treatmentId, context.userId(), medication)) {
            find(context, treatmentId, medicationId);
            throw new MedicationOperationConflictException("Medicamento alterado durante a edicao.");
        }
        return medication.withRevision(previous.getRevision() + 1);
    }

    public void deactivate(AuthContext context, String treatmentId, String medicationId) {
        MedicamentoEmUso medication = find(context, treatmentId, medicationId);
        treatmentRules.validateDeactivation(context, treatmentId, medication);
        // Sem exclusao fisica: as referencias historicas continuam existentes.
        if (!medication.isActive()) { return; }
        int minimum = treatmentRules.minimumRemainingActiveMedications(context, treatmentId);
        if (!repository.deactivate(treatmentId, context.userId(), medicationId, medication.getRevision(), minimum)) {
            find(context, treatmentId, medicationId);
            throw new MedicationOperationConflictException("Ultimo medicamento ativo ou alteracao concorrente.");
        }
    }
}
