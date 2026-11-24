package backend.models;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representa o resultado consolidado da consulta da agenda diária de um usuário.
 */
public class DailyAgendaResponse {
    private final LocalDate date;
    private final String timezone;
    private final List<DailyAgendaItem> doses;

    public DailyAgendaResponse(LocalDate date, String timezone, List<DailyAgendaItem> doses) {
        this.date = Objects.requireNonNull(date, "date não pode ser null");
        this.timezone = Objects.requireNonNull(timezone, "timezone não pode ser null");
        this.doses = doses != null ? Collections.unmodifiableList(doses) : Collections.emptyList();
    }

    public LocalDate getDate() {
        return date;
    }

    public String getTimezone() {
        return timezone;
    }

    public List<DailyAgendaItem> getDoses() {
        return doses;
    }
}
