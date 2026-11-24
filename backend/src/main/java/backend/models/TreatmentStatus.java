// Autor: Samuel Campovilla
package backend.models;
// Representa os possíveis estados de um tratamento
public enum TreatmentStatus {
    DRAFT,    // Tratamento em elaboração
    ACTIVE,   // Tratamento validado e em andamento
    CLOSED    // Tratamento encerrado
}
