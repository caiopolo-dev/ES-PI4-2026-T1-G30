import { describe, it } from "node:test";
import assert from "node:assert/strict";

import {
  InMemoryStorage,
  WebReminderService,
  type NotificationPermissionStatus,
  type ReminderNotificationPayload,
  type WebNotificationContent,
  type WebNotificationDriver,
} from "../src/notifications/webReminders";
import type { Agenda, Dose } from "../src/types/api";

class FakeWebDriver implements WebNotificationDriver {
  readonly maxPendingNotifications: number;
  permission: NotificationPermissionStatus;
  available = true;
  scheduleCalls = 0;
  readonly scheduled = new Map<string, { content: WebNotificationContent; triggerDate: Date }>();
  private seq = 0;
  private readonly clickListeners = new Set<(payload: ReminderNotificationPayload) => void>();

  constructor(options?: { permission?: NotificationPermissionStatus; maxPendingNotifications?: number }) {
    this.permission = options?.permission ?? "granted";
    this.maxPendingNotifications = options?.maxPendingNotifications ?? 50;
  }

  isAvailable(): boolean {
    return this.available;
  }

  getPermissionStatus(): NotificationPermissionStatus {
    return this.available ? this.permission : "unavailable";
  }

  async requestPermission(): Promise<NotificationPermissionStatus> {
    return this.getPermissionStatus();
  }

  scheduleLocalNotification(content: WebNotificationContent, triggerDate: Date): string {
    this.scheduleCalls += 1;
    this.seq += 1;
    const id = `web-notif-${this.seq}`;
    this.scheduled.set(id, { content, triggerDate });
    return id;
  }

  cancelScheduledNotification(id: string): void {
    this.scheduled.delete(id);
  }

  getAllScheduledNotificationIds(): string[] {
    return Array.from(this.scheduled.keys());
  }

  onNotificationClick(listener: (payload: ReminderNotificationPayload) => void): () => void {
    this.clickListeners.add(listener);
    return () => this.clickListeners.delete(listener);
  }

  simulateClick(id: string): void {
    const entry = this.scheduled.get(id);
    if (!entry) throw new Error(`Notificação ${id} não encontrada`);
    for (const listener of this.clickListeners) listener(entry.content.data);
  }

  dispose(): void {
    this.scheduled.clear();
    this.clickListeners.clear();
  }
}

function makeDose(overrides: Partial<Dose> = {}): Dose {
  return {
    id: overrides.id ?? "dose-001",
    treatmentId: overrides.treatmentId ?? "treat-001",
    medicationInUseId: overrides.medicationInUseId ?? "med-001",
    medicationName: overrides.medicationName ?? "Losartana 50 mg",
    plannedQuantity: overrides.plannedQuantity ?? 1,
    scheduledAt: overrides.scheduledAt ?? "2026-11-22T23:00:00Z",
    scheduledLocalTime: overrides.scheduledLocalTime ?? "20:00",
    scheduleStatus: overrides.scheduleStatus ?? "SCHEDULED",
    displayStatus: overrides.displayStatus ?? "UPCOMING",
    record: overrides.record !== undefined ? overrides.record : null,
  };
}

const FIXED_NOW = new Date("2026-11-22T15:00:00Z");

describe("Integração React Web — Lembretes de Medicamentos (Issue #37)", () => {
  it("1. Agenda doses futuras com privacidade por padrão e impede duplicatas em consultas repetidas", async () => {
    const driver = new FakeWebDriver({ permission: "granted" });
    const service = new WebReminderService({
      userId: "user-web-1",
      driver,
      storage: new InMemoryStorage(),
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    const doses = [
      makeDose({ id: "d1", medicationName: "Losartana 50 mg", scheduledAt: "2026-11-22T18:00:00Z", scheduledLocalTime: "15:00" }),
      makeDose({ id: "d2", medicationName: "Sinvastatina 20 mg", scheduledAt: "2026-11-22T23:00:00Z", scheduledLocalTime: "20:00" }),
    ];

    const [r1, r2, r3] = await Promise.all([
      service.reconcileReminders(doses, { reconciledDates: ["2026-11-22"] }),
      service.reconcileReminders(doses, { reconciledDates: ["2026-11-22"] }),
      service.reconcileReminders(doses, { reconciledDates: ["2026-11-22"] }),
    ]);

    assert.equal(r1.scheduledCount, 2);
    assert.equal(r2.unchangedCount, 2);
    assert.equal(r3.unchangedCount, 2);
    assert.equal(driver.scheduled.size, 2);
    assert.equal(driver.scheduleCalls, 2);

    for (const entry of driver.scheduled.values()) {
      assert.ok(!entry.content.body.includes("Losartana"));
      assert.ok(!entry.content.body.includes("Sinvastatina"));
    }
  });

  it("2. Desabilitar preferência cancela pendentes; permissão bloqueada/indisponível nunca aparece ativa", async () => {
    const driver = new FakeWebDriver({ permission: "granted" });
    const service = new WebReminderService({
      userId: "user-web-1",
      driver,
      storage: new InMemoryStorage(),
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    await service.reconcileReminders([makeDose({ id: "d1" })], { reconciledDates: ["2026-11-22"] });
    assert.equal(driver.scheduled.size, 1);

    const disabled = await service.disableReminders();
    assert.equal(disabled.cancelledCount, 1);
    assert.equal(driver.scheduled.size, 0);

    const blockedDriver = new FakeWebDriver({ permission: "blocked" });
    const blockedService = new WebReminderService({
      userId: "user-web-1",
      driver: blockedDriver,
      storage: new InMemoryStorage(),
      nowProvider: () => FIXED_NOW,
    });
    const enabled = await blockedService.enableReminders();
    assert.equal(enabled.effectiveActive, false);
    const rec = await blockedService.reconcileReminders([makeDose({ id: "d2" })]);
    assert.equal(rec.effectiveActive, false);
    assert.equal(blockedDriver.scheduled.size, 0);
  });

  it("3. Edição de cronograma, encerramento de tratamento e registro explícito cancelam lembretes afetados sem alterar histórico", async () => {
    const driver = new FakeWebDriver({ permission: "granted" });
    const service = new WebReminderService({
      userId: "user-web-1",
      driver,
      storage: new InMemoryStorage(),
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    await service.reconcileReminders(
      [
        makeDose({ id: "d-t1-v1", treatmentId: "t1", scheduledAt: "2026-11-22T23:00:00Z", scheduledLocalTime: "20:00" }),
        makeDose({ id: "d-t2-a", treatmentId: "t2", scheduledAt: "2026-11-22T21:00:00Z", scheduledLocalTime: "18:00" }),
      ],
      { reconciledDates: ["2026-11-22"] },
    );
    assert.equal(driver.scheduled.size, 2);

    // Edição de cronograma do tratamento t1: d-t1-v1 é cancelada e d-t1-v2 entra às 21:00
    await service.reconcileReminders(
      [
        makeDose({ id: "d-t1-v1", treatmentId: "t1", scheduleStatus: "CANCELLED" }),
        makeDose({ id: "d-t1-v2", treatmentId: "t1", scheduledAt: "2026-11-23T00:00:00Z", scheduledLocalTime: "21:00" }),
      ],
      { reconciledDates: ["2026-11-22"], scopeTreatmentId: "t1" },
    );
    assert.deepEqual(
      service.getManagedReminders().map((r) => r.plannedDoseId).sort(),
      ["d-t1-v2", "d-t2-a"],
    );

    // Registro explícito de d-t2-a cancela seu lembrete
    assert.equal(await service.handleDoseRecorded("d-t2-a"), true);
    assert.deepEqual(
      service.getManagedReminders().map((r) => r.plannedDoseId),
      ["d-t1-v2"],
    );

    // Encerramento do tratamento t1 cancela lembretes restantes dele
    assert.equal(await service.cancelRemindersByTreatment("t1"), 1);
    assert.equal(driver.scheduled.size, 0);
  });

  it("4. Sincronização por janela (syncWithAgendaWindow) e clique na notificação direcionam sem criar registro automático", async () => {
    const driver = new FakeWebDriver({ permission: "granted" });
    const service = new WebReminderService({
      userId: "user-web-1",
      driver,
      storage: new InMemoryStorage(),
      schedulingWindowDays: 2,
      nowProvider: () => FIXED_NOW,
    });

    await service.enableReminders();
    const fetchedDates: string[] = [];
    const fetchAgenda = async (date: string): Promise<Agenda> => {
      fetchedDates.push(date);
      return {
        date,
        timezone: "America/Sao_Paulo",
        doses: date === "2026-11-22" ? [makeDose({ id: "d-click" })] : [],
      };
    };

    const syncResult = await service.syncWithAgendaWindow(fetchAgenda, { windowDays: 2 });
    assert.deepEqual(fetchedDates, ["2026-11-22", "2026-11-23"]);
    assert.equal(syncResult.scheduledCount, 1);

    let clickedPayload: ReminderNotificationPayload | null = null;
    driver.onNotificationClick((payload) => {
      clickedPayload = payload;
    });
    const notifId = service.getManagedReminders()[0].platformNotificationId;
    driver.simulateClick(notifId);

    assert.ok(clickedPayload);
    assert.equal((clickedPayload as ReminderNotificationPayload).plannedDoseId, "d-click");
  });
});

