package backend.services;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.models.MedicamentoEmUso;
import backend.repositories.MedicationInUseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Regras de cadastro e edicao dos medicamentos de um tratamento. */
public final class MedicationInUseService {
    private final MedicationInUseRepository repository;

    public MedicationInUseService(MedicationInUseRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public MedicamentoEmUso find(AuthContext context, String treatmentId, String medicationId) {
        return repository.find(treatmentId, Objects.requireNonNull(context).userId(), medicationId)
            .orElseThrow(ResourceNotFoundException::new);
    }

    public MedicamentoEmUso add(AuthContext context, String treatmentId, String name, String dose,
            BigDecimal quantity, String unit, String notes) {
        String status = editableStatus(context, treatmentId);
        var medication = new MedicamentoEmUso(null, name, dose, quantity, unit, notes, true, List.of());
        validate(status, medication);
        if (!repository.addInState(treatmentId, context.userId(), medication, status)) { throw conflict(); }
        return medication;
    }

    public MedicamentoEmUso update(AuthContext context, String treatmentId, String medicationId,
            String name, String dose, BigDecimal quantity, String unit, String notes) {
        String status = editableStatus(context, treatmentId);
        MedicamentoEmUso previous = find(context, treatmentId, medicationId);
        if (!previous.isActive()) { throw conflict(); }
        // Ate integrar a #28, nao altera quantidade/unidade de medicamento com cronograma.
        if (!previous.getSchedules().isEmpty()
                && (!sameQuantity(previous.getQuantity(), quantity) || !Objects.equals(previous.getUnit(), unit))) {
            throw new IllegalStateException("Alteracao exige versionamento do cronograma.");
        }
        var medication = new MedicamentoEmUso(previous.getId(), name, dose, quantity, unit, notes,
            previous.isActive(), previous.getSchedules());
        validate(status, medication);
        if (!repository.updateInState(treatmentId, context.userId(), medication, status)) { throw conflict(); }
        return medication;
    }

    public void deactivate(AuthContext context, String treatmentId, String medicationId) {
        String status = editableStatus(context, treatmentId);
        if (!find(context, treatmentId, medicationId).isActive()) { return; }
        // Preserva o documento mesmo em rascunho enquanto nao ha prova de ausencia de historico.
        if (!repository.deactivateInState(treatmentId, context.userId(), medicationId, status)) { throw conflict(); }
    }

    private String editableStatus(AuthContext context, String treatmentId) {
        String status = repository.findTreatmentStatus(treatmentId, Objects.requireNonNull(context).userId())
            .orElseThrow(ResourceNotFoundException::new);
        if (!status.equals("DRAFT") && !status.equals("ACTIVE")) { throw conflict(); }
        return status;
    }

    private void validate(String status, MedicamentoEmUso medication) {
        if (status.equals("ACTIVE")) { medication.validateForActivation(); }
    }

    private boolean sameQuantity(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private IllegalStateException conflict() {
        return new IllegalStateException("Operacao incompativel com o estado atual do tratamento ou medicamento.");
    }
}
