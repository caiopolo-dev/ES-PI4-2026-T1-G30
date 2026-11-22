// Desenvolvido por Miguel Gallinucci.
package backend.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Periodo consultado e itens do historico, incluindo quando a lista esta vazia. */
public record DoseHistoryResponse(
        // Mantive periodo e fuso na resposta para o consumidor interpretar os itens no mesmo contexto da consulta.
        LocalDate from,
        LocalDate to,
        String timezone,
        // Uma referencia unica permite distinguir doses futuras e vencidas sem inventar uma acao.
        Instant referenceTime,

        List<DoseHistoryItem> items) {

    public DoseHistoryResponse {
        // Estes dados sao obrigatorios mesmo com items vazio, para a resposta continuar descrevendo a consulta.

        Objects.requireNonNull(from, "Data inicial obrigatoria.");
        Objects.requireNonNull(to, "Data final obrigatoria.");
        Objects.requireNonNull(timezone, "Fuso obrigatorio.");
        Objects.requireNonNull(referenceTime, "Instante de referencia obrigatorio.");

        // Fiz uma copia imutavel porque a lista original nao deve mudar a resposta pronta.
        items = List.copyOf(items);
        // A copia protege a lista de itens; os campos de cada item ja sao definidos pelo record.
    }
}
