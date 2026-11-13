package backend.models;

/**
 * Situação do agendamento de uma DosePrevista (planned_doses).
 * Distingue ocorrências válidas (SCHEDULED) de ocorrências canceladas por alteração/encerramento (CANCELLED).
 */
public enum ScheduleStatus {
    SCHEDULED,
    CANCELLED
}
