package backend.services;

import backend.auth.AuthContext;
import backend.models.MedicamentoEmUso;

/** Contrato de integracao com as regras de tratamento da issue #26. */
public interface TreatmentMedicationRules {
    void validateAddition(AuthContext context, String treatmentId, MedicamentoEmUso medication);
    void validateUpdate(AuthContext context, String treatmentId, MedicamentoEmUso medication);
    void validateDeactivation(AuthContext context, String treatmentId, MedicamentoEmUso medication);
}
