package backend.src.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representa uma versão do cronograma de doses de um medicamento em uso.
 * Preserva o histórico de alterações de horários, frequência (dias da semana) e vigência.
 */
public class CronogramaDose {
    private final int version;
    private final List<String> daysOfWeek;
    private final List<String> times;
    private final String startDate;
    private final String endDate;
    private final String timezone;
    private final boolean active;

    public CronogramaDose(
            int version,
            List<String> daysOfWeek,
            List<String> times,
            String startDate,
            String endDate,
            String timezone,
            boolean active) {
        if (version < 1) {
            throw new IllegalArgumentException("version deve ser maior ou igual a 1");
        }
        Objects.requireNonNull(times, "times não pode ser null");
        if (times.isEmpty()) {
            throw new IllegalArgumentException("times deve conter ao menos um horário");
        }
        Objects.requireNonNull(startDate, "startDate não pode ser null");
        if (startDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate não pode ser vazio");
        }
        Objects.requireNonNull(timezone, "timezone não pode ser null");
        if (timezone.trim().isEmpty()) {
            throw new IllegalArgumentException("timezone não pode ser vazio");
        }

        this.version = version;
        this.daysOfWeek = daysOfWeek != null
                ? Collections.unmodifiableList(new ArrayList<>(daysOfWeek))
                : null;
        this.times = Collections.unmodifiableList(new ArrayList<>(times));
        this.startDate = startDate.trim();
        this.endDate = (endDate != null && !endDate.trim().isEmpty()) ? endDate.trim() : null;
        this.timezone = timezone.trim();
        this.active = active;
    }

    /**
     * Construtor de conveniência para criar a versão inicial (v1) ativa de um cronograma.
     */
    public CronogramaDose(List<String> times, String startDate, String timezone) {
        this(1, null, times, startDate, null, timezone, true);
    }

    /**
     * Construtor de conveniência para criar a versão inicial (v1) ativa com dias da semana e vigência.
     */
    public CronogramaDose(
            List<String> daysOfWeek,
            List<String> times,
            String startDate,
            String endDate,
            String timezone) {
        this(1, daysOfWeek, times, startDate, endDate, timezone, true);
    }

    public int getVersion() {
        return version;
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

    /**
     * Retorna uma cópia deste cronograma alterando apenas a flag active.
     */
    public CronogramaDose withActive(boolean newActive) {
        if (this.active == newActive) {
            return this;
        }
        return new CronogramaDose(
                this.version,
                this.daysOfWeek,
                this.times,
                this.startDate,
                this.endDate,
                this.timezone,
                newActive
        );
    }

    /**
     * Retorna uma cópia deste cronograma com o número de versão especificado.
     */
    public CronogramaDose withVersion(int newVersion) {
        if (this.version == newVersion) {
            return this;
        }
        return new CronogramaDose(
                newVersion,
                this.daysOfWeek,
                this.times,
                this.startDate,
                this.endDate,
                this.timezone,
                this.active
        );
    }

    /**
     * Retorna uma cópia inativa deste cronograma para preservação histórica.
     */
    public CronogramaDose deactivate() {
        return withActive(false);
    }

    /**
     * Cria uma nova versão ativa do cronograma a partir desta versão, incrementando version.
     */
    public CronogramaDose nextVersion(
            List<String> newDaysOfWeek,
            List<String> newTimes,
            String newStartDate,
            String newEndDate,
            String newTimezone) {
        return new CronogramaDose(
                this.version + 1,
                newDaysOfWeek,
                newTimes,
                newStartDate,
                newEndDate,
                newTimezone != null ? newTimezone : this.timezone,
                true
        );
    }

    /**
     * Cria uma nova versão ativa alterando apenas os horários e a data de início.
     */
    public CronogramaDose nextVersion(List<String> newTimes, String newStartDate) {
        return nextVersion(this.daysOfWeek, newTimes, newStartDate, this.endDate, this.timezone);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CronogramaDose)) {
            return false;
        }
        CronogramaDose that = (CronogramaDose) o;
        return version == that.version
                && active == that.active
                && Objects.equals(daysOfWeek, that.daysOfWeek)
                && Objects.equals(times, that.times)
                && Objects.equals(startDate, that.startDate)
                && Objects.equals(endDate, that.endDate)
                && Objects.equals(timezone, that.timezone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, daysOfWeek, times, startDate, endDate, timezone, active);
    }

    @Override
    public String toString() {
        return "CronogramaDose{"
                + "version=" + version
                + ", daysOfWeek=" + daysOfWeek
                + ", times=" + times
                + ", startDate='" + startDate + '\''
                + ", endDate='" + endDate + '\''
                + ", timezone='" + timezone + '\''
                + ", active=" + active
                + '}';
    }
}

