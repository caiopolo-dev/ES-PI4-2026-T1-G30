// Desenvolvido por Caio Ferreira Polo.
package backend.services;

/**
 * Representa uma tentativa incompatível de registrar uma dose prevista.
 * O handler transforma essa situação em HTTP 409 para informar que a operação
 * entra em conflito com o estado atual da dose.
 */
public final class DoseRecordConflictException extends IllegalStateException {
    public DoseRecordConflictException(String message) {
        super(message);
    }
}
