
package backend.models;

import java.time.DayOfWeek;
import java.time.Instant; // issue 28 - Representa um momento exato da vigência.
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Representa uma configuração versionada de horários
 * de administração de um medicamento em uso.
 */
public class CronogramaDose {
    // issue 28
    private final int version;
    private final ScheduleFrequency frequency;
    private final List<String> daysOfWeek;

    // issue 28
    // Instant representa um momento exato no tempo, em UTC.
    // validFrom indica quando a versão começa a valer; validTo indica quando termina (null se não definido).
    private final Instant validFrom;
    private final Instant validTo;

    private final List<String> times;
    private final String startDate;
    private final String endDate;
    private final String timezone;
    private final boolean active;

    // Novo construtor: recebe a frequência explicitamente.
    // issue 28 - Mantém a assinatura antiga e encaminha a validação ao construtor com vigência.
    public CronogramaDose(
            int version,
            ScheduleFrequency frequency,
            List<String> daysOfWeek,
            List<String> times,
            String startDate,
            String endDate,
            String timezone,
            boolean active) {

        this(version, frequency, daysOfWeek, times,
                startDate, endDate, timezone, active, null, null);
    }

    // issue 28 - Novo construtor que recebe a vigência e mantém as validações existentes.
    public CronogramaDose(
            int version,
            ScheduleFrequency frequency,
            List<String> daysOfWeek,
            List<String> times,
            String startDate,
            String endDate,
            String timezone,
            boolean active,
            Instant validFrom,
            Instant validTo) {

        if (version < 1) {
            throw new IllegalArgumentException(
                    "version deve ser maior ou igual a 1");
        }

        this.frequency = Objects.requireNonNull(
                frequency, "frequency não pode ser null");

        this.daysOfWeek = validateDays(frequency, daysOfWeek);
        this.times = validateTimes(times);

        LocalDate start = parseDate(startDate, "startDate");
        LocalDate end = endDate == null || endDate.isBlank()
                ? null
                : parseDate(endDate, "endDate");

        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "endDate não pode ser anterior a startDate");
        }

        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException(
                    "timezone não pode ser vazio");
        }

        String zone = timezone.trim();

        try {
            ZoneId.of(zone);
        } catch (DateTimeException error) {
            throw new IllegalArgumentException(
                    "timezone inválido: " + zone, error);
        }

        this.version = version;
        this.startDate = start.toString();
        this.endDate = end == null ? null : end.toString();
        this.timezone = zone;
        this.active = active;

        // issue 28 - Impede uma data de término sem início e exige fim posterior ao início.
        if (validFrom == null && validTo != null) {
            throw new IllegalArgumentException("validTo exige validFrom");
        }

        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo deve ser posterior a validFrom");
        }

        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    // Construtor antigo: preservado para compatibilidade.
    public CronogramaDose(
            int version,
            List<String> daysOfWeek,
            List<String> times,
            String startDate,
            String endDate,
            String timezone,
            boolean active) {

        // issue 28
        this(version, inferFrequency(daysOfWeek), daysOfWeek,
                times, startDate, endDate, timezone, active);
    }

    // Construtor de conveniência para recorrência diária.
    public CronogramaDose(
            List<String> times,
            String startDate,
            String timezone) {

        this(1, ScheduleFrequency.EVERY_DAY, null,
                times, startDate, null, timezone, true);
    }

    // Construtor antigo com dias da semana.
    public CronogramaDose(
            List<String> daysOfWeek,
            List<String> times,
            String startDate,
            String endDate,
            String timezone) {

        this(1, daysOfWeek, times, startDate,
                endDate, timezone, true);
    }

    // issue 28
    private static ScheduleFrequency inferFrequency(List<String> days) {
        return days == null || days.isEmpty()
                ? ScheduleFrequency.EVERY_DAY
                : ScheduleFrequency.DAYS_OF_WEEK;
    }

    // issue 28
    private static List<String> validateDays(
            ScheduleFrequency frequency,
            List<String> days) {

        if (frequency == ScheduleFrequency.EVERY_DAY) {
            if (days != null && !days.isEmpty()) {
                throw new IllegalArgumentException(
                        "EVERY_DAY não aceita daysOfWeek");
            }
            return null;
        }

        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException(
                    "DAYS_OF_WEEK exige ao menos um dia");
        }

        Set<String> unique = new HashSet<>();
        List<String> copy = new ArrayList<>();

        for (String day : days) {
            if (day == null) {
                throw new IllegalArgumentException(
                        "daysOfWeek não aceita null");
            }

            try {
                DayOfWeek.valueOf(day);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(
                        "Dia da semana inválido: " + day, error);
            }

            if (!unique.add(day)) {
                throw new IllegalArgumentException(
                        "Dia da semana duplicado: " + day);
            }

            copy.add(day);
        }

        return Collections.unmodifiableList(copy);
    }

    // issue 28
    private static List<String> validateTimes(List<String> times) {
        if (times == null || times.isEmpty()) {
            throw new IllegalArgumentException(
                    "times deve conter ao menos um horário");
        }

        Set<String> unique = new HashSet<>();
        List<String> copy = new ArrayList<>();

        for (String time : times) {
            if (time == null ||
                    !time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) {
                throw new IllegalArgumentException(
                        "Horário inválido (HH:mm): " + time);
            }

            LocalTime.parse(
                    time, DateTimeFormatter.ofPattern("HH:mm"));

            if (!unique.add(time)) {
                throw new IllegalArgumentException(
                        "Horário duplicado: " + time);
            }

            copy.add(time);
        }

        return Collections.unmodifiableList(copy);
    }

    // issue 28
    private static LocalDate parseDate(String text, String field) {
        if (text == null ||
                !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException(
                    field + " deve estar em yyyy-MM-dd");
        }

        try {
            return LocalDate.parse(
                    text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeException error) {
            throw new IllegalArgumentException(
                    field + " é uma data inválida", error);
        }
    }

    public int getVersion() {
        return version;
    }

    // issue 28
    public ScheduleFrequency getFrequency() {
        return frequency;
    }

    // issue 28 - Retorna quando esta versão do cronograma começa a valer.
    public Instant getValidFrom() {
        return validFrom;
    }

    // issue 28 - Retorna quando esta versão deixa de valer.
    public Instant getValidTo() {
        return validTo;
    }

    public List<String> getDaysOfWeek() {
        return daysOfWeek;
    }

    public List<String> getTimes() {
        return times;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isActive() {
        return active;
    }

    public CronogramaDose withActive(boolean newActive) {
        if (active == newActive) {
            return this;
        }

        // issue 28 - Preserva a frequência e a vigência ao copiar o cronograma.
        return new CronogramaDose(
                version, frequency, daysOfWeek, times,
                startDate, endDate, timezone, newActive,
                validFrom, validTo);
    }

    public CronogramaDose withVersion(int newVersion) {
        if (version == newVersion) {
            return this;
        }

        // issue 28 - Preserva a frequência e a vigência ao alterar o número da versão.
        return new CronogramaDose(
                newVersion, frequency, daysOfWeek, times,
                startDate, endDate, timezone, active,
                validFrom, validTo);
    }


    // issue 28 - Cria uma cópia do cronograma com uma nova vigência.
    public CronogramaDose withValidity(Instant from, Instant to) {
        if (Objects.equals(validFrom, from) &&Objects.equals(validTo, to)) {
            return this;
        }
        return new CronogramaDose(version, frequency, daysOfWeek, times,startDate, endDate, timezone, active,from, to);
    }


    // issue 28 - Verifica se um momento pertence à vigência desta versão.
    public boolean coversInstant(Instant instant) {
        Objects.requireNonNull(instant, "instant não pode ser null");
        if (validFrom == null) {
            return false;
        }

        return !instant.isBefore(validFrom)&& (validTo == null || instant.isBefore(validTo));
    }



    public CronogramaDose deactivate() {
        return withActive(false);
    }

    public CronogramaDose nextVersion(
            List<String> newDaysOfWeek,
            List<String> newTimes,
            String newStartDate,
            String newEndDate,
            String newTimezone) {

        return new CronogramaDose(
                version + 1,
                inferFrequency(newDaysOfWeek),
                newDaysOfWeek,
                newTimes,
                newStartDate,
                newEndDate,
                newTimezone == null ? timezone : newTimezone,
                true);
    }

    public CronogramaDose nextVersion(
            List<String> newTimes,
            String newStartDate) {

        return new CronogramaDose(
                version + 1, frequency, daysOfWeek,
                newTimes, newStartDate, endDate, timezone, true);
    }

    // issue 28
    // Compara as regras do cronograma, ignorando versão e estado ativo.
    public boolean hasSameConfiguration(CronogramaDose other) {
        if (other == null) {
            return false;
        }

        Set<String> thisDays = new HashSet<>(daysOfWeek == null ? Collections.emptyList() : daysOfWeek);
        Set<String> otherDays = new HashSet<>(other.daysOfWeek == null? Collections.emptyList(): other.daysOfWeek);

        return frequency == other.frequency && thisDays.equals(otherDays) && new HashSet<>(times).equals(new HashSet<>(other.times))&& Objects.equals(startDate, other.startDate) && Objects.equals(endDate, other.endDate) && Objects.equals(timezone, other.timezone);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof CronogramaDose that)) {
            return false;
        }

        return version == that.version
                && active == that.active
                && frequency == that.frequency
                && Objects.equals(daysOfWeek, that.daysOfWeek)
                && Objects.equals(times, that.times)
                && Objects.equals(startDate, that.startDate)
                && Objects.equals(endDate, that.endDate)
                && Objects.equals(timezone, that.timezone)
                // issue 28 - Diferencia versões com períodos de vigência distintos.
                && Objects.equals(validFrom, that.validFrom)
                && Objects.equals(validTo, that.validTo);
    }

    @Override
    public int hashCode() {
        // issue 28 - Inclui a vigência para manter consistência com equals().
        return Objects.hash(
                version, frequency, daysOfWeek, times,
                startDate, endDate, timezone, active,
                validFrom, validTo);
    }

    @Override
    public String toString() {
        return "CronogramaDose{"
                + "version=" + version
                + ", frequency=" + frequency
                + ", daysOfWeek=" + daysOfWeek
                + ", times=" + times
                + ", startDate='" + startDate + '\''
                + ", endDate='" + endDate + '\''
                + ", timezone='" + timezone + '\''
                + ", active=" + active
                // issue 28 - Exibe o período de vigência para facilitar a leitura do objeto.
                + ", validFrom=" + validFrom
                + ", validTo=" + validTo
                + '}';
    }
}
