package backend.models;

import java.math.BigDecimal;
import java.time.Instant;

/** Dados da ocorrencia e da acao registrada, sem reconstruir o passado. */
public record DoseHistoryItem(
        String plannedDoseId,
        String treatmentId,
        String medicationInUseId,
        String medicationName,

        int scheduleVersion,
        Instant scheduledAt,
        String scheduleTimezone,
        BigDecimal plannedQuantity,
        String plannedUnit,

        DoseAction action,
        Instant actualTime,
        Instant recordedAt,
        boolean isCancelled) {

    // Mantive os dados da previsao separados da acao: uma dose pode existir sem registro.
    // A unidade historica desconhecida permanece null porque a ocorrencia nao guarda esse dado.
}
