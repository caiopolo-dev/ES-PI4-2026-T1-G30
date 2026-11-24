// Desenvolvido por Miguel Gallinucci.
package backend.services;

import backend.auth.AuthContext;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.repositories.TreatmentRepository;
import java.util.List;
import backend.auth.ResourceNotFoundException;
import java.util.Objects;

/** Restringe as operacoes de tratamentos ao titular autenticado. */
public final class TreatmentAccessService {
    private final TreatmentRepository repository;

    public TreatmentAccessService(TreatmentRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<Treatment> list(AuthContext context) {
        // Peguei o titular do contexto e conferi o retorno para nao listar tratamentos de outra pessoa.
        String userId = Objects.requireNonNull(context).userId();

        return repository.findByUserId(userId).stream()

            // stream percorre os resultados; filter mantem apenas os tratamentos que pertencem ao titular.
            .filter(treatment -> userId.equals(treatment.getUserId()))
            .toList();
    }

    // Recebe identidade e ID do tratamento; devolve o recurso autorizado ou lanca a excecao que vira 404.
    public Treatment find(AuthContext context, String treatmentId) {
        String userId = Objects.requireNonNull(context).userId();

        // Usa a mesma resposta para recurso inexistente ou de outro titular.
        // orElseThrow transforma a ausencia de resultado autorizado na excecao tratada pelo Router.
        return repository.findByIdAndUserId(treatmentId, userId)
            .filter(treatment -> userId.equals(treatment.getUserId()))
            .orElseThrow(() -> new ResourceNotFoundException());
    }

    public MedicamentoEmUso findMedication(AuthContext context, String treatmentId, String medicationId) {
        // Primeiro autorizei o tratamento; depois procurei o medicamento dentro dele para preservar o vinculo.
        return find(context, treatmentId).findMedicationById(medicationId)
            .orElseThrow(() -> new ResourceNotFoundException());
    }

    public Treatment update(AuthContext context, Treatment updated) {
        Objects.requireNonNull(updated);

        // Conferi o tratamento salvo antes de aceitar os dados recebidos para atualizacao.
        find(context, updated.getId());

        // O objeto recebido nao pode trocar o titular do tratamento.
        if (!context.userId().equals(updated.getUserId())) {
            throw new ResourceNotFoundException();
        }

        return repository.update(updated);
    }
}
