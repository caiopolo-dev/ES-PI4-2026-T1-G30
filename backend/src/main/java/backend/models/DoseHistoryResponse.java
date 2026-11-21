package backend.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Periodo consultado e itens do historico, incluindo quando a lista esta vazia. */
public record DoseHistoryResponse(
        LocalDate from,
        LocalDate to,
        String timezone,
        Instant referenceTime,

        List<DoseHistoryItem> items) {

    public DoseHistoryResponse {

        Objects.requireNonNull(from, "Data inicial obrigatoria.");
        Objects.requireNonNull(to, "Data final obrigatoria.");
        Objects.requireNonNull(timezone, "Fuso obrigatorio.");
        Objects.requireNonNull(referenceTime, "Instante de referencia obrigatorio.");

        // Fiz uma copia imutavel porque a lista original nao deve mudar a resposta pronta.
        items = List.copyOf(items);
    }
}
