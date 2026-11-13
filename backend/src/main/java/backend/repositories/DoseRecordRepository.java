package backend.repositories;

import backend.models.RegistroDeDose;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Contrato de persistência para registros de realização de doses (collection dose_records).
 */
public interface DoseRecordRepository {

    /**
     * Persiste um registro explícito de ação do usuário sobre uma DosePrevista.
     */
    RegistroDeDose save(RegistroDeDose registroDeDose);

    /**
     * Busca o registro associado a uma DosePrevista específica.
     */
    Optional<RegistroDeDose> findByPlannedDoseId(String plannedDoseId);

    /**
     * Carrega em lote os registros associados a um conjunto de IDs de DosePrevista,
     * retornando um mapa indexado por plannedDoseId.
     */
    Map<String, RegistroDeDose> findByPlannedDoseIds(Collection<String> plannedDoseIds);

    /**
     * Retorna a quantidade total de registros armazenados (útil para verificação de ausência de efeitos colaterais).
     */
    int countAll();
}
