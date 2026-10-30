package backend.src.repositories;

import backend.src.models.Treatment;
import java.util.List;
import java.util.Optional;

/**
 * Define as operações necessárias para persistir e recuperar tratamentos,
 * preservando a associação com o titular (usuário) e o histórico de cronogramas.
 */
public interface TreatmentRepository {

    /**
     * Persiste um novo tratamento associado a um usuário.
     */
    Treatment save(Treatment treatment);

    /**
     * Recupera um tratamento pelo seu identificador único.
     */
    Optional<Treatment> findById(String id);

    /**
     * Lista todos os tratamentos pertencentes a um usuário específico (titular).
     */
    List<Treatment> findByUserId(String userId);

    /**
     * Atualiza um tratamento existente preservando medicamentos em uso
     * e versões históricas dos cronogramas.
     */
    Treatment update(Treatment treatment);

    /**
     * Recupera um tratamento pelo ID filtrando também pelo identificador do titular.
     */
    default Optional<Treatment> findByIdAndUserId(String id, String userId) {
        if (id == null || userId == null) {
            return Optional.empty();
        }
        return findById(id).filter(treatment -> userId.equals(treatment.getUserId()));
    }
}

