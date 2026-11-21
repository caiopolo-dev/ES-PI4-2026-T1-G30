package backend.repositories;

import backend.models.MedicamentoEmUso;
import java.util.Optional;

/** Acesso aos medicamentos embutidos no tratamento. */
public interface MedicationInUseRepository {
    boolean add(String treatmentId, String userId, MedicamentoEmUso medication);
    Optional<MedicamentoEmUso> find(String treatmentId, String userId, String medicationId);
    boolean update(String treatmentId, String userId, MedicamentoEmUso medication);
    // Grava os dados do medicamento e as versoes preparadas no mesmo comando.
    default boolean updateWithSchedules(String treatmentId, String userId, MedicamentoEmUso medication) {
        throw new UnsupportedOperationException("Atualizacao conjunta nao suportada.");
    }
    boolean deactivate(String treatmentId, String userId, String medicationId, long revision, int minimumRemainingActive);
}
