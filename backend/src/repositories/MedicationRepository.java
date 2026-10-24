package backend.src.repositories;

import backend.src.models.Medication;
import java.util.List;

/**
 * Define as operações de persistência dos medicamentos.
 */
public interface MedicationRepository {
    List<Medication> findAll();
}
