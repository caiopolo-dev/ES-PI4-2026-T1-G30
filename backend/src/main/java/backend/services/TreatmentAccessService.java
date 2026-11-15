package backend.services;

import backend.auth.AuthContext;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.repositories.TreatmentRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Restringe as operacoes de tratamentos ao titular autenticado. */
public final class TreatmentAccessService {
    private final TreatmentRepository repository;

    public TreatmentAccessService(TreatmentRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<Treatment> list(AuthContext context) {
        String userId = Objects.requireNonNull(context).userId();
        return repository.findByUserId(userId).stream()
            .filter(treatment -> userId.equals(treatment.getUserId()))
            .toList();
    }

    public Treatment find(AuthContext context, String treatmentId) {
        String userId = Objects.requireNonNull(context).userId();
        // Usa a mesma resposta para recurso inexistente ou de outro titular.
        return repository.findByIdAndUserId(treatmentId, userId)
            .filter(treatment -> userId.equals(treatment.getUserId()))
            .orElseThrow(() -> new NoSuchElementException("Recurso nao encontrado."));
    }

    public MedicamentoEmUso findMedication(AuthContext context, String treatmentId, String medicationId) {
        return find(context, treatmentId).findMedicationById(medicationId)
            .orElseThrow(() -> new NoSuchElementException("Recurso nao encontrado."));
    }

    public Treatment update(AuthContext context, Treatment updated) {
        Objects.requireNonNull(updated);
        find(context, updated.getId());
        // O objeto recebido nao pode trocar o titular do tratamento.
        if (!context.userId().equals(updated.getUserId())) {
            throw new NoSuchElementException("Recurso nao encontrado.");
        }
        return repository.update(updated);
    }
}
