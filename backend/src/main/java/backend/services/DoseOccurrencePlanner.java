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

            // Percorre as versões do cronograma que podem gerar ocorrências.
            // Uma versão inativa ainda pode possuir vigência futura.
            for (CronogramaDose cronograma : obterCronogramasElegiveis(medicamento)) {

                doses.addAll(gerarDosesMedicamento(
                        treatment,
                        medicamento,
                        cronograma,
                        startInclusive,
                        endExclusive
                ));
            }

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


        // Restringe o intervalo solicitado à vigência da versão do cronograma.
        Instant validFrom = cronograma.getValidFrom();
        Instant validTo = cronograma.getValidTo();

        if (validFrom != null && validFrom.isAfter(startInclusive)) {
            startInclusive = validFrom;
        }

        if (validTo != null && validTo.isBefore(endExclusive)) {
            endExclusive = validTo;
        }

        // A versão do cronograma pode não possuir vigência
        // dentro do intervalo solicitado.
        if (!startInclusive.isBefore(endExclusive)) {
            return doses;
        }


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

        Integer quantidade = obterQuantidadePrevista(medicamento, cronograma);

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


    // Recupera a quantidade correspondente à versão
    // do cronograma responsável pela ocorrência.
    private Integer obterQuantidadePrevista(
            MedicamentoEmUso medicamento,
            CronogramaDose cronograma) {

        // O snapshot possui prioridade sobre os dados atuais.
        if (cronograma.hasPosologySnapshot()) {
            return cronograma.getQuantitySnapshot();
        }

        // Compatibilidade com versões abertas sem snapshot.
        if (cronograma.isActive()) {
            return medicamento.getQuantity();
        }

        // Não utiliza a quantidade atual para reconstruir
        // ocorrências de versões históricas desconhecidas.
        throw new IllegalStateException(
                "Não é possível determinar a quantidade da versão histórica "
                        + cronograma.getVersion()
        );
    }


    // Obtém os dias da semana de acordo com a frequência do cronograma.
    // null representa uma recorrência diária, sem restrição de dias.
    private Set<DayOfWeek> obterDiasSemana(CronogramaDose cronograma) {

        return switch (cronograma.getFrequency()) {

            case EVERY_DAY -> null;

            case DAYS_OF_WEEK -> {

                if (cronograma.getDaysOfWeek() == null
                        || cronograma.getDaysOfWeek().isEmpty()) {
                    throw new IllegalArgumentException("Cronograma semanal sem dias definidos");
                }

                Set<DayOfWeek> dias = new HashSet<>();

                for (String dia : cronograma.getDaysOfWeek()) {
                    dias.add(DayOfWeek.valueOf(dia));
                }

                yield dias;
            }
        };
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


    // Recupera as versões que possuem informações suficientes
    // para determinar quando suas ocorrências devem ser geradas.
    private List<CronogramaDose> obterCronogramasElegiveis(MedicamentoEmUso medicamento) {

        List<CronogramaDose> cronogramas = medicamento.getSchedules();
        List<CronogramaDose> elegiveis = new ArrayList<>();
        Set<Integer> versoes = new HashSet<>();

        int quantidadeAtivas = 0;

        boolean possuiVigencia = false;
        boolean possuiLegado = false;

        for (CronogramaDose cronograma : cronogramas) {

            // Cada versão deve possuir uma identidade única no histórico.
            if (!versoes.add(cronograma.getVersion())) {
                throw new IllegalStateException("Versão de cronograma duplicada");
            }

            if (cronograma.isActive()) {
                quantidadeAtivas++;
            }

            if (cronograma.getValidFrom() == null) {
                possuiLegado = true;
            } else {
                possuiVigencia = true;
            }
        }

        if (quantidadeAtivas > 1) {
            throw new IllegalStateException("Medicamento possui mais de um cronograma ativo");
        }

        // Não é possível determinar com segurança a vigência das versões
        // quando o histórico mistura versões antigas e versionadas.
        if (possuiLegado && possuiVigencia) {
            throw new IllegalStateException("Histórico possui versões sem vigência definida");
        }

        // Mantém a compatibilidade com cronogramas antigos.
        // Nesse caso, somente a versão ativa poderá gerar ocorrências.
        if (!possuiVigencia) {

            for (CronogramaDose cronograma : cronogramas) {

                if (cronograma.isActive()) {
                    elegiveis.add(cronograma);
                }
            }

            return elegiveis;
        }

        // Cronogramas versionados são avaliados pela vigência,
        // independentemente do valor do atributo active.
        elegiveis.addAll(cronogramas);

        elegiveis.sort(Comparator.comparing(CronogramaDose::getValidFrom));

        // Verifica se existem versões com períodos de vigência sobrepostos.
        for (int i = 0; i < elegiveis.size(); i++) {

            CronogramaDose atual = elegiveis.get(i);

            if (atual.isActive() && atual.getValidTo() != null) {
                throw new IllegalStateException("Cronograma ativo possui término de vigência");
            }

            if (!atual.isActive() && atual.getValidTo() == null) {
                throw new IllegalStateException("Cronograma inativo possui vigência aberta");
            }

            if (i + 1 < elegiveis.size()) {

                CronogramaDose proximo = elegiveis.get(i + 1);

                if (atual.getValidTo() == null
                        || atual.getValidTo().isAfter(proximo.getValidFrom())) {
                    throw new IllegalStateException("Cronogramas possuem vigências sobrepostas");
                }
            }
        }

        return elegiveis;
    }
}
