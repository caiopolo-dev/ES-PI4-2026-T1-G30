// Desenvolvido por Miguel Gallinucci.
package backend.services;

/** Operacao impedida por uma regra de negocio do medicamento ou tratamento. */
public final class MedicationOperationConflictException extends IllegalStateException {
    public MedicationOperationConflictException(String message) {
        // O handler responde 409 quando os dados sao validos, mas o estado atual impede a operacao.
        super(message);
    }
}
