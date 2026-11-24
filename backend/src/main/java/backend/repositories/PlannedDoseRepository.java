// Autor: Samuel Campovilla
package backend.repositories;

import backend.models.DosePrevista;

import java.time.Instant;
import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

public interface PlannedDoseRepository {
    // Salvar uma única dose
    DosePrevista save(DosePrevista dose);
    // Salvar diversas doses recebendo um array de doses (Lista<>)
    List<DosePrevista> saveAll(List<DosePrevista> doses);

    // ! Importante: O Optional funciona como um container que pode conter uma DosePrevista ou estar vazio
    // Oracle docs: A container object which may or may not contain a non-null value
    Optional<DosePrevista> findById(String id);

    // Se um usuário possui três doses previstas em um determinado dia,
    // o método deve retornar três objetos DosePrevista
    List<DosePrevista> findByUserAndDate(String user, LocalDate data);
    // LocalDate, orecle docs: date-time object that represents a date,
    // often viewed as year-month-day. Other date fields, such as day-of-year,
    // day-of-week and week-of-year, can also be accessed

    //O método retorna as "DosePrevista" dos medicamentos de um tratamento que estejam dentro do período solicitado
    List<DosePrevista> findByTreatmentAndPeriod(String treatmentId, Instant inicio, Instant fim);

    // Busca as doses de um usuário dentro de um período
    List<DosePrevista> findByUserAndPeriod(
            String userId, Instant inicio, Instant fim);

    // Retorna a quantidade de doses armazenadas
    int countAll();

    // OOs métodos abaixo retornam long porque retornarão a quantidade de documentos efetivamente modificados
    // O instante inicio representa o limite a partir do qual as ocorrências serão canceladas

    // Cancela as doses futuras de um tratamento encerrado.
    long cancelScheduledForTreatmentFrom(String userId, String treatmentId, Instant inicio);

    // Cancela as doses futuras de uma versão específica do cronograma.
    long cancelScheduledForMedicationVersionFrom(String userId, String treatmentId, String medicationInUseId, int scheduleVersion, Instant inicio);
}
