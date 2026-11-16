package backend.repositories;

import backend.models.MedicamentoEmUso;
import java.util.Optional;

/** Operacoes pontuais no array medications do tratamento existente. */
public interface MedicationInUseRepository {
    Optional<String> findTreatmentStatus(String treatmentId, String userId);
    boolean addInState(String treatmentId, String userId, MedicamentoEmUso medication, String status);
    boolean updateInState(String treatmentId, String userId, MedicamentoEmUso medication, String status);
    boolean deactivateInState(String treatmentId, String userId, String medicationId, String status);
    Optional<MedicamentoEmUso> find(String treatmentId, String userId, String medicationId);
    boolean add(String treatmentId, String userId, MedicamentoEmUso medication);
    boolean update(String treatmentId, String userId, MedicamentoEmUso medication);
}
