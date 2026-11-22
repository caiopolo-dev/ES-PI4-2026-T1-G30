import { buildCivilDateWindow } from '../api/doseCertaApi';
import { buildReminderNotificationContent } from './privacyFormatter';
import { ReminderPreferencesStore } from './preferencesStore';
import { ReminderRegistry } from './reminderRegistry';
import type { DoseCertaAgendaClient, PlannedDose } from '../types/api';
import type {
  KeyValueStorage,
  LocalNotificationDriver,
  MedicationNotificationService,
  NotificationPermissionStatus,
  ReconcileOptions,
  ReconcileRemindersResult,
  ReminderPreferences,
  ScheduledReminderRecord,
  ScheduleReminderResult,
} from '../types/reminders';

export const DEFAULT_SCHEDULING_WINDOW_DAYS = 7;
export const DEFAULT_MAX_PENDING_REMINDERS = 50;

export interface NotificationServiceOptions {
  userId: string;
  driver: LocalNotificationDriver;
  storage: KeyValueStorage;
  schedulingWindowDays?: number;
  maxPendingReminders?: number;
  nowProvider?: () => Date;
  defaultTimezone?: string;
}

/**
 * Serviço principal de lembretes de medicamentos para React Native (Issue #37).
 *
 * Princípios garantidos:
 * 1. Usa exclusivamente DosePrevista do backend como fonte de verdade (sem calcular posologia).
 * 2. Nunca cria nem altera RegistroDeDose (zero efeitos colaterais no estado da dose).
 * 3. Reconciliação idempotente e serializada por plannedDoseId + scheduledAt (sem duplicatas).
 * 4. Respeita preferência do usuário, estado de permissão, janela de programação e limite da plataforma.
 * 5. Permite cancelamento preciso ao alterar cronograma, encerrar tratamento, desabilitar preferência ou registrar dose.
 */
export class ReactNativeNotificationService implements MedicationNotificationService {
  private readonly userId: string;
  private readonly driver: LocalNotificationDriver;
  private readonly preferencesStore: ReminderPreferencesStore;
  private readonly registry: ReminderRegistry;
  private readonly schedulingWindowDays: number;
  private readonly maxPendingReminders: number;
  private readonly nowProvider: () => Date;
  private readonly defaultTimezone: string;

  /** Fila serializada para evitar race conditions e duplicatas em reconciliações concorrentes. */
  private operationQueue: Promise<unknown> = Promise.resolve();

  constructor(options: NotificationServiceOptions) {
    if (!options.userId || !options.userId.trim()) {
      throw new Error('userId é obrigatório para inicializar o serviço de lembretes.');
    }
    this.userId = options.userId.trim();
    this.driver = options.driver;
    this.nowProvider = options.nowProvider ?? (() => new Date());
    this.preferencesStore = new ReminderPreferencesStore(options.storage, this.nowProvider);
    this.registry = new ReminderRegistry(options.storage);
    this.schedulingWindowDays = Math.max(1, options.schedulingWindowDays ?? DEFAULT_SCHEDULING_WINDOW_DAYS);
    this.maxPendingReminders = Math.max(
      1,
      Math.min(
        options.maxPendingReminders ?? DEFAULT_MAX_PENDING_REMINDERS,
        options.driver.maxPendingNotifications,
      ),
    );
    this.defaultTimezone = options.defaultTimezone ?? 'America/Sao_Paulo';
  }

  private enqueue<T>(task: () => Promise<T>): Promise<T> {
    const next = this.operationQueue.then(task, task);
    this.operationQueue = next.then(
      () => undefined,
      () => undefined,
    );
    return next;
  }

  async getPermissionStatus(): Promise<NotificationPermissionStatus> {
    if (!this.driver.isAvailable()) {
      return 'unavailable';
    }
    return this.driver.getPermissionStatus();
  }

  async requestPermission(): Promise<NotificationPermissionStatus> {
    if (!this.driver.isAvailable()) {
      return 'unavailable';
    }
    return this.driver.requestPermission();
  }

  async getPreferences(): Promise<ReminderPreferences> {
    return this.preferencesStore.getPreferences(this.userId);
  }

  async getManagedReminders(): Promise<ScheduledReminderRecord[]> {
    return this.registry.getAll(this.userId);
  }

  /**
   * Habilita a preferência de lembretes do usuário.
   * Só é considerado efetivamente ativo quando a permissão do sistema também está 'granted'.
   */
  async enableReminders(
    plannedDosesToReconcile?: PlannedDose[],
  ): Promise<{
    preferences: ReminderPreferences;
    permissionStatus: NotificationPermissionStatus;
    effectiveActive: boolean;
    reconcileResult?: ReconcileRemindersResult;
  }> {
    const permissionStatus = await this.getPermissionStatus();
    const preferences = await this.preferencesStore.setEnabled(this.userId, true);
    const effectiveActive = preferences.enabled && permissionStatus === 'granted';

    if (effectiveActive && plannedDosesToReconcile) {
      const reconcileResult = await this.reconcileReminders(plannedDosesToReconcile);
      return { preferences, permissionStatus, effectiveActive, reconcileResult };
    }

    return { preferences, permissionStatus, effectiveActive };
  }

  /**
   * Desabilita a preferência de lembretes e cancela todos os lembretes pendentes
   * administrados pelo aplicativo.
   */
  async disableReminders(): Promise<{
    preferences: ReminderPreferences;
    cancelledCount: number;
  }> {
    const preferences = await this.preferencesStore.setEnabled(this.userId, false);
    const cancelledCount = await this.cancelAllManagedReminders();
    return { preferences, cancelledCount };
  }

  async setLockScreenPrivacy(hideSensitiveDetailsOnLockScreen: boolean): Promise<ReminderPreferences> {
    return this.preferencesStore.savePreferences(this.userId, {
      hideSensitiveDetailsOnLockScreen,
    });
  }

  /**
   * Agenda um único lembrete de forma idempotente para uma DosePrevista elegível.
   */
  async scheduleReminder(plannedDose: PlannedDose): Promise<ScheduleReminderResult> {
    return this.enqueue(() => this.scheduleReminderInternal(plannedDose));
  }

  private async scheduleReminderInternal(
    plannedDose: PlannedDose,
    preloadedOsIds?: Set<string>,
  ): Promise<ScheduleReminderResult> {
    const preferences = await this.preferencesStore.getPreferences(this.userId);
    const permissionStatus = await this.getPermissionStatus();

    if (!preferences.enabled) {
      return {
        plannedDoseId: plannedDose.id,
        status: 'blocked_or_disabled',
        reason: 'Preferência de lembretes desativada pelo usuário.',
      };
    }

    if (permissionStatus !== 'granted') {
      return {
        plannedDoseId: plannedDose.id,
        status: 'blocked_or_disabled',
        reason: `Permissão de notificações indisponível (${permissionStatus}).`,
      };
    }

    const eligibility = this.evaluateEligibility(plannedDose);
    if (!eligibility.eligible) {
      return {
        plannedDoseId: plannedDose.id,
        status: 'skipped_ineligible',
        reason: eligibility.reason,
      };
    }

    const existing = await this.registry.getByPlannedDoseId(this.userId, plannedDose.id);
    const osScheduledIds = preloadedOsIds ?? new Set(await this.driver.getAllScheduledNotificationIds());

    if (
      existing &&
      this.isSameScheduleSnapshot(existing, plannedDose, preferences.hideSensitiveDetailsOnLockScreen) &&
      osScheduledIds.has(existing.platformNotificationId)
    ) {
      return {
        plannedDoseId: plannedDose.id,
        status: 'skipped_already_scheduled',
        platformNotificationId: existing.platformNotificationId,
      };
    }

    // Se já existia agendamento anterior para o mesmo plannedDoseId com horário/versão diferente,
    // cancela antes de programar o substituto.
    if (existing) {
      try {
        await this.driver.cancelScheduledNotification(existing.platformNotificationId);
      } catch {
        // Prossegue para garantir atualização limpa mesmo se o SO já descartou o ID anterior.
      }
      await this.registry.remove(this.userId, plannedDose.id);
    }

    const content = buildReminderNotificationContent(plannedDose, preferences);
    const triggerDate = new Date(plannedDose.scheduledAt);

    try {
      await this.driver.ensureChannelConfigured();
      const platformNotificationId = await this.driver.scheduleLocalNotification(content, triggerDate);
      const record: ScheduledReminderRecord = {
        plannedDoseId: plannedDose.id,
        platformNotificationId,
        userId: this.userId,
        treatmentId: plannedDose.treatmentId,
        medicationInUseId: plannedDose.medicationInUseId,
        scheduledAt: plannedDose.scheduledAt,
        scheduledLocalTime: plannedDose.scheduledLocalTime,
        timezone: plannedDose.timezone || this.defaultTimezone,
        date: plannedDose.date || content.data.date,
        scheduleVersion: plannedDose.scheduleVersion,
        hideSensitiveDetailsOnLockScreen: preferences.hideSensitiveDetailsOnLockScreen,
        scheduledOnDeviceAt: this.nowProvider().toISOString(),
      };
      await this.registry.upsert(this.userId, record);
      osScheduledIds.add(platformNotificationId);

      return {
        plannedDoseId: plannedDose.id,
        status: 'scheduled',
        platformNotificationId,
      };
    } catch (error) {
      const reason = error instanceof Error ? error.message : 'Erro desconhecido ao agendar notificação.';
      return {
        plannedDoseId: plannedDose.id,
        status: 'failed',
        reason,
      };
    }
  }

  /**
   * Cancela o lembrete local vinculado a uma DosePrevista específica (por plannedDoseId).
   */
  async cancelReminder(plannedDoseId: string): Promise<boolean> {
    return this.enqueue(() => this.cancelReminderInternal(plannedDoseId));
  }

  private async cancelReminderInternal(plannedDoseId: string): Promise<boolean> {
    const existing = await this.registry.getByPlannedDoseId(this.userId, plannedDoseId);
    if (!existing) {
      return false;
    }
    try {
      await this.driver.cancelScheduledNotification(existing.platformNotificationId);
    } catch {
      // Ainda removemos do registro local para manter consistência do app.
    }
    await this.registry.remove(this.userId, plannedDoseId);
    return true;
  }

  /**
   * Cancela imediatamente todos os lembretes futuros associados a um tratamento encerrado/cancelado.
   * Não altera histórico nem registros de doses.
   */
  async cancelRemindersByTreatment(treatmentId: string): Promise<number> {
    return this.enqueue(async () => {
      const records = await this.registry.findByTreatmentId(this.userId, treatmentId);
      let cancelled = 0;
      for (const record of records) {
        try {
          await this.driver.cancelScheduledNotification(record.platformNotificationId);
        } catch {
          // Segue limpando os demais lembretes do tratamento.
        }
        await this.registry.remove(this.userId, record.plannedDoseId);
        cancelled += 1;
      }
      return cancelled;
    });
  }

  /**
   * Cancela todos os lembretes pendentes administrados pelo aplicativo para o usuário atual.
   */
  async cancelAllManagedReminders(): Promise<number> {
    return this.enqueue(async () => {
      const records = await this.registry.getAll(this.userId);
      let cancelled = 0;
      for (const record of records) {
        try {
          await this.driver.cancelScheduledNotification(record.platformNotificationId);
        } catch {
          // Continua cancelamento em lote sem interromper por falha isolada.
        }
        cancelled += 1;
      }
      await this.registry.clear(this.userId);
      return cancelled;
    });
  }

  /**
   * Política para dose já registrada (TAKEN ou SKIPPED):
   * cancela eventual lembrete pendente dessa ocorrência sem tocar no histórico.
   */
  async handleDoseRecorded(plannedDoseId: string): Promise<boolean> {
    return this.cancelReminder(plannedDoseId);
  }

  /**
   * Reconcilia de forma idempotente os agendamentos locais com as doses previstas do backend:
   * - Se preferência estiver desligada -> cancela pendentes e retorna sem agendar.
   * - Se permissão não estiver 'granted' -> não agenda e sinaliza effectiveActive = false.
   * - Remove lembretes expirados, cancelados, já registrados ou que saíram da agenda após edição de cronograma.
   * - Mantém intactos lembretes já agendados com o mesmo plannedDoseId e scheduledAt (sem duplicidade).
   * - Reagenda lembretes cujo horário/versão mudou.
   * - Aplica janela suportada e limite máximo de notificações pendentes da plataforma.
   */
  async reconcileReminders(
    plannedDoses: PlannedDose[],
    options?: ReconcileOptions,
  ): Promise<ReconcileRemindersResult> {
    return this.enqueue(() => this.reconcileRemindersInternal(plannedDoses, options));
  }

  private async reconcileRemindersInternal(
    plannedDoses: PlannedDose[],
    options?: ReconcileOptions,
  ): Promise<ReconcileRemindersResult> {
    const preferences = await this.preferencesStore.getPreferences(this.userId);
    const permissionStatus = await this.getPermissionStatus();
    const errors: string[] = [];

    if (!preferences.enabled) {
      const existing = await this.registry.getAll(this.userId);
      let cancelledCount = 0;
      for (const item of existing) {
        try {
          await this.driver.cancelScheduledNotification(item.platformNotificationId);
        } catch {
          // Ignora falha individual ao limpar lembrete obsoleto.
        }
        cancelledCount += 1;
      }
      await this.registry.clear(this.userId);

      return {
        scheduledCount: 0,
        rescheduledCount: 0,
        unchangedCount: 0,
        cancelledCount,
        skippedCount: plannedDoses.length,
        failedCount: 0,
        permissionStatus,
        preferenceEnabled: false,
        effectiveActive: false,
        truncatedByPlatformLimit: false,
        errors,
      };
    }

    if (permissionStatus !== 'granted') {
      return {
        scheduledCount: 0,
        rescheduledCount: 0,
        unchangedCount: 0,
        cancelledCount: 0,
        skippedCount: plannedDoses.length,
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

    // 1. Filtra doses elegíveis (futuras, SCHEDULED, sem registro, do usuário, dentro da janela)
    const eligibleDoses: PlannedDose[] = [];
    const ineligibleExplicitIds = new Set<string>();

    for (const dose of plannedDoses) {
      const evaluation = this.evaluateEligibility(dose);
      if (evaluation.eligible) {
        eligibleDoses.push(dose);
      } else {
        skippedCount += 1;
        if (dose.id) {
          ineligibleExplicitIds.add(dose.id);
        }
      }
    }

    // Ordena cronologicamente para priorizar as doses mais próximas ao aplicar o limite da plataforma.
    eligibleDoses.sort((a, b) => {
      const diff = new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime();
      return diff !== 0 ? diff : a.id.localeCompare(b.id);
    });

    const truncatedByPlatformLimit = eligibleDoses.length > this.maxPendingReminders;
    const cappedEligible = eligibleDoses.slice(0, this.maxPendingReminders);
    if (truncatedByPlatformLimit) {
      skippedCount += eligibleDoses.length - cappedEligible.length;
    }

    const desiredById = new Map<string, PlannedDose>();
    for (const dose of cappedEligible) {
      desiredById.set(dose.id, dose);
    }

    const reconciledDatesSet = options?.reconciledDates ? new Set(options.reconciledDates) : null;
    const scopeTreatmentId = options?.scopeTreatmentId;

    // 2. Cancela lembretes locais que expiraram, ficaram inelegíveis ou sumiram após alteração de cronograma/tratamento.
    const existingRecords = await this.registry.getAll(this.userId);
    const toRemoveIds: string[] = [];

    for (const record of existingRecords) {
      const recordMs = new Date(record.scheduledAt).getTime();
      const isPast = Number.isNaN(recordMs) || recordMs <= nowMs;
      const isExplicitlyIneligible = ineligibleExplicitIds.has(record.plannedDoseId);
      const isInReconciledScope =
        (!reconciledDatesSet || reconciledDatesSet.has(record.date)) &&
        (!scopeTreatmentId || record.treatmentId === scopeTreatmentId);
      const missingFromDesired = isInReconciledScope && !desiredById.has(record.plannedDoseId);

      if (isPast || isExplicitlyIneligible || missingFromDesired) {
        try {
          await this.driver.cancelScheduledNotification(record.platformNotificationId);
        } catch {
          // Segue limpeza do registro mesmo se o SO já disparou/removeu a notificação.
        }
        toRemoveIds.push(record.plannedDoseId);
        cancelledCount += 1;
      }
    }
    await this.registry.removeMany(this.userId, toRemoveIds);

    // 3. Reconcilia doses elegíveis (mantém idempotentes, reagenda alteradas, agenda novas).
    const osScheduledIds = new Set(await this.driver.getAllScheduledNotificationIds());
    let scheduledCount = 0;
    let rescheduledCount = 0;
    let unchangedCount = 0;
    let failedCount = 0;

    for (const dose of cappedEligible) {
      const existing = await this.registry.getByPlannedDoseId(this.userId, dose.id);
      const alreadyPresentInOs = Boolean(existing && osScheduledIds.has(existing.platformNotificationId));

      if (
        existing &&
        alreadyPresentInOs &&
        this.isSameScheduleSnapshot(existing, dose, preferences.hideSensitiveDetailsOnLockScreen)
      ) {
        unchangedCount += 1;
        continue;
      }

      const wasReplacement = Boolean(existing);
      const result = await this.scheduleReminderInternal(dose, osScheduledIds);
      if (result.status === 'scheduled') {
        if (wasReplacement) {
          rescheduledCount += 1;
        } else {
          scheduledCount += 1;
        }
      } else if (result.status === 'skipped_already_scheduled') {
        unchangedCount += 1;
      } else if (result.status === 'failed') {
        failedCount += 1;
        if (result.reason) {
          errors.push(`${dose.id}: ${result.reason}`);
        }
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

  /**
   * Consulta a agenda real do backend (GET /doses?date=AAAA-MM-DD) dentro da janela de programação
   * configurada (ex.: próximos 7 dias) e reconcilia os lembretes futuros automaticamente.
   */
  async syncWithAgendaWindow(
    agendaClient: Pick<DoseCertaAgendaClient, 'getDailyAgenda'>,
    options?: { windowDays?: number; scopeTreatmentId?: string; signal?: AbortSignal },
  ): Promise<ReconcileRemindersResult> {
    const windowDays = options?.windowDays ?? this.schedulingWindowDays;
    const dates = buildCivilDateWindow(this.nowProvider(), windowDays, this.defaultTimezone);

    const allDoses: PlannedDose[] = [];
    for (const date of dates) {
      const response = await agendaClient.getDailyAgenda(date, options?.signal);
      for (const dose of response.doses) {
        allDoses.push({
          ...dose,
          date: dose.date ?? response.date,
          timezone: dose.timezone ?? response.timezone,
        });
      }
    }

    return this.reconcileReminders(allDoses, {
      reconciledDates: dates,
      scopeTreatmentId: options?.scopeTreatmentId,
    });
  }

  private evaluateEligibility(plannedDose: PlannedDose): { eligible: boolean; reason?: string } {
    if (!plannedDose.id || !plannedDose.id.trim()) {
      return { eligible: false, reason: 'Ocorrência sem plannedDoseId válido.' };
    }
    if (plannedDose.userId && plannedDose.userId !== this.userId) {
      return { eligible: false, reason: 'Dose pertence a outro usuário.' };
    }
    if (plannedDose.scheduleStatus !== 'SCHEDULED') {
      return { eligible: false, reason: `Status de agenda inelegível (${plannedDose.scheduleStatus}).` };
    }
    if (plannedDose.record !== null || plannedDose.displayStatus === 'TAKEN' || plannedDose.displayStatus === 'SKIPPED') {
      return { eligible: false, reason: 'Dose já possui registro explícito (não deve ser agendada).' };
    }

    const scheduledMs = new Date(plannedDose.scheduledAt).getTime();
    if (Number.isNaN(scheduledMs)) {
      return { eligible: false, reason: `Horário scheduledAt inválido: ${plannedDose.scheduledAt}` };
    }

    const nowMs = this.nowProvider().getTime();
    if (scheduledMs <= nowMs) {
      return { eligible: false, reason: 'Dose no passado ou já vencida.' };
    }

    const windowEndMs = nowMs + this.schedulingWindowDays * 24 * 60 * 60 * 1000;
    if (scheduledMs > windowEndMs) {
      return {
        eligible: false,
        reason: `Dose além da janela suportada de ${this.schedulingWindowDays} dias.`,
      };
    }

    return { eligible: true };
  }

  private isSameScheduleSnapshot(
    existing: ScheduledReminderRecord,
    incoming: PlannedDose,
    hideSensitiveDetailsOnLockScreen: boolean,
  ): boolean {
    const incomingTz = incoming.timezone || this.defaultTimezone;
    const sameTime = existing.scheduledAt === incoming.scheduledAt;
    const sameLocalTime = existing.scheduledLocalTime === incoming.scheduledLocalTime;
    const sameTz = existing.timezone === incomingTz;
    const samePrivacy = existing.hideSensitiveDetailsOnLockScreen === hideSensitiveDetailsOnLockScreen;
    const sameVersion =
      incoming.scheduleVersion === undefined ||
      existing.scheduleVersion === undefined ||
      existing.scheduleVersion === incoming.scheduleVersion;
    return sameTime && sameLocalTime && sameTz && samePrivacy && sameVersion;
  }
}
