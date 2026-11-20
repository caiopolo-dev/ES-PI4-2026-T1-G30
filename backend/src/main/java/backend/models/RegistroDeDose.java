// Autor: Samuel Campovilla
package backend.models;

import java.time.Instant;
import java.util.UUID;

// Representa uma ação explícita do usuário sobre uma DosePrevista.
// Registra se o medicamento foi tomado (TAKEN) ou ignorado (SKIPPED).
// A ausência de registro não significa que a dose foi ignorada.

public class RegistroDeDose {

    private final String id;
    private final String plannedDoseId;
    private final String userId;
    private final DoseAction action;
    private final Instant actualTime;
    private final Instant recordedAt;

    public RegistroDeDose(
            String id,
            String plannedDoseId,
            String userId,
            DoseAction action,
            Instant actualTime,
            Instant recordedAt) {

        // Gera um identificador automaticamente caso não seja informado
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        if (plannedDoseId == null || plannedDoseId.isBlank()) {
            throw new IllegalArgumentException("ID da dose prevista inválido");
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("ID do usuário inválido");
        }

        if (action == null) {
            throw new IllegalArgumentException("Ação não pode ser null");
        }

        if (recordedAt == null) {
            throw new IllegalArgumentException("Horário do registro não pode ser null");
        }

        // Se tomou, precisamos saber quando
        if (action == DoseAction.TAKEN && actualTime == null) {
            throw new IllegalArgumentException("TAKEN precisa de actualTime");
        }

        // Se não tomou, não existe horário de ingestão
        if (action == DoseAction.SKIPPED && actualTime != null) {
            throw new IllegalArgumentException("SKIPPED não deve ter actualTime");
        }

        this.id = id.trim();
        this.plannedDoseId = plannedDoseId.trim();
        this.userId = userId.trim();
        this.action = action;
        this.actualTime = actualTime;
        this.recordedAt = recordedAt;
    }

    public String getId() {
        return id;
    }

    public String getPlannedDoseId() {
        return plannedDoseId;
    }

    public String getUserId() {
        return userId;
    }

    public DoseAction getAction() {
        return action;
    }

    public Instant getActualTime() {
        return actualTime;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
