package backend.repositories;

import backend.models.Medication;
import java.util.List;

/**
 * Define as operações de persistência dos medicamentos.
 */
public interface MedicationRepository {
    List<Medication> findAll();
}
