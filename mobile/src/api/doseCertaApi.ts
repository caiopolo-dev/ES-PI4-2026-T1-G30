import type {
  DailyAgendaResponse,
  DoseAction,
  DoseCertaAgendaClient,
  DoseRecordResponse,
} from '../types/api';

export class DoseCertaApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'DoseCertaApiError';
    this.status = status;
  }
}

export interface DoseCertaApiConfig {
  baseUrl: string;
  getAuthToken: () => Promise<string | null> | string | null;
  fetchImpl?: typeof fetch;
}

/**
 * Cliente HTTP da camada mobile React Native para integração com os endpoints do backend DoseCerta:
 * - GET /doses?date=AAAA-MM-DD (fonte de verdade das doses previstas)
 * - PUT /doses/{id}/record (acionado EXCLUSIVAMENTE por ação explícita do usuário: TAKEN ou SKIPPED)
 */
export class DoseCertaApi implements DoseCertaAgendaClient {
  private readonly baseUrl: string;
  private readonly getAuthToken: () => Promise<string | null> | string | null;
  private readonly fetchImpl: typeof fetch;

  constructor(config: DoseCertaApiConfig) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, '');
    this.getAuthToken = config.getAuthToken;
    this.fetchImpl = config.fetchImpl ?? fetch;
  }

  async getDailyAgenda(date: string, signal?: AbortSignal): Promise<DailyAgendaResponse> {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      throw new Error(`Formato de data civil inválido: "${date}". Esperado AAAA-MM-DD.`);
    }

    const response = await this.request<DailyAgendaResponse>(
      `/doses?date=${encodeURIComponent(date)}`,
      'GET',
      undefined,
      signal,
    );

    // Enriquecemos cada item em memória com data e fuso retornados pelo próprio envelope da agenda,
    // sem alterar os horários ou posologia definidos pelo backend.
    return {
      ...response,
      doses: response.doses.map((dose) => ({
        ...dose,
        date: dose.date ?? response.date,
        timezone: dose.timezone ?? response.timezone,
      })),
    };
  }

  /**
   * Registra uma dose explicitamente como TAKEN ("Tomei") ou SKIPPED ("Não tomei").
   * Nunca deve ser chamado automaticamente pela entrega ou abertura de uma notificação.
   */
  async recordDose(plannedDoseId: string, action: DoseAction): Promise<DoseRecordResponse> {
    if (!plannedDoseId || !plannedDoseId.trim()) {
      throw new Error('plannedDoseId é obrigatório para registrar uma dose.');
    }
    if (action !== 'TAKEN' && action !== 'SKIPPED') {
      throw new Error(`Ação de registro inválida: "${String(action)}".`);
    }

    return this.request<DoseRecordResponse>(
      `/doses/${encodeURIComponent(plannedDoseId)}/record`,
      'PUT',
      { action },
    );
  }

  private async request<T>(
    path: string,
    method: 'GET' | 'PUT' | 'POST',
    body?: unknown,
    signal?: AbortSignal,
  ): Promise<T> {
    const token = await this.getAuthToken();
    const headers: Record<string, string> = {
      Accept: 'application/json',
    };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    const response = await this.fetchImpl(`${this.baseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });

    if (!response.ok) {
      const errorBody = await response.json().catch(() => null) as { error?: string } | null;
      throw new DoseCertaApiError(
        response.status,
        errorBody?.error ?? `Erro na comunicação com o servidor (HTTP ${response.status}).`,
      );
    }

    return (await response.json()) as T;
  }
}

/**
 * Gera datas civis no formato AAAA-MM-DD para uma janela de programação (ex.: hoje até +N dias)
 * respeitando o fuso horário acordado com o backend (padrão America/Sao_Paulo).
 */
export function buildCivilDateWindow(
  startReference: Date,
  windowDays: number,
  timeZone = 'America/Sao_Paulo',
): string[] {
  const days = Math.max(1, Math.floor(windowDays));
  const dates: string[] = [];
  const startCivil = formatCivilDateInTimeZone(startReference, timeZone);

  for (let offset = 0; offset < days; offset += 1) {
    dates.push(addDaysToCivilDate(startCivil, offset));
  }
  return dates;
}

export function formatCivilDateInTimeZone(date: Date, timeZone = 'America/Sao_Paulo'): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);

  const year = parts.find((p) => p.type === 'year')?.value ?? '1970';
  const month = parts.find((p) => p.type === 'month')?.value ?? '01';
  const day = parts.find((p) => p.type === 'day')?.value ?? '01';
  return `${year}-${month}-${day}`;
}

export function addDaysToCivilDate(civilDate: string, daysToAdd: number): string {
  const [yearStr, monthStr, dayStr] = civilDate.split('-');
  const utc = new Date(Date.UTC(Number(yearStr), Number(monthStr) - 1, Number(dayStr) + daysToAdd));
  const y = String(utc.getUTCFullYear()).padStart(4, '0');
  const m = String(utc.getUTCMonth() + 1).padStart(2, '0');
  const d = String(utc.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

