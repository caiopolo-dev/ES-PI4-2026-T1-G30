/**
 * Contratos HTTP do backend DoseCerta alinhados à OpenAPI / develop.
 * O backend é a única fonte de verdade para doses previstas, horários e fusos.
 */

export type DoseAction = 'TAKEN' | 'SKIPPED';

export type ScheduleStatus = 'SCHEDULED' | 'CANCELLED';

export type DisplayStatus = 'UPCOMING' | 'DUE' | 'OVERDUE' | DoseAction;

export interface AgendaDoseRecord {
  id: string;
  action: DoseAction;
  actualTime: string | null;
  recordedAt: string;
}

/**
 * Representa uma ocorrência identificável de DosePrevista retornada por GET /doses?date=AAAA-MM-DD.
 */
export interface PlannedDose {
  id: string;
  treatmentId: string;
  medicationInUseId: string;
  medicationName: string;
  plannedQuantity: number;
  /** Instante previsto em formato ISO-8601 UTC fornecido pelo backend (ex.: "2026-11-22T11:00:00Z"). */
  scheduledAt: string;
  /** Horário local formatado no fuso da agenda (ex.: "08:00"). */
  scheduledLocalTime: string;
  scheduleStatus: ScheduleStatus;
  displayStatus: DisplayStatus;
  record: AgendaDoseRecord | null;
  /** Campos opcionais que podem enriquecer a reconciliação quando disponibilizados pelo backend ou agenda. */
  scheduleVersion?: number;
  timezone?: string;
  date?: string;
  userId?: string;
}

export interface DailyAgendaResponse {
  date: string;
  timezone: string;
  doses: PlannedDose[];
}

export interface DoseRecordResponse {
  plannedDoseId: string;
  action: DoseAction;
  scheduledAt: string;
  scheduleVersion: number;
  actualTime: string | null;
  recordedAt: string;
}

export interface DoseCertaAgendaClient {
  getDailyAgenda(date: string, signal?: AbortSignal): Promise<DailyAgendaResponse>;
  recordDose(plannedDoseId: string, action: DoseAction): Promise<DoseRecordResponse>;
}

