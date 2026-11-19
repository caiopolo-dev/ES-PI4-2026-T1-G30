package backend.services;

/** Operacao impedida por uma regra de negocio do medicamento ou tratamento. */
public final class MedicationOperationConflictException extends IllegalStateException {
    public MedicationOperationConflictException(String message) {
        super(message);
    }
}
