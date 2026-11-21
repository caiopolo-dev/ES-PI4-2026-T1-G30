package backend.services;

/** Representa uma tentativa incompatível de registrar uma dose prevista. */
public final class DoseRecordConflictException extends IllegalStateException {
    public DoseRecordConflictException(String message) {
        super(message);
    }
}
