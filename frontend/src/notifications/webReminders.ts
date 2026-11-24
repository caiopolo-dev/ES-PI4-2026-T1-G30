import type { Agenda, Dose } from "../types/api";

const DEFAULT_TIMEZONE = "America/Sao_Paulo";

function civilDate(value = new Date(), timeZone = DEFAULT_TIMEZONE): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(value);
  return ["year", "month", "day"]
    .map((key) => parts.find((part) => part.type === key)?.value ?? "")
    .join("-");
}

function shiftDate(date: string, offset: number): string {
  const result = new Date(`${date}T12:00:00Z`);
  result.setUTCDate(result.getUTCDate() + offset);
  return result.toISOString().slice(0, 10);
}

export type NotificationPermissionStatus =
  | "granted"
  | "undetermined"
  | "denied"
  | "blocked"
  | "unavailable";

export interface ReminderPreferences {
  enabled: boolean;
  hideSensitiveDetailsOnLockScreen: boolean;
  updatedAt: string;
}

export interface ScheduledReminderRecord {
  plannedDoseId: string;
  platformNotificationId: string;
  userId: string;
  treatmentId: string;
  medicationInUseId: string;
  medicationName: string;
  scheduledAt: string;
  scheduledLocalTime: string;
  timezone: string;
  date: string;
  hideSensitiveDetailsOnLockScreen: boolean;
  scheduledOnDeviceAt: string;
}

export interface ReminderNotificationPayload {
  type: "DOSE_REMINDER";
  plannedDoseId: string;
  treatmentId: string;
  medicationInUseId: string;
  date: string;
  scheduledAt: string;
  scheduledLocalTime: string;
  timezone: string;
}

export interface ReminderNavigationTarget {
  date: string;
  plannedDoseId: string;
  treatmentId: string;
  medicationInUseId: string;
}

export interface WebNotificationContent {
  title: string;
  body: string;
  tag: string;
  data: ReminderNotificationPayload;
}

export interface ScheduleReminderResult {
  plannedDoseId: string;
  status:
    | "scheduled"
    | "skipped_already_scheduled"
    | "skipped_ineligible"
    | "blocked_or_disabled"
    | "failed";
  platformNotificationId?: string;
  reason?: string;
}

export interface ReconcileOptions {
  reconciledDates?: string[];
  scopeTreatmentId?: string;
  timezone?: string;
}

export interface ReconcileRemindersResult {
  scheduledCount: number;
  rescheduledCount: number;
  unchangedCount: number;
  cancelledCount: number;
  skippedCount: number;
  failedCount: number;
  permissionStatus: NotificationPermissionStatus;
  preferenceEnabled: boolean;
  effectiveActive: boolean;
  truncatedByPlatformLimit: boolean;
  errors: string[];
}

export interface KeyValueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface WebNotificationDriver {
  readonly maxPendingNotifications: number;
  isAvailable(): boolean;
  getPermissionStatus(): NotificationPermissionStatus;
  requestPermission(): Promise<NotificationPermissionStatus>;
  scheduleLocalNotification(content: WebNotificationContent, triggerDate: Date): string;
  cancelScheduledNotification(platformNotificationId: string): void;
  getAllScheduledNotificationIds(): string[];
  onNotificationClick(listener: (payload: ReminderNotificationPayload) => void): () => void;
  dispose(): void;
}

export const DEFAULT_WEB_SCHEDULING_WINDOW_DAYS = 7;
export const DEFAULT_WEB_MAX_PENDING_REMINDERS = 50;
const MAX_TIMEOUT_MS = 2_147_483_647;
const PREFS_PREFIX = "dosecerta.reminders.prefs.";
const REGISTRY_PREFIX = "dosecerta.reminders.registry.";

export class InMemoryStorage implements KeyValueStorage {
  private readonly data = new Map<string, string>();
  getItem(key: string) {
    return this.data.has(key) ? (this.data.get(key) ?? null) : null;
  }
  setItem(key: string, value: string) {
    this.data.set(key, value);
  }
  removeItem(key: string) {
    this.data.delete(key);
  }
}

export class BrowserLocalStorageAdapter implements KeyValueStorage {
  private readonly fallback = new InMemoryStorage();
  getItem(key: string): string | null {
    try {
      return typeof localStorage !== "undefined" ? localStorage.getItem(key) : this.fallback.getItem(key);
    } catch {
      return this.fallback.getItem(key);
    }
  }
  setItem(key: string, value: string): void {
    try {
      if (typeof localStorage !== "undefined") localStorage.setItem(key, value);
      else this.fallback.setItem(key, value);
    } catch {
      this.fallback.setItem(key, value);
    }
  }
  removeItem(key: string): void {
    try {
      if (typeof localStorage !== "undefined") localStorage.removeItem(key);
      else this.fallback.removeItem(key);
    } catch {
      this.fallback.removeItem(key);
    }
  }
}

export function createDefaultReminderPreferences(now: Date = new Date()): ReminderPreferences {
  return {
    enabled: false,
    hideSensitiveDetailsOnLockScreen: true,
    updatedAt: now.toISOString(),
  };
}

/**
 * Gera conteúdo de notificação compatível com privacidade na tela bloqueada/desktop.
 * Por padrão (hideSensitiveDetailsOnLockScreen = true), omite nome do medicamento e dados clínicos.
 */
export function buildReminderNotificationContent(
  dose: Dose,
  date: string,
  timezone: string,
  preferences: Pick<ReminderPreferences, "hideSensitiveDetailsOnLockScreen">,
): WebNotificationContent {
  const timeLabel = dose.scheduledLocalTime || "--:--";
  const payload: ReminderNotificationPayload = {
    type: "DOSE_REMINDER",
    plannedDoseId: dose.id,
    treatmentId: dose.treatmentId,
    medicationInUseId: dose.medicationInUseId,
    date,
    scheduledAt: dose.scheduledAt,
    scheduledLocalTime: timeLabel,
    timezone,
  };

  if (preferences.hideSensitiveDetailsOnLockScreen) {
    return {
      title: "DoseCerta — Lembrete de horário",
      body: `Você tem uma dose programada para ${timeLabel}. Abra o aplicativo para consultar os detalhes.`,
      tag: `dosecerta-${dose.id}`,
      data: payload,
    };
  }

  return {
    title: `DoseCerta — Horário previsto (${timeLabel})`,
    body: `${dose.medicationName} está programado para ${timeLabel}. Abra o app para registrar manualmente.`,
    tag: `dosecerta-${dose.id}`,
    data: payload,
  };
}

/**
 * Adaptador Web baseado na Browser Notifications API (window.Notification) + timers locais.
 *
 * Limitação documentada do ambiente Web (sem Web Push no MVP):
 * notificações locais são disparadas enquanto a aba/navegador permanecer aberto.
 */
export class BrowserNotificationDriver implements WebNotificationDriver {
  readonly maxPendingNotifications = DEFAULT_WEB_MAX_PENDING_REMINDERS;
  private readonly timers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly listeners = new Set<(payload: ReminderNotificationPayload) => void>();
  private readonly nowProvider: () => Date;
  private seq = 0;

  constructor(nowProvider: () => Date = () => new Date()) {
    this.nowProvider = nowProvider;
  }

  isAvailable(): boolean {
    return typeof window !== "undefined" && typeof window.Notification !== "undefined";
  }

  getPermissionStatus(): NotificationPermissionStatus {
    if (!this.isAvailable()) return "unavailable";
    const permission = window.Notification.permission;
    if (permission === "granted") return "granted";
    if (permission === "denied") return "blocked";
    return "undetermined";
  }

  async requestPermission(): Promise<NotificationPermissionStatus> {
    if (!this.isAvailable()) return "unavailable";
    try {
      const result = await window.Notification.requestPermission();
      if (result === "granted") return "granted";
      if (result === "denied") return "blocked";
      return "undetermined";
    } catch {
      return "unavailable";
    }
  }

  scheduleLocalNotification(content: WebNotificationContent, triggerDate: Date): string {
    if (!this.isAvailable()) {
      throw new Error("API de notificações do navegador indisponível neste ambiente.");
    }
    const delayMs = triggerDate.getTime() - this.nowProvider().getTime();
    if (delayMs <= 0) {
      throw new Error("Não é possível agendar notificação para um horário passado.");
    }
    const clampedDelay = Math.min(delayMs, MAX_TIMEOUT_MS);
    this.seq += 1;
    const id = `web-notif-${this.seq}-${content.data.plannedDoseId}`;

    const timer = setTimeout(() => {
      this.timers.delete(id);
      if (!this.isAvailable() || window.Notification.permission !== "granted") return;
      try {
        const notification = new window.Notification(content.title, {
          body: content.body,
          tag: content.tag,
          data: content.data,
        });
        notification.onclick = () => {
          try {
            window.focus();
          } catch {
            // Ignora restrições de foco em contextos bloqueados.
          }
          notification.close();
          for (const listener of this.listeners) {
            listener(content.data);
          }
        };
      } catch {
        // Falha pontual de entrega não deve interromper o app.
      }
    }, clampedDelay);

    this.timers.set(id, timer);
    return id;
  }

  cancelScheduledNotification(platformNotificationId: string): void {
    const timer = this.timers.get(platformNotificationId);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(platformNotificationId);
    }
  }

  getAllScheduledNotificationIds(): string[] {
    return Array.from(this.timers.keys());
  }

  onNotificationClick(listener: (payload: ReminderNotificationPayload) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  dispose(): void {
    for (const timer of this.timers.values()) {
      clearTimeout(timer);
    }
    this.timers.clear();
    this.listeners.clear();
  }
}

export interface WebReminderServiceOptions {
  userId: string;
  driver: WebNotificationDriver;
  storage?: KeyValueStorage;
  schedulingWindowDays?: number;
  maxPendingReminders?: number;
  nowProvider?: () => Date;
  defaultTimezone?: string;
}

/**
 * Motor de lembretes integrado ao frontend React Web (Issue #37).
 *
 * Regras garantidas:
 * - Fonte de verdade única: GET /doses?date=AAAA-MM-DD (backend);
 * - Identidade estável por plannedDoseId (sem duplicatas em consultas repetidas);
 * - Nunca chama PUT /doses/{id}/record automaticamente;
 * - Cancela lembretes afetados ao alterar cronograma, encerrar tratamento, desabilitar preferência ou registrar dose;
 * - Respeita janela de programação e limite máximo de agendamentos.
 */
export class WebReminderService {
  private readonly userId: string;
  private readonly driver: WebNotificationDriver;
  private readonly storage: KeyValueStorage;
  private readonly schedulingWindowDays: number;
  private readonly maxPendingReminders: number;
  private readonly nowProvider: () => Date;
  private readonly defaultTimezone: string;
  private operationQueue: Promise<unknown> = Promise.resolve();

  constructor(options: WebReminderServiceOptions) {
    if (!options.userId || !options.userId.trim()) {
      throw new Error("userId é obrigatório para inicializar o serviço de lembretes.");
    }
    this.userId = options.userId.trim();
    this.driver = options.driver;
    this.storage = options.storage ?? new BrowserLocalStorageAdapter();
    this.nowProvider = options.nowProvider ?? (() => new Date());
    this.schedulingWindowDays = Math.max(1, options.schedulingWindowDays ?? DEFAULT_WEB_SCHEDULING_WINDOW_DAYS);
    this.maxPendingReminders = Math.max(
      1,
      Math.min(
        options.maxPendingReminders ?? DEFAULT_WEB_MAX_PENDING_REMINDERS,
        options.driver.maxPendingNotifications,
      ),
    );
    this.defaultTimezone = options.defaultTimezone ?? DEFAULT_TIMEZONE;
  }

  private enqueue<T>(task: () => Promise<T>): Promise<T> {
    const next = this.operationQueue.then(task, task);
    this.operationQueue = next.then(
      () => undefined,
      () => undefined,
    );
    return next;
  }

  private prefsKey(): string {
    return `${PREFS_PREFIX}${this.userId}`;
  }

  private registryKey(): string {
    return `${REGISTRY_PREFIX}${this.userId}`;
  }

  getPermissionStatus(): NotificationPermissionStatus {
    return this.driver.isAvailable() ? this.driver.getPermissionStatus() : "unavailable";
  }

  async requestPermission(): Promise<NotificationPermissionStatus> {
    return this.driver.isAvailable() ? this.driver.requestPermission() : "unavailable";
  }

  getPreferences(): ReminderPreferences {
    const raw = this.storage.getItem(this.prefsKey());
    if (!raw) return createDefaultReminderPreferences(this.nowProvider());
    try {
      const parsed = JSON.parse(raw) as Partial<ReminderPreferences>;
      return {
        enabled: Boolean(parsed.enabled),
        hideSensitiveDetailsOnLockScreen:
          typeof parsed.hideSensitiveDetailsOnLockScreen === "boolean"
            ? parsed.hideSensitiveDetailsOnLockScreen
            : true,
        updatedAt: typeof parsed.updatedAt === "string" ? parsed.updatedAt : this.nowProvider().toISOString(),
      };
    } catch {
      return createDefaultReminderPreferences(this.nowProvider());
    }
  }

  private savePreferences(patch: Partial<Pick<ReminderPreferences, "enabled" | "hideSensitiveDetailsOnLockScreen">>): ReminderPreferences {
    const current = this.getPreferences();
    const updated: ReminderPreferences = {
      enabled: patch.enabled !== undefined ? patch.enabled : current.enabled,
      hideSensitiveDetailsOnLockScreen:
        patch.hideSensitiveDetailsOnLockScreen !== undefined
          ? patch.hideSensitiveDetailsOnLockScreen
          : current.hideSensitiveDetailsOnLockScreen,
      updatedAt: this.nowProvider().toISOString(),
    };
    this.storage.setItem(this.prefsKey(), JSON.stringify(updated));
    return updated;
  }

  private readRegistry(): Record<string, ScheduledReminderRecord> {
    const raw = this.storage.getItem(this.registryKey());
    if (!raw) return {};
    try {
      const parsed = JSON.parse(raw) as Record<string, ScheduledReminderRecord>;
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch {
      return {};
    }
  }

  private writeRegistry(map: Record<string, ScheduledReminderRecord>): void {
    this.storage.setItem(this.registryKey(), JSON.stringify(map));
  }

  getManagedReminders(): ScheduledReminderRecord[] {
    return Object.values(this.readRegistry()).sort((a, b) =>
      a.scheduledAt.localeCompare(b.scheduledAt),
    );
  }

  async enableReminders(): Promise<{
    preferences: ReminderPreferences;
    permissionStatus: NotificationPermissionStatus;
    effectiveActive: boolean;
  }> {
    const permissionStatus = this.getPermissionStatus();
    const preferences = this.savePreferences({ enabled: true });
    return {
      preferences,
      permissionStatus,
      effectiveActive: preferences.enabled && permissionStatus === "granted",
    };
  }

  async disableReminders(): Promise<{
    preferences: ReminderPreferences;
    cancelledCount: number;
  }> {
    const preferences = this.savePreferences({ enabled: false });
    const cancelledCount = await this.cancelAllManagedReminders();
    return { preferences, cancelledCount };
  }

  setLockScreenPrivacy(hideSensitiveDetailsOnLockScreen: boolean): ReminderPreferences {
    return this.savePreferences({ hideSensitiveDetailsOnLockScreen });
  }

  async scheduleReminder(
    dose: Dose,
    date?: string,
    timezone: string = this.defaultTimezone,
  ): Promise<ScheduleReminderResult> {
    return this.enqueue(async () => this.scheduleReminderInternal(dose, date, timezone));
  }

  private scheduleReminderInternal(
    dose: Dose,
    explicitDate?: string,
    timezone: string = this.defaultTimezone,
    preloadedOsIds?: Set<string>,
  ): ScheduleReminderResult {
    const preferences = this.getPreferences();
    const permissionStatus = this.getPermissionStatus();

    if (!preferences.enabled) {
      return {
        plannedDoseId: dose.id,
        status: "blocked_or_disabled",
        reason: "Preferência de lembretes desativada pelo usuário.",
      };
    }

    if (permissionStatus !== "granted") {
      return {
        plannedDoseId: dose.id,
        status: "blocked_or_disabled",
        reason: `Permissão indisponível (${permissionStatus}).`,
      };
    }

    const eligibility = this.evaluateEligibility(dose);
    if (!eligibility.eligible) {
      return {
        plannedDoseId: dose.id,
        status: "skipped_ineligible",
        reason: eligibility.reason,
      };
    }

    const resolvedDate = explicitDate || civilDate(new Date(dose.scheduledAt), timezone);
    const registry = this.readRegistry();
    const existing = registry[dose.id];
    const osIds = preloadedOsIds ?? new Set(this.driver.getAllScheduledNotificationIds());

    if (
      existing &&
      osIds.has(existing.platformNotificationId) &&
      this.isSameSnapshot(existing, dose, timezone, preferences.hideSensitiveDetailsOnLockScreen)
    ) {
      return {
        plannedDoseId: dose.id,
        status: "skipped_already_scheduled",
        platformNotificationId: existing.platformNotificationId,
      };
    }

    if (existing) {
      this.driver.cancelScheduledNotification(existing.platformNotificationId);
      delete registry[dose.id];
      this.writeRegistry(registry);
    }

    const content = buildReminderNotificationContent(dose, resolvedDate, timezone, preferences);
    try {
      const platformNotificationId = this.driver.scheduleLocalNotification(
        content,
        new Date(dose.scheduledAt),
      );
      registry[dose.id] = {
        plannedDoseId: dose.id,
        platformNotificationId,
        userId: this.userId,
        treatmentId: dose.treatmentId,
        medicationInUseId: dose.medicationInUseId,
        medicationName: dose.medicationName,
        scheduledAt: dose.scheduledAt,
        scheduledLocalTime: dose.scheduledLocalTime,
        timezone,
        date: resolvedDate,
        hideSensitiveDetailsOnLockScreen: preferences.hideSensitiveDetailsOnLockScreen,
        scheduledOnDeviceAt: this.nowProvider().toISOString(),
      };
      this.writeRegistry(registry);
      osIds.add(platformNotificationId);
      return {
        plannedDoseId: dose.id,
        status: "scheduled",
        platformNotificationId,
      };
    } catch (error) {
      return {
        plannedDoseId: dose.id,
        status: "failed",
        reason: error instanceof Error ? error.message : "Erro ao agendar notificação.",
      };
    }
  }

  async cancelReminder(plannedDoseId: string): Promise<boolean> {
    return this.enqueue(async () => {
      const registry = this.readRegistry();
      const existing = registry[plannedDoseId];
      if (!existing) return false;
      this.driver.cancelScheduledNotification(existing.platformNotificationId);
      delete registry[plannedDoseId];
      this.writeRegistry(registry);
      return true;
    });
  }

  async cancelRemindersByTreatment(treatmentId: string): Promise<number> {
    return this.enqueue(async () => {
      const registry = this.readRegistry();
      let cancelled = 0;
      for (const [id, record] of Object.entries(registry)) {
        if (record.treatmentId === treatmentId) {
          this.driver.cancelScheduledNotification(record.platformNotificationId);
          delete registry[id];
          cancelled += 1;
        }
      }
      if (cancelled > 0) this.writeRegistry(registry);
      return cancelled;
    });
  }

  async cancelAllManagedReminders(): Promise<number> {
    return this.enqueue(async () => {
      const registry = this.readRegistry();
      let cancelled = 0;
      for (const record of Object.values(registry)) {
        this.driver.cancelScheduledNotification(record.platformNotificationId);
        cancelled += 1;
      }
      this.storage.removeItem(this.registryKey());
      return cancelled;
    });
  }

  async handleDoseRecorded(plannedDoseId: string): Promise<boolean> {
    return this.cancelReminder(plannedDoseId);
  }

  async reconcileReminders(
    doses: Dose[],
    options?: ReconcileOptions,
  ): Promise<ReconcileRemindersResult> {
    return this.enqueue(async () => this.reconcileInternal(doses, options));
  }

  private reconcileInternal(
    doses: Dose[],
    options?: ReconcileOptions,
  ): ReconcileRemindersResult {
    const preferences = this.getPreferences();
    const permissionStatus = this.getPermissionStatus();
    const timezone = options?.timezone || this.defaultTimezone;
    const errors: string[] = [];

    if (!preferences.enabled) {
      const registry = this.readRegistry();
      let cancelledCount = 0;
      for (const record of Object.values(registry)) {
        this.driver.cancelScheduledNotification(record.platformNotificationId);
        cancelledCount += 1;
      }
      this.storage.removeItem(this.registryKey());
      return {
        scheduledCount: 0,
        rescheduledCount: 0,
        unchangedCount: 0,
        cancelledCount,
        skippedCount: doses.length,
        failedCount: 0,
        permissionStatus,
        preferenceEnabled: false,
        effectiveActive: false,
        truncatedByPlatformLimit: false,
        errors,
      };
    }

    if (permissionStatus !== "granted") {
      return {
        scheduledCount: 0,
        rescheduledCount: 0,
        unchangedCount: 0,
        cancelledCount: 0,
        skippedCount: doses.length,
        failedCount: 0,
        permissionStatus,
        preferenceEnabled: true,
        effectiveActive: false,
        truncatedByPlatformLimit: false,
        errors,
      };
    }

    const nowMs = this.nowProvider().getTime();
    let cancelledCount = 0;
    let skippedCount = 0;

    const eligible: Dose[] = [];
    const ineligibleExplicitIds = new Set<string>();

    for (const dose of doses) {
      const check = this.evaluateEligibility(dose);
      if (check.eligible) eligible.push(dose);
      else {
        skippedCount += 1;
        if (dose.id) ineligibleExplicitIds.add(dose.id);
      }
    }

    eligible.sort((a, b) => {
      const diff = new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime();
      return diff !== 0 ? diff : a.id.localeCompare(b.id);
    });

    const truncatedByPlatformLimit = eligible.length > this.maxPendingReminders;
    const capped = eligible.slice(0, this.maxPendingReminders);
    if (truncatedByPlatformLimit) skippedCount += eligible.length - capped.length;

    const desiredById = new Map<string, Dose>();
    for (const dose of capped) desiredById.set(dose.id, dose);

    const reconciledDatesSet = options?.reconciledDates ? new Set(options.reconciledDates) : null;
    const scopeTreatmentId = options?.scopeTreatmentId;

    const registry = this.readRegistry();
    let registryChanged = false;
    for (const [id, record] of Object.entries(registry)) {
      const recordMs = new Date(record.scheduledAt).getTime();
      const isPast = Number.isNaN(recordMs) || recordMs <= nowMs;
      const isExplicitlyIneligible = ineligibleExplicitIds.has(id);
      const inScope =
        (!reconciledDatesSet || reconciledDatesSet.has(record.date)) &&
        (!scopeTreatmentId || record.treatmentId === scopeTreatmentId);
      const missingFromDesired = inScope && !desiredById.has(id);

      if (isPast || isExplicitlyIneligible || missingFromDesired) {
        this.driver.cancelScheduledNotification(record.platformNotificationId);
        delete registry[id];
        registryChanged = true;
        cancelledCount += 1;
      }
    }
    if (registryChanged) this.writeRegistry(registry);

    const osIds = new Set(this.driver.getAllScheduledNotificationIds());
    let scheduledCount = 0;
    let rescheduledCount = 0;
    let unchangedCount = 0;
    let failedCount = 0;

    for (const dose of capped) {
      const currentRegistry = this.readRegistry();
      const existing = currentRegistry[dose.id];
      const presentInOs = Boolean(existing && osIds.has(existing.platformNotificationId));

      if (
        existing &&
        presentInOs &&
        this.isSameSnapshot(existing, dose, timezone, preferences.hideSensitiveDetailsOnLockScreen)
      ) {
        unchangedCount += 1;
        continue;
      }

      const wasReplacement = Boolean(existing);
      const doseDate = civilDate(new Date(dose.scheduledAt), timezone);
      const res = this.scheduleReminderInternal(dose, doseDate, timezone, osIds);
      if (res.status === "scheduled") {
        if (wasReplacement) rescheduledCount += 1;
        else scheduledCount += 1;
      } else if (res.status === "skipped_already_scheduled") {
        unchangedCount += 1;
      } else if (res.status === "failed") {
        failedCount += 1;
        if (res.reason) errors.push(`${dose.id}: ${res.reason}`);
      } else {
        skippedCount += 1;
      }
    }

    return {
      scheduledCount,
      rescheduledCount,
      unchangedCount,
      cancelledCount,
      skippedCount,
      failedCount,
      permissionStatus,
      preferenceEnabled: true,
      effectiveActive: true,
      truncatedByPlatformLimit,
      errors,
    };
  }

  async syncWithAgendaWindow(
    fetchAgenda: (date: string) => Promise<Agenda>,
    options?: { windowDays?: number; scopeTreatmentId?: string },
  ): Promise<ReconcileRemindersResult> {
    const windowDays = Math.max(1, options?.windowDays ?? this.schedulingWindowDays);
    const today = civilDate(this.nowProvider(), this.defaultTimezone);
    const dates = Array.from({ length: windowDays }, (_, idx) => shiftDate(today, idx));

    const doses: Dose[] = [];
    let resolvedTz = this.defaultTimezone;

    for (const date of dates) {
      const agenda = await fetchAgenda(date);
      if (agenda.timezone) resolvedTz = agenda.timezone;
      doses.push(...agenda.doses);
    }

    return this.reconcileReminders(doses, {
      reconciledDates: dates,
      scopeTreatmentId: options?.scopeTreatmentId,
      timezone: resolvedTz,
    });
  }

  /** Limpa apenas timers ativos em memória (ex.: no logout), sem apagar preferências salvas. */
  dispose(): void {
    this.driver.dispose();
  }

  private evaluateEligibility(dose: Dose): { eligible: boolean; reason?: string } {
    if (!dose.id || !dose.id.trim()) {
      return { eligible: false, reason: "Dose sem plannedDoseId válido." };
    }
    if (dose.scheduleStatus !== "SCHEDULED") {
      return { eligible: false, reason: `Status inelegível (${dose.scheduleStatus}).` };
    }
    if (dose.record !== null || dose.displayStatus === "TAKEN" || dose.displayStatus === "SKIPPED") {
      return { eligible: false, reason: "Dose já possui registro explícito." };
    }
    const ms = new Date(dose.scheduledAt).getTime();
    if (Number.isNaN(ms)) {
      return { eligible: false, reason: "Horário scheduledAt inválido." };
    }
    const nowMs = this.nowProvider().getTime();
    if (ms <= nowMs) {
      return { eligible: false, reason: "Dose no passado ou já vencida." };
    }
    const maxWindowMs = nowMs + this.schedulingWindowDays * 24 * 60 * 60 * 1000;
    if (ms > maxWindowMs) {
      return { eligible: false, reason: `Além da janela de ${this.schedulingWindowDays} dias.` };
    }
    return { eligible: true };
  }

  private isSameSnapshot(
    existing: ScheduledReminderRecord,
    incoming: Dose,
    timezone: string,
    hideSensitive: boolean,
  ): boolean {
    return (
      existing.scheduledAt === incoming.scheduledAt &&
      existing.scheduledLocalTime === incoming.scheduledLocalTime &&
      existing.timezone === timezone &&
      existing.hideSensitiveDetailsOnLockScreen === hideSensitive
    );
  }
}
