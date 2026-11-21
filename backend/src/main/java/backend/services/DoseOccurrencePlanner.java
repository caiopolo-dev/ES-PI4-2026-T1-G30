// Autor: Samuel Campovilla
package backend.services;

import backend.models.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.zone.ZoneRules;
import java.util.*;

// Responsável por transformar os cronogramas de um tratamento
// em ocorrências concretas de DosePrevista.
// Não realiza operações no banco de dados.

public class DoseOccurrencePlanner {

    public List<DosePrevista> planOccurrences(
            Treatment treatment,
            Instant startInclusive,
            Instant endExclusive) {

        if (treatment == null || startInclusive == null || endExclusive == null) {
            throw new IllegalArgumentException("Tratamento e intervalo não podem ser nulos");
        }

        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("Intervalo de geração inválido");
        }

        List<DosePrevista> doses = new ArrayList<>();

        // Somente tratamentos ativos podem gerar novas ocorrências.
        if (treatment.getStatus() != TreatmentStatus.ACTIVE) {
            return doses;
        }

        for (MedicamentoEmUso medicamento : treatment.getMedications()) {

            // Medicamentos desativados não devem gerar doses.
            if (!medicamento.isActive()) {
                continue;
            }

            // Obtém a versão ativa do cronograma, caso exista.
            Optional<CronogramaDose> cronogramaOpt = medicamento.getActiveSchedule();

            if (cronogramaOpt.isEmpty()) {
                continue;
            }

            CronogramaDose cronograma = cronogramaOpt.get();

            // Cada medicamento possui sua própria regra e seu próprio fuso.
            doses.addAll(gerarDosesMedicamento(
                    treatment,
                    medicamento,
                    cronograma,
                    startInclusive,
                    endExclusive
            ));
        }

        // Ordena as ocorrências pelo instante previsto.
        // O ID funciona como critério de desempate.
        doses.sort(
                Comparator.comparing(DosePrevista::getScheduledAt)
                        .thenComparing(DosePrevista::getId)
        );

        return doses;
    }

    private List<DosePrevista> gerarDosesMedicamento(
            Treatment treatment,
            MedicamentoEmUso medicamento,
            CronogramaDose cronograma,
            Instant startInclusive,
            Instant endExclusive) {

        List<DosePrevista> doses = new ArrayList<>();

        ZoneId fuso = ZoneId.of(cronograma.getTimezone());
        ZoneRules regrasFuso = fuso.getRules();

        LocalDate inicioCronograma = LocalDate.parse(cronograma.getStartDate());

        LocalDate fimCronograma = cronograma.getEndDate() == null ? null : LocalDate.parse(cronograma.getEndDate());

        // Converte os limites UTC para datas civis do fuso do cronograma.
        LocalDate inicio = startInclusive.atZone(fuso).toLocalDate();
        LocalDate fim = endExclusive.atZone(fuso).toLocalDate();

        // Calcula a interseção entre o período solicitado,
        // a vigência do tratamento e a vigência do cronograma.
        if (treatment.getStartDate() != null && treatment.getStartDate().isAfter(inicio)) {
            inicio = treatment.getStartDate();
        }

        if (inicioCronograma.isAfter(inicio)) {
            inicio = inicioCronograma;
        }

        if (treatment.getEndDate() != null && treatment.getEndDate().isBefore(fim)) {
            fim = treatment.getEndDate();
        }

        if (fimCronograma != null && fimCronograma.isBefore(fim)) {
            fim = fimCronograma;
        }

        if (inicio.isAfter(fim)) {
            return doses;
        }

        // Obtém os dias e horários da regra.
        Set<DayOfWeek> diasSemana = obterDiasSemana(cronograma);
        Set<LocalTime> horarios = obterHorarios(cronograma);

        Integer quantidade = medicamento.getQuantity();

        if (quantidade == null || quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade do medicamento inválida");
        }

        BigDecimal quantidadePrevista = BigDecimal.valueOf(quantidade);

        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {

            // Uma lista null significa que a recorrência é diária.
            if (diasSemana != null && !diasSemana.contains(data.getDayOfWeek())) {
                continue;
            }

            for (LocalTime horario : horarios) {

                LocalDateTime dataHora = LocalDateTime.of(data, horario);

                // Consulta os offsets válidos para aquele horário local.
                // Durante uma mudança de horário de verão, podem existir
                // zero, um ou dois offsets para a mesma hora.
                List<ZoneOffset> offsets = regrasFuso.getValidOffsets(dataHora);

                // Horários inexistentes durante a transição não geram doses.
                if (offsets.isEmpty()) {
                    continue;
                }

                // Se o horário for ambíguo, utiliza o primeiro offset válido.
                Instant instante = dataHora.toInstant(offsets.get(0));

                // O início do intervalo é inclusivo e o final é exclusivo.
                if (instante.isBefore(startInclusive) || !instante.isBefore(endExclusive)) {
                    continue;
                }

                String id = PlannedDoseIdentity.generateId(
                        treatment.getUserId(),
                        treatment.getId(),
                        medicamento.getId(),
                        cronograma.getVersion(),
                        instante
                );

                DosePrevista dose = new DosePrevista(
                        id,
                        treatment.getUserId(),
                        treatment.getId(),
                        medicamento.getId(),
                        medicamento.getName(),
                        cronograma.getVersion(),
                        instante,
                        cronograma.getTimezone(),
                        quantidadePrevista,
                        ScheduleStatus.SCHEDULED
                );

                doses.add(dose);
            }
        }

        return doses;
    }

    // Converte os dias informados no cronograma para DayOfWeek.
    // null representa recorrência diária.
    private Set<DayOfWeek> obterDiasSemana(CronogramaDose cronograma) {

        if (cronograma.getDaysOfWeek() == null) {
            return null;
        }

        if (cronograma.getDaysOfWeek().isEmpty()) {
            throw new IllegalArgumentException("A lista de dias da semana não pode ser vazia");
        }

        Set<DayOfWeek> dias = new HashSet<>();

        for (String dia : cronograma.getDaysOfWeek()) {
            dias.add(DayOfWeek.valueOf(dia));
        }

        return dias;
    }

    // Converte os horários do cronograma e evita repetições.
    private Set<LocalTime> obterHorarios(CronogramaDose cronograma) {

        if (cronograma.getTimes() == null || cronograma.getTimes().isEmpty()) {
            throw new IllegalArgumentException("Cronograma sem horários válidos");
        }

        Set<LocalTime> horarios = new TreeSet<>();

        for (String horario : cronograma.getTimes()) {

            if (horario == null || !horario.matches("\\d{2}:\\d{2}")) {
                throw new IllegalArgumentException("Formato de horário inválido");
            }

            horarios.add(LocalTime.parse(horario));
        }

        return horarios;
    }
}
