package backend.repositories;

import backend.models.DosePrevista;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistência para ocorrências de DosePrevista (collection planned_doses).
 */
public interface PlannedDoseRepository {

    /**
     * Persiste uma ocorrência de dose prevista de maneira idempotente.
     */
    DosePrevista save(DosePrevista dosePrevista);

    /**
     * Busca uma dose prevista pelo seu identificador único.
     */
    Optional<DosePrevista> findById(String id);

    /**
     * Consulta as doses previstas de um usuário específico dentro do intervalo UTC [startInclusive, endExclusive).
     */
    List<DosePrevista> findByUserAndPeriod(String userId, Instant startInclusive, Instant endExclusive);

    /**
     * Retorna a quantidade total de documentos armazenados (útil para verificação de ausência de efeitos colaterais).
     */
    int countAll();
}
