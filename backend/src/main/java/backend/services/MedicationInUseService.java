package backend.services;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.models.MedicamentoEmUso;
import backend.repositories.MedicationInUseRepository;
import java.util.List;
import java.util.Objects;

/** Operacoes de medicamentos vinculados ao titular autenticado. */
public final class MedicationInUseService {
    private final MedicationInUseRepository repository;
    private final TreatmentMedicationRules treatmentRules;

    public MedicationInUseService(MedicationInUseRepository repository, TreatmentMedicationRules treatmentRules) {
        this.repository = Objects.requireNonNull(repository);
        this.treatmentRules = Objects.requireNonNull(treatmentRules);
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
