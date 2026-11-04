package backend.services;

import backend.models.Medication;
import backend.repositories.MedicationRepository;
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
