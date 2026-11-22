// Desenvolvido por Miguel Gallinucci.
package backend.services;

import backend.auth.AuthContext;
import backend.models.MedicamentoEmUso;

/**
 * Contrato de integracao com as regras de tratamento da issue #26.
 * Negacoes de negocio devem lancar MedicationOperationConflictException.
 */
public interface TreatmentMedicationRules {
    // Separei as regras do tratamento para a gestao de medicamentos nao duplicar o ciclo de vida da #26.
    void validateAddition(AuthContext context, String treatmentId, MedicamentoEmUso medication);
    void validateUpdate(AuthContext context, String treatmentId, MedicamentoEmUso medication);
    void validateDeactivation(AuthContext context, String treatmentId, MedicamentoEmUso medication);
    // Retorna 1 quando o tratamento precisa manter um medicamento ativo; 0 quando pode ficar vazio.
    int minimumRemainingActiveMedications(AuthContext context, String treatmentId);
}
