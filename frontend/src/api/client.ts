import type { Agenda, Action, DoseRecordResponse, HistoryResponse, LoginResponse, MedicationInput, MedicationPresentationSearchResponse, MedicationResponse, ScheduleInput, ScheduleResponse, Summary, Treatment, TreatmentInput, User } from "../types/api";

const sessionKey = "dosecerta.session";
export const session = {
  get: () => sessionStorage.getItem(sessionKey),
  save: (token: string) => sessionStorage.setItem(sessionKey, token),
  clear: () => sessionStorage.removeItem(sessionKey),
};
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
export function messageOf(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Sessão ou credenciais inválidas.";
    if (error.status === 429) return "Muitas tentativas. Aguarde antes de tentar novamente.";
    return error.message;
  }
  return error instanceof Error && !(error instanceof TypeError) ? error.message : "Não foi possível conectar ao servidor. Verifique a conexão e tente novamente.";
}

async function request<T>(path: string, method = "GET", body?: unknown, authenticated = true, signal?: AbortSignal): Promise<T> {
  const headers = new Headers({ Accept: "application/json" });
  const token = authenticated ? session.get() : null;
  if (token) headers.set("Authorization", "Bearer " + token);
  if (body !== undefined) headers.set("Content-Type", "application/json");
  const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal, cache: "no-store" });
  if (response.status === 401 && authenticated && session.get() === token) {
    session.clear();
    window.dispatchEvent(new Event("dosecerta:expired"));
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new ApiError(response.status, error?.error || "A solicitação falhou (HTTP " + response.status + ").");
  }
  return response.status === 204 ? undefined as T : await response.json() as T;
}

const treatmentPath = (id: string) => "/treatments/" + encodeURIComponent(id);
const medicationPath = (id: string, med: string) => treatmentPath(id) + "/medications/" + encodeURIComponent(med);
export function periodQuery(from: string, to: string, treatmentId: string) {
  const params = new URLSearchParams({ from, to });
  if (treatmentId) params.set("treatmentId", treatmentId);
  return params.toString();
}
export const api = {
  register: (name: string, email: string, password: string) => request<User>("/auth/register", "POST", { name, email, password }, false),
  login: (email: string, password: string) => request<LoginResponse>("/auth/login", "POST", { email, password }, false),
  me: () => request<User>("/auth/me"),
  logout: () => request<void>("/auth/logout", "POST"),
  treatments: () => request<Treatment[]>("/treatments"),
  treatment: (id: string) => request<Treatment>(treatmentPath(id)),
  create: (body: TreatmentInput) => request<Treatment>("/treatments", "POST", body),
  update: (id: string, body: TreatmentInput) => request<Treatment>(treatmentPath(id), "PUT", body),
  activate: (id: string) => request<Treatment>(treatmentPath(id) + "/activate", "POST"),
  close: (id: string) => request<Treatment>(treatmentPath(id) + "/close", "POST"),
  addMedication: (id: string, body: MedicationInput) => request<MedicationResponse>(treatmentPath(id) + "/medications", "POST", body),
  updateMedication: (id: string, med: string, body: MedicationInput & { revision: number; validFrom?: string }) => request<MedicationResponse>(medicationPath(id, med), "PUT", body),
  deactivateMedication: (id: string, med: string) => request<void>(medicationPath(id, med), "DELETE"),
  schedule: (id: string, med: string) => request<ScheduleResponse>(medicationPath(id, med) + "/schedule"),
  saveSchedule: (id: string, med: string, body: ScheduleInput) => request<ScheduleResponse>(medicationPath(id, med) + "/schedule", "PUT", body),
  agenda: (date: string) => request<Agenda>("/doses?date=" + encodeURIComponent(date)),
  record: (id: string, action: Action) => request<DoseRecordResponse>("/doses/" + encodeURIComponent(id) + "/record", "PUT", { action }),
  summary: (query: string, signal?: AbortSignal) => request<Summary>("/adherence/summary?" + query, "GET", undefined, true, signal),
  history: (query: string, signal?: AbortSignal) => request<HistoryResponse>("/adherence/history?" + query, "GET", undefined, true, signal),
  catalog: () => request<{ id: string; name: string }[]>("/medications"),
  presentations: (query: string) => request<MedicationPresentationSearchResponse>("/medication-presentations?q=" + encodeURIComponent(query)),
};
