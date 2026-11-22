import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

import { DoseCertaApi } from '../src/api/doseCertaApi';
import { ExpoNotificationDriver } from '../src/notifications/expoNotificationDriver';
import { attachNotificationInteractionHandler } from '../src/notifications/interactionHandler';
import { ReactNativeNotificationService } from '../src/notifications/notificationService';
import { InMemoryKeyValueStorage } from '../src/notifications/storage';
import type { DailyAgendaResponse, DoseAction, DoseCertaAgendaClient, DoseRecordResponse, PlannedDose } from '../src/types/api';
import type {
  LocalNotificationDriver,
  NotificationPermissionStatus,
  PlatformNotificationContent,
  ReminderNavigationTarget,
  ReminderNotificationPayload,
} from '../src/types/reminders';

interface ScheduledOsNotification {
  id: string;
  content: PlatformNotificationContent;
  triggerDate: Date;
}

/**
 * Driver fake determinístico simulando o agendador local de notificações do SO (Android/iOS)
 * para testes reproduzíveis sem emulador.
 */
class FakeLocalNotificationDriver implements LocalNotificationDriver {
  readonly platformName: 'ios' | 'android' | 'unsupported';
  readonly maxPendingNotifications: number;

  permissionStatus: NotificationPermissionStatus;
  available = true;
  failNextSchedule = false;
  scheduleCalls = 0;
  cancelCalls = 0;
  channelConfigured = false;
  readonly scheduled = new Map<string, ScheduledOsNotification>();

  private seq = 0;
  private readonly listeners = new Set<(payload: ReminderNotificationPayload) => void>();
  private coldStartPayload: ReminderNotificationPayload | null = null;

  constructor(options?: {
    platformName?: 'ios' | 'android' | 'unsupported';
    permissionStatus?: NotificationPermissionStatus;
    maxPendingNotifications?: number;
  }) {
    this.platformName = options?.platformName ?? 'android';
    this.permissionStatus = options?.permissionStatus ?? 'granted';
    this.maxPendingNotifications = options?.maxPendingNotifications ?? 50;
  }

  isAvailable(): boolean {
    return this.available && this.platformName !== 'unsupported';
  }

  async ensureChannelConfigured(): Promise<void> {
    this.channelConfigured = true;
  }

  async getPermissionStatus(): Promise<NotificationPermissionStatus> {
    return this.isAvailable() ? this.permissionStatus : 'unavailable';
  }

  async requestPermission(): Promise<NotificationPermissionStatus> {
    return this.getPermissionStatus();
  }

  async scheduleLocalNotification(content: PlatformNotificationContent, triggerDate: Date): Promise<string> {
    this.scheduleCalls += 1;
    if (this.failNextSchedule) {
      this.failNextSchedule = false;
      throw new Error('Falha simulada no AlarmManager do SO');
    }
    this.seq += 1;
    const id = `os-notif-${this.seq}`;
    this.scheduled.set(id, { id, content, triggerDate });
    return id;
  }

  async cancelScheduledNotification(platformNotificationId: string): Promise<void> {
    this.cancelCalls += 1;
    this.scheduled.delete(platformNotificationId);
  }

  async getAllScheduledNotificationIds(): Promise<string[]> {
    return Array.from(this.scheduled.keys());
  }

  addResponseListener(listener: (payload: ReminderNotificationPayload) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  async getInitialNotificationResponse(): Promise<ReminderNotificationPayload | null> {
    return this.coldStartPayload;
  }

  simulateUserTapWhileRunningOrMinimized(platformNotificationId: string): void {
    const entry = this.scheduled.get(platformNotificationId);
    if (!entry) {
      throw new Error(`Notificação ${platformNotificationId} não encontrada no SO simulado.`);
    }
    for (const listener of this.listeners) {
      listener(entry.content.data);
    }
  }

  simulateColdStartFromClosedApp(platformNotificationId: string): void {
    const entry = this.scheduled.get(platformNotificationId);
    if (!entry) {
      throw new Error(`Notificação ${platformNotificationId} não encontrada no SO simulado.`);
    }
    this.coldStartPayload = entry.content.data;
  }
}

class FakeAgendaApiClient implements DoseCertaAgendaClient {
  readonly agendasByDate = new Map<string, DailyAgendaResponse>();
  readonly recordCalls: Array<{ plannedDoseId: string; action: DoseAction }> = [];
  agendaFetchCount = 0;

  setAgenda(date: string, doses: PlannedDose[], timezone = 'America/Sao_Paulo'): void {
    this.agendasByDate.set(date, { date, timezone, doses });
  }

  async getDailyAgenda(date: string): Promise<DailyAgendaResponse> {
    this.agendaFetchCount += 1;
    return (
      this.agendasByDate.get(date) ?? {
        date,
        timezone: 'America/Sao_Paulo',
        doses: [],
      }
    );
  }

  async recordDose(plannedDoseId: string, action: DoseAction): Promise<DoseRecordResponse> {
    this.recordCalls.push({ plannedDoseId, action });
    return {
      plannedDoseId,
      action,
      scheduledAt: '2026-11-22T11:00:00Z',
      scheduleVersion: 1,
      actualTime: '2026-11-22T11:01:00Z',
      recordedAt: '2026-11-22T11:01:05Z',
    };
  }
}

function makeDose(overrides: Partial<PlannedDose> = {}): PlannedDose {
  return {
    id: overrides.id ?? 'dose-001',
    treatmentId: overrides.treatmentId ?? 'treat-001',
    medicationInUseId: overrides.medicationInUseId ?? 'med-001',
    medicationName: overrides.medicationName ?? 'Losartana 50 mg',
    plannedQuantity: overrides.plannedQuantity ?? 1,
    scheduledAt: overrides.scheduledAt ?? '2026-11-22T23:00:00Z', // 20:00 America/Sao_Paulo
    scheduledLocalTime: overrides.scheduledLocalTime ?? '20:00',
    scheduleStatus: overrides.scheduleStatus ?? 'SCHEDULED',
    displayStatus: overrides.displayStatus ?? 'UPCOMING',
    record: overrides.record !== undefined ? overrides.record : null,
    scheduleVersion: overrides.scheduleVersion ?? 1,
    timezone: overrides.timezone ?? 'America/Sao_Paulo',
    date: overrides.date ?? '2026-11-22',
    userId: overrides.userId,
  };
}

const FIXED_NOW = new Date('2026-11-22T15:00:00Z'); // 12:00 America/Sao_Paulo

describe('Issue #37 — Lembretes de Medicamentos em React Native', () => {
  it('1. Agenda lembrete para uma dose futura respeitando horário/fuso do backend e privacidade na tela bloqueada', async () => {
    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    const dose = makeDose({
      id: 'dose-future-1',
      medicationName: 'Losartana 50 mg',
      scheduledAt: '2026-11-22T23:00:00Z',
      scheduledLocalTime: '20:00',
    });

    const result = await service.scheduleReminder(dose);
    assert.equal(result.status, 'scheduled');
    assert.ok(result.platformNotificationId);
    assert.equal(driver.scheduled.size, 1);

    const osNotif = driver.scheduled.get(result.platformNotificationId)!;
    assert.equal(osNotif.triggerDate.toISOString(), '2026-11-22T23:00:00.000Z');
    assert.equal(osNotif.content.data.plannedDoseId, 'dose-future-1');
    // Privacidade padrão na tela bloqueada: não expõe o nome do medicamento.
    assert.ok(!osNotif.content.title.includes('Losartana'));
    assert.ok(!osNotif.content.body.includes('Losartana'));
    assert.ok(osNotif.content.body.includes('20:00'));

    const managed = await service.getManagedReminders();
    assert.equal(managed.length, 1);
    assert.equal(managed[0].plannedDoseId, 'dose-future-1');
    assert.equal(managed[0].platformNotificationId, result.platformNotificationId);
  });

  it('2. Agenda dois medicamentos em horários diferentes mantendo identidades distintas', async () => {
    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    const doses = [
      makeDose({
        id: 'dose-med-a',
        medicationInUseId: 'med-a',
        medicationName: 'Losartana 50 mg',
        scheduledAt: '2026-11-22T18:00:00Z',
        scheduledLocalTime: '15:00',
      }),
      makeDose({
        id: 'dose-med-b',
        medicationInUseId: 'med-b',
        medicationName: 'Sinvastatina 20 mg',
        scheduledAt: '2026-11-22T23:00:00Z',
        scheduledLocalTime: '20:00',
      }),
    ];

    const summary = await service.reconcileReminders(doses);
    assert.equal(summary.scheduledCount, 2);
    assert.equal(summary.effectiveActive, true);
    assert.equal(driver.scheduled.size, 2);

    const managed = await service.getManagedReminders();
    assert.deepEqual(
      managed.map((m) => m.plannedDoseId).sort(),
      ['dose-med-a', 'dose-med-b'],
    );
  });

  it('3. Preferência desligada impede novos agendamentos e limpa todos os lembretes pendentes; reativação reconcilia', async () => {
    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      nowProvider: () => FIXED_NOW,
    });

    const doses = [
      makeDose({ id: 'dose-1', scheduledAt: '2026-11-22T19:00:00Z', scheduledLocalTime: '16:00' }),
      makeDose({ id: 'dose-2', scheduledAt: '2026-11-22T23:00:00Z', scheduledLocalTime: '20:00' }),
    ];

    // Estado padrão inicial: desligado -> não agenda.
    const initialReconcile = await service.reconcileReminders(doses);
    assert.equal(initialReconcile.preferenceEnabled, false);
    assert.equal(initialReconcile.effectiveActive, false);
    assert.equal(driver.scheduled.size, 0);

    // Liga e agenda
    await service.enableReminders(doses);
    assert.equal(driver.scheduled.size, 2);

    // Desliga -> cancela pendentes administrados pelo app
    const disabled = await service.disableReminders();
    assert.equal(disabled.preferences.enabled, false);
    assert.equal(disabled.cancelledCount, 2);
    assert.equal(driver.scheduled.size, 0);
    assert.equal((await service.getManagedReminders()).length, 0);

    // Nova consulta da agenda enquanto desligado continua sem agendar
    const afterDisable = await service.reconcileReminders(doses);
    assert.equal(afterDisable.scheduledCount, 0);
    assert.equal(driver.scheduled.size, 0);

    // Reativa -> reconcilia próximas doses elegíveis
    const reactivated = await service.enableReminders(doses);
    assert.equal(reactivated.effectiveActive, true);
    assert.equal(reactivated.reconcileResult?.scheduledCount, 2);
    assert.equal(driver.scheduled.size, 2);
  });

  it('4. Permissão negada, bloqueada ou indisponível não marca como ativo e não impede agenda nem registro manual', async () => {
    const api = new FakeAgendaApiClient();
    const dose = makeDose({ id: 'dose-manual-ok', scheduledAt: '2026-11-22T23:00:00Z', scheduledLocalTime: '20:00' });
    api.setAgenda('2026-11-22', [dose]);

    for (const status of ['denied', 'blocked', 'unavailable'] as const) {
      const driver = new FakeLocalNotificationDriver({ permissionStatus: status });
      const storage = new InMemoryKeyValueStorage();
      const service = new ReactNativeNotificationService({
        userId: 'user-1',
        driver,
        storage,
        nowProvider: () => FIXED_NOW,
      });

      const enabledAttempt = await service.enableReminders();
      assert.equal(enabledAttempt.effectiveActive, false, `Status ${status} nunca pode aparecer ativo`);

      const agenda = await api.getDailyAgenda('2026-11-22');
      assert.equal(agenda.doses.length, 1);

      const reconcile = await service.reconcileReminders(agenda.doses);
      assert.equal(reconcile.effectiveActive, false);
      assert.equal(reconcile.scheduledCount, 0);
      assert.equal(driver.scheduled.size, 0);

      // Registro manual explícito continua funcionando normalmente mesmo sem notificações!
      const recordResp = await api.recordDose(dose.id, 'TAKEN');
      assert.equal(recordResp.action, 'TAKEN');
    }
  });

  it('5. Consultas repetidas e concorrentes da agenda são idempotentes e não geram duplicatas', async () => {
    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    const doses = [
      makeDose({ id: 'dose-dup-1', scheduledAt: '2026-11-22T19:00:00Z', scheduledLocalTime: '16:00' }),
      makeDose({ id: 'dose-dup-2', scheduledAt: '2026-11-22T23:00:00Z', scheduledLocalTime: '20:00' }),
    ];

    // Dispara 4 reconciliações simultâneas (ex.: re-renders ou navegações rápidas)
    const results = await Promise.all([
      service.reconcileReminders(doses),
      service.reconcileReminders(doses),
      service.reconcileReminders(doses),
      service.reconcileReminders(doses),
    ]);

    assert.equal(driver.scheduled.size, 2);
    assert.equal(driver.scheduleCalls, 2);
    assert.equal(results[0].scheduledCount, 2);
    assert.equal(results[1].unchangedCount, 2);
    assert.equal(results[2].unchangedCount, 2);
    assert.equal(results[3].unchangedCount, 2);
  });

  it('6. Alteração de horário cancela lembretes futuros antigos e agenda os substitutos sem alterar histórico', async () => {
    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();

    const pastTakenDose = makeDose({
      id: 'dose-past-taken',
      scheduledAt: '2026-11-22T11:00:00Z', // 08:00 passado
      scheduledLocalTime: '08:00',
      displayStatus: 'TAKEN',
      record: {
        id: 'rec-1',
        action: 'TAKEN',
        actualTime: '2026-11-22T11:02:00Z',
        recordedAt: '2026-11-22T11:02:10Z',
      },
    });

    const oldFutureDoseV1 = makeDose({
      id: 'dose-v1-2000',
      scheduledAt: '2026-11-22T23:00:00Z', // 20:00
      scheduledLocalTime: '20:00',
      scheduleVersion: 1,
    });

    await service.reconcileReminders([pastTakenDose, oldFutureDoseV1], { reconciledDates: ['2026-11-22'] });
    assert.equal(driver.scheduled.size, 1);
    const oldNotifId = (await service.getManagedReminders())[0].platformNotificationId;

    // Cronograma alterado de 20:00 (v1) para 21:00 (v2): backend cancela dose-v1-2000 e gera dose-v2-2100.
    const cancelledOldDose = makeDose({
      ...oldFutureDoseV1,
      scheduleStatus: 'CANCELLED',
    });
    const newFutureDoseV2 = makeDose({
      id: 'dose-v2-2100',
      scheduledAt: '2026-11-23T00:00:00Z', // 21:00 America/Sao_Paulo
      scheduledLocalTime: '21:00',
      scheduleVersion: 2,
      date: '2026-11-22',
    });

    const afterEdit = await service.reconcileReminders(
      [pastTakenDose, cancelledOldDose, newFutureDoseV2],
      { reconciledDates: ['2026-11-22'] },
    );

    assert.equal(afterEdit.cancelledCount, 1);
    assert.equal(afterEdit.scheduledCount, 1);
    assert.equal(driver.scheduled.has(oldNotifId), false);
    assert.equal(driver.scheduled.size, 1);

    const managed = await service.getManagedReminders();
    assert.equal(managed.length, 1);
    assert.equal(managed[0].plannedDoseId, 'dose-v2-2100');
    assert.equal(managed[0].scheduledLocalTime, '21:00');
    // O registro histórico passado permanece intocado e não agendado.
    assert.equal(pastTakenDose.record?.action, 'TAKEN');
  });

  it('7. Encerramento de tratamento cancela apenas os lembretes futuros daquele tratamento', async () => {
    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    await service.reconcileReminders([
      makeDose({ id: 'dose-t1-a', treatmentId: 'treat-1', scheduledAt: '2026-11-22T18:00:00Z', scheduledLocalTime: '15:00' }),
      makeDose({ id: 'dose-t1-b', treatmentId: 'treat-1', scheduledAt: '2026-11-22T23:00:00Z', scheduledLocalTime: '20:00' }),
      makeDose({ id: 'dose-t2-a', treatmentId: 'treat-2', scheduledAt: '2026-11-22T21:00:00Z', scheduledLocalTime: '18:00' }),
    ]);

    assert.equal(driver.scheduled.size, 3);

    const cancelled = await service.cancelRemindersByTreatment('treat-1');
    assert.equal(cancelled, 2);
    assert.equal(driver.scheduled.size, 1);

    const remaining = await service.getManagedReminders();
    assert.equal(remaining.length, 1);
    assert.equal(remaining[0].treatmentId, 'treat-2');
    assert.equal(remaining[0].plannedDoseId, 'dose-t2-a');
  });

  it('8 e 9. Entrega e toque no lembrete (app minimizado ou fechado/cold-start) direcionam à dose SEM criar RegistroDeDose', async () => {
    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const api = new FakeAgendaApiClient();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    const dose = makeDose({
      id: 'dose-tap-01',
      treatmentId: 'treat-9',
      medicationInUseId: 'med-9',
      scheduledAt: '2026-11-22T22:00:00Z',
      scheduledLocalTime: '19:00',
      date: '2026-11-22',
    });

    const scheduled = await service.scheduleReminder(dose);
    assert.equal(scheduled.status, 'scheduled');
    const notifId = scheduled.platformNotificationId!;

    const navigatedTargets: ReminderNavigationTarget[] = [];
    const handler = attachNotificationInteractionHandler(driver, (target) => {
      navigatedTargets.push(target);
    });

    // Cenário A: app minimizado/em segundo plano -> usuário toca na notificação
    driver.simulateUserTapWhileRunningOrMinimized(notifId);
    assert.equal(navigatedTargets.length, 1);
    assert.deepEqual(navigatedTargets[0], {
      screen: 'Agenda',
      params: {
        date: '2026-11-22',
        highlightPlannedDoseId: 'dose-tap-01',
        treatmentId: 'treat-9',
        medicationInUseId: 'med-9',
      },
    });
    // Regra de ouro: zero RegistroDeDose automático!
    assert.equal(api.recordCalls.length, 0);

    // Cenário B: app fechado (cold start) -> usuário abre o app tocando no lembrete
    driver.simulateColdStartFromClosedApp(notifId);
    const coldStartTarget = await handler.checkInitialNotification();
    assert.ok(coldStartTarget);
    assert.equal(coldStartTarget.params.highlightPlannedDoseId, 'dose-tap-01');
    assert.equal(api.recordCalls.length, 0);

    handler.dispose();

    // Somente ação explícita do usuário ("Tomei") chama PUT /doses/{id}/record e cancela o lembrete pendente.
    await api.recordDose('dose-tap-01', 'TAKEN');
    const cancelledOnRecord = await service.handleDoseRecorded('dose-tap-01');
    assert.equal(api.recordCalls.length, 1);
    assert.equal(cancelledOnRecord, true);
    assert.equal(driver.scheduled.size, 0);
  });

  it('10. Respeita janela suportada (7 dias), limite máximo de agendamentos pendentes e isolamento de usuário', async () => {
    const driver = new FakeLocalNotificationDriver({
      permissionStatus: 'granted',
      maxPendingNotifications: 2,
    });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-A',
      driver,
      storage,
      schedulingWindowDays: 7,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();

    const doses: PlannedDose[] = [
      makeDose({ id: 'dose-other-user', userId: 'user-B', scheduledAt: '2026-11-22T17:00:00Z', scheduledLocalTime: '14:00' }),
      makeDose({ id: 'dose-too-far', scheduledAt: '2026-12-15T11:00:00Z', scheduledLocalTime: '08:00', date: '2026-12-15' }),
      makeDose({ id: 'dose-ok-3', scheduledAt: '2026-11-24T11:00:00Z', scheduledLocalTime: '08:00', date: '2026-11-24' }),
      makeDose({ id: 'dose-ok-1', scheduledAt: '2026-11-22T18:00:00Z', scheduledLocalTime: '15:00', date: '2026-11-22' }),
      makeDose({ id: 'dose-ok-2', scheduledAt: '2026-11-23T11:00:00Z', scheduledLocalTime: '08:00', date: '2026-11-23' }),
    ];

    const result = await service.reconcileReminders(doses);
    assert.equal(result.scheduledCount, 2);
    assert.equal(result.truncatedByPlatformLimit, true);

    // Deve priorizar cronologicamente as 2 doses mais próximas do user-A (dose-ok-1 e dose-ok-2)
    const managed = await service.getManagedReminders();
    assert.deepEqual(
      managed.map((m) => m.plannedDoseId).sort(),
      ['dose-ok-1', 'dose-ok-2'],
    );
  });

  it('11. Integração com GET /doses?date=AAAA-MM-DD via DoseCertaApi + syncWithAgendaWindow e tratamento de falha de agendamento', async () => {
    const requestedUrls: string[] = [];
    const mockFetch: typeof fetch = async (input) => {
      const url = String(input);
      requestedUrls.push(url);
      if (url.includes('date=2026-11-22')) {
        return new Response(
          JSON.stringify({
            date: '2026-11-22',
            timezone: 'America/Sao_Paulo',
            doses: [makeDose({ id: 'dose-http-1', scheduledAt: '2026-11-22T22:00:00Z', scheduledLocalTime: '19:00' })],
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      return new Response(
        JSON.stringify({ date: '2026-11-23', timezone: 'America/Sao_Paulo', doses: [] }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      );
    };

    const apiClient = new DoseCertaApi({
      baseUrl: 'http://127.0.0.1:8080',
      getAuthToken: () => 'token-valid',
      fetchImpl: mockFetch,
    });

    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      schedulingWindowDays: 2,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    driver.failNextSchedule = true;

    const failedSync = await service.syncWithAgendaWindow(apiClient, { windowDays: 2 });
    assert.equal(requestedUrls.length, 2);
    assert.ok(requestedUrls[0].endsWith('/doses?date=2026-11-22'));
    assert.ok(requestedUrls[1].endsWith('/doses?date=2026-11-23'));
    assert.equal(failedSync.failedCount, 1);
    assert.equal(failedSync.errors.length, 1);

    // Próxima sincronização se recupera normalmente e agenda a dose
    const recoveredSync = await service.syncWithAgendaWindow(apiClient, { windowDays: 2 });
    assert.equal(recoveredSync.scheduledCount, 1);
    assert.equal(recoveredSync.failedCount, 0);
  });

  it('12. ExpoNotificationDriver mapeia corretamente estados de permissão (granted, undetermined, denied, blocked, unavailable)', async () => {
    const unavailableDriver = new ExpoNotificationDriver({
      platformName: 'android',
      moduleLoader: async () => null,
    });
    assert.equal(await unavailableDriver.getPermissionStatus(), 'unavailable');

    const blockedDriver = new ExpoNotificationDriver({
      platformName: 'android',
      moduleLoader: async () => ({
        getPermissionsAsync: async () => ({ status: 'denied', canAskAgain: false }),
        requestPermissionsAsync: async () => ({ status: 'denied', canAskAgain: false }),
        scheduleNotificationAsync: async () => 'id',
        cancelScheduledNotificationAsync: async () => undefined,
        getAllScheduledNotificationsAsync: async () => [],
        addNotificationResponseReceivedListener: () => ({ remove: () => undefined }),
      }),
    });
    assert.equal(await blockedDriver.getPermissionStatus(), 'blocked');

    const deniedDriver = new ExpoNotificationDriver({
      platformName: 'ios',
      moduleLoader: async () => ({
        getPermissionsAsync: async () => ({ status: 'denied', canAskAgain: true }),
        requestPermissionsAsync: async () => ({ status: 'granted', canAskAgain: true }),
        scheduleNotificationAsync: async () => 'id',
        cancelScheduledNotificationAsync: async () => undefined,
        getAllScheduledNotificationsAsync: async () => [],
        addNotificationResponseReceivedListener: () => ({ remove: () => undefined }),
      }),
    });
    assert.equal(await deniedDriver.getPermissionStatus(), 'denied');
    assert.equal(await deniedDriver.requestPermission(), 'granted');
  });

  it('13. Alternar privacidade na tela bloqueada reagenda com texto atualizado sem duplicar notificações', async () => {
    const driver = new FakeLocalNotificationDriver({ permissionStatus: 'granted' });
    const storage = new InMemoryKeyValueStorage();
    const service = new ReactNativeNotificationService({
      userId: 'user-1',
      driver,
      storage,
      nowProvider: () => FIXED_NOW,
    });

    const dose = makeDose({
      id: 'dose-priv-1',
      medicationName: 'Metformina 850 mg',
      scheduledAt: '2026-11-22T23:00:00Z',
      scheduledLocalTime: '20:00',
    });

    await service.enableReminders([dose]);
    assert.equal(driver.scheduled.size, 1);
    const firstNotif = Array.from(driver.scheduled.values())[0];
    assert.ok(!firstNotif.content.body.includes('Metformina'));

    await service.setLockScreenPrivacy(false);
    const reconcileAfterOptIn = await service.reconcileReminders([dose]);
    assert.equal(reconcileAfterOptIn.rescheduledCount, 1);
    assert.equal(driver.scheduled.size, 1);
    const secondNotif = Array.from(driver.scheduled.values())[0];
    assert.ok(secondNotif.content.body.includes('Metformina 850 mg'));
  });
});
