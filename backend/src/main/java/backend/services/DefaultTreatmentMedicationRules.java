// Desenvolvido por Miguel Gallinucci.
package backend.services;

import backend.auth.AuthContext;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import java.util.Objects;

/** Aplica as regras de medicamentos usando o tratamento do titular. */
public final class DefaultTreatmentMedicationRules implements TreatmentMedicationRules {
    private final TreatmentService treatments;

    public DefaultTreatmentMedicationRules(TreatmentService treatments) {
        // Reutilizei o servico de tratamentos para manter a mesma verificacao de titularidade da #26.
        this.treatments = Objects.requireNonNull(treatments);
    }

    @Override
    public void validateAddition(AuthContext context, String treatmentId, MedicamentoEmUso medication) {
        validateMedication(editable(context, treatmentId), medication);
    }

    @Override
    public void validateUpdate(AuthContext context, String treatmentId, MedicamentoEmUso medication) {
        validateMedication(editable(context, treatmentId), medication);
    }

    @Override
    // O servico de medicamentos chama antes do DELETE: ACTIVE precisa continuar com medicamento e cronograma valido.
    // Por isso, retirar o ultimo medicamento valido e bloqueado; encerrar o tratamento e outro fluxo.
    public void validateDeactivation(AuthContext context, String treatmentId, MedicamentoEmUso medication) {
        Treatment treatment = editable(context, treatmentId);

        if (treatment.getStatus() == TreatmentStatus.ACTIVE && medication.isActive()) {
            var remaining = treatment.getMedications().stream()
                .filter(m -> m.isActive() && !m.getId().equals(medication.getId())).toList();

            try {
                // Simulei o tratamento sem esse medicamento para validar a desativacao antes de gravar.
                // Reutiliza a validacao existente de tratamento ativo, sem alterar o original.
                new Treatment(treatment.getId(), treatment.getUserId(), treatment.getName(),
                    treatment.getDescription(), treatment.getStartDate(), treatment.getEndDate(),
                    treatment.getStatus(), treatment.getClosedAt(), remaining);
            } catch (IllegalArgumentException invalid) {
                throw new MedicationOperationConflictException("O tratamento precisa manter medicamento com cronograma valido.");
            }
        }
    }

    @Override
    public int minimumRemainingActiveMedications(AuthContext context, String treatmentId) {
        // ACTIVE precisa manter medicamento ativo; DRAFT pode ficar vazio enquanto e preparado.
        return editable(context, treatmentId).getStatus() == TreatmentStatus.ACTIVE ? 1 : 0;
    }

    private Treatment editable(AuthContext context, String treatmentId) {
        // O encerramento preserva consultas, mas impede novas alteracoes nos medicamentos.
        Treatment treatment = treatments.find(context, treatmentId);

        if (treatment.getStatus() == TreatmentStatus.CLOSED) {
            throw new MedicationOperationConflictException("Tratamento encerrado nao permite alteracoes.");
        }

        return treatment;
    }

    private void validateMedication(Treatment treatment, MedicamentoEmUso medication) {
        // O modelo atual exige nome e dose; quantidade e unidade podem faltar no rascunho.
        if (treatment.getStatus() == TreatmentStatus.ACTIVE) {
            try {
                medication.validateForActivation();
            } catch (IllegalStateException invalid) {
                throw new MedicationOperationConflictException("Medicamento incompleto para tratamento ativo.");
            }
        }
    }
}
