package backend.repositories;

import backend.models.RegistroDeDose;

import java.time.Instant;
import java.util.*;

public interface DoseRecordRepository {
    // Salva uma ação do usuário sobre uma dose prevista
    RegistroDeDose save(RegistroDeDose registro);

    // Busca o registro associado a uma dose prevista
    Optional<RegistroDeDose> findByPlannedDoseId(String plannedDoseId);

    // Busca os registros realizados dentro de um intervalo de tempo
    List<RegistroDeDose> findByPeriod(Instant inicio, Instant fim);

    // Busca vários registros de uma só vez, utilizando os IDs das doses previstas
    Map<String, RegistroDeDose> findByPlannedDoseIds(Collection<String> plannedDoseIds);

    // Retorna a quantidade de registros armazenados
    int countAll();

    // default permite implementar um método diretamente na interface.
    // Assim, as classes que implementam DoseRecordRepository herdam este método
    // automaticamente, mas podem sobrescrevê-lo se necessário.
    // Estou utilizando para manter o contrato que foi previsto por outras classes que chamam este método
    default Map<String, RegistroDeDose> findByPlannedDoseIdsAndUserId(Collection<String> plannedDoseIds, String userId) {
        // Var permite ao Java inferir o tipo
        var resultado = new HashMap<String, RegistroDeDose>();

        if (plannedDoseIds == null || plannedDoseIds.isEmpty()
                || userId == null || userId.isBlank()) {
            return resultado;
        }

        // Busca todos os registros de uma só vez
        var registros = findByPlannedDoseIds(plannedDoseIds);

        // Percorre os IDs solicitados e mantém apenas os registros do usuário
        for (String id : plannedDoseIds) {
            if (id == null) {
                continue;
            }

            RegistroDeDose registro = registros.get(id);

            if (registro != null
                    && id.equals(registro.getPlannedDoseId())
                    && userId.equals(registro.getUserId())) {
                resultado.put(id, registro);
            }
        }


        return resultado;
    }
}
