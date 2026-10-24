package backend.src.services;

import backend.src.models.Medication;
import backend.src.repositories.MedicationRepository;
import java.util.List;
import java.util.Objects;

/**
 * Executa os casos de uso relacionados aos medicamentos.
 */
public class MedicationService {
    private final MedicationRepository repository;

    public MedicationService(MedicationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository não pode ser null");
    }

    public List<Medication> listMedications() {
        return repository.findAll();
    }
}
