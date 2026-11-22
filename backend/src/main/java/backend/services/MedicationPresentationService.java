package backend.services;

import backend.models.MedicationPresentation;
import backend.repositories.MedicationPresentationRepository;
import java.util.List;
import java.util.Objects;

/**
 * Executa a busca limitada no catálogo experimental de apresentações.
 */
public final class MedicationPresentationService {
    private static final int MAX_RESULTS = 20;

    private final MedicationPresentationRepository repository;

    public MedicationPresentationService(MedicationPresentationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<MedicationPresentation> search(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new IllegalArgumentException("Busca deve possuir entre 2 e 100 caracteres.");
        }
        return repository.search(normalized, MAX_RESULTS);
    }
}
