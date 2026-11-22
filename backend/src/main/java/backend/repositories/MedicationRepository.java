//Caio Ferreira Polo issue 40
package backend.repositories;

import backend.models.Medication;
import java.util.List;
import java.util.Optional;

/**
 * Define as operações de persistência dos medicamentos.
 */
public interface MedicationRepository {
    List<Medication> findAll();

    default Optional<Medication> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (Medication medication : findAll()) {
            if (id.trim().equals(medication.getId())) {
                return Optional.of(medication);
            }
        }
        return Optional.empty();
    }
}
