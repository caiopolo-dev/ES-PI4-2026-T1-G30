// Autor: Samuel Campovilla
package backend.models;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Responsável por gerar identificadores determinísticos
// para as ocorrências de DosePrevista.
// Uma mesma ocorrência deve possuir o mesmo ID,
// independentemente de quantas vezes for gerada.

public final class PlannedDoseIdentity {

    // Construtor privado impede a criação de objetos desta classe.
    // Seus métodos serão utilizados diretamente pela classe -> Static methods.
    private PlannedDoseIdentity() {
    }

    public static String generateId(
            String userId,
            String treatmentId,
            String medicationInUseId,
            int scheduleVersion,
            Instant scheduledAt) {

        // Verifica se os identificadores informados são válidos.
        if (userId == null || userId.isBlank()
                || treatmentId == null || treatmentId.isBlank()
                || medicationInUseId == null || medicationInUseId.isBlank()) {
            throw new IllegalArgumentException("Os identificadores não podem ser nulos ou vazios");
        }

        if (scheduleVersion < 1) {
            throw new IllegalArgumentException("A versão do cronograma deve ser positiva");
        }

        Objects.requireNonNull(scheduledAt, "scheduledAt não pode ser null");

        // Normaliza os identificadores, assim como DosePrevista.
        userId = userId.trim();
        treatmentId = treatmentId.trim();
        medicationInUseId = medicationInUseId.trim();

        // Monta a identidade lógica da ocorrência.
        // O prefixo v1 identifica a versão deste algoritmo.
        StringBuilder chave = new StringBuilder("planned-dose-v1:");

        adicionarCampo(chave, userId);
        adicionarCampo(chave, treatmentId);
        adicionarCampo(chave, medicationInUseId);
        adicionarCampo(chave, String.valueOf(scheduleVersion));
        adicionarCampo(chave, scheduledAt.toString());

        // Gera sempre o mesmo UUID para a mesma sequência de bytes.
        return UUID.nameUUIDFromBytes(chave.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    // Adiciona um campo informando seu tamanho antes do valor.
    // Isso evita ambiguidades caso um ID contenha separadores.
    private static void adicionarCampo(StringBuilder chave, String valor) {
        chave.append(valor.length()).append(':').append(valor);
    }
}
