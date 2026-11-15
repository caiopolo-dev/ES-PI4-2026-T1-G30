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

    // Devolve somente registros vinculados as doses e ao titular solicitado.
    default Map<String, RegistroDeDose> findByPlannedDoseIdsAndUserId(
            Collection<String> plannedDoseIds, String userId) {
        Map<String, RegistroDeDose> result = new java.util.LinkedHashMap<>();
        if (userId == null || userId.isBlank() || plannedDoseIds == null || plannedDoseIds.isEmpty()) {
            return result;
        }
        Map<String, RegistroDeDose> records = findByPlannedDoseIds(plannedDoseIds);
        if (records == null) {
            return result;
        }
        for (var entry : records.entrySet()) {
            RegistroDeDose record = entry.getValue();
            if (record != null && plannedDoseIds.contains(entry.getKey())
                    && entry.getKey().equals(record.getPlannedDoseId())
                    && userId.equals(record.getUserId())) {
                result.put(entry.getKey(), record);
            }
        }
        return result;
    }
}
