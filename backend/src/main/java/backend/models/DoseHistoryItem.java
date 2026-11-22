// Desenvolvido por Miguel Gallinucci.
package backend.models;

import java.math.BigDecimal;
import java.time.Instant;

/** Dados da ocorrencia e da acao registrada, sem reconstruir o passado. */
public record DoseHistoryItem(
        // DTO e um objeto para transportar dados; usei record para agrupar os campos da resposta sem setters.
        // Estes IDs ligam a ocorrencia ao tratamento e ao medicamento que deram origem a ela.
        String plannedDoseId,
        String treatmentId,
        String medicationInUseId,
        String medicationName,

        int scheduleVersion,
        Instant scheduledAt,
        String scheduleTimezone,
        BigDecimal plannedQuantity,
        // BigDecimal representa decimais com precisao; usei para evitar arredondamento causado por double.
        String plannedUnit,

        // actualTime indica quando a acao ocorreu; recordedAt indica quando ela foi registrada.
        DoseAction action,
        Instant actualTime,
        Instant recordedAt,
        boolean isCancelled) {

    // Mantive os dados da previsao separados da acao: uma dose pode existir sem registro.
    // A unidade historica desconhecida permanece null porque a ocorrencia nao guarda esse dado.
}
