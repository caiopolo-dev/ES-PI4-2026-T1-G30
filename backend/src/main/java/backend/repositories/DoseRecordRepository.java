package backend.repositories;

import backend.models.RegistroDeDose;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DoseRecordRepository {
    // Salva uma ação do usuário sobre uma dose prevista
    RegistroDeDose save(RegistroDeDose registro);

    // Busca o registro associado a uma dose prevista
    Optional<RegistroDeDose> findByPlannedDoseId(String plannedDoseId);

    // Busca os registros realizados dentro de um intervalo de tempo
    List<RegistroDeDose> findByPeriod(Instant inicio, Instant fim);
}
