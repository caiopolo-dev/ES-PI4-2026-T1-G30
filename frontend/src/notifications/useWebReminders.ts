import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import type { Dose, User } from "../types/api";
import {
  BrowserLocalStorageAdapter,
  BrowserNotificationDriver,
  createDefaultReminderPreferences,
  WebReminderService,
  type NotificationPermissionStatus,
  type ReconcileRemindersResult,
  type ReminderNavigationTarget,
  type ReminderPreferences,
  type ScheduledReminderRecord,
} from "./webReminders";

export interface RemindersController {
  permissionStatus: NotificationPermissionStatus;
  preferences: ReminderPreferences;
  effectiveActive: boolean;
  managedReminders: ScheduledReminderRecord[];
  pendingCount: number;
  statusSummaryLabel: string;
  showPermissionRationale: boolean;
  nonBlockingWarning: string | null;
  lastSyncResult: ReconcileRemindersResult | null;
  requestToggleReminders: (enable: boolean) => Promise<void>;
  confirmPermissionRationaleAndEnable: () => Promise<NotificationPermissionStatus>;
  dismissPermissionRationale: () => void;
  toggleLockScreenPrivacy: (hideSensitive: boolean) => Promise<void>;
  syncAgendaDoses: (doses: Dose[], date: string, timezone?: string) => Promise<void>;
  syncAgendaWindow: (scopeTreatmentId?: string) => Promise<void>;
  handleScheduleUpdated: (treatmentId: string) => Promise<void>;
  handleTreatmentClosed: (treatmentId: string) => Promise<void>;
  handleDoseRecorded: (plannedDoseId: string) => Promise<void>;
}

function buildWarning(
  status: NotificationPermissionStatus,
  prefs: ReminderPreferences,
): string | null {
  if (status === "unavailable") {
    return "Notificações do navegador indisponíveis neste ambiente. A agenda e o registro manual continuam funcionando normalmente.";
  }
  if (status === "blocked") {
    return "As notificações estão bloqueadas nas permissões do navegador. Libere-as nas configurações do site para receber lembretes.";
  }
  if (status === "denied" && prefs.enabled) {
    return "Permissão de notificações negada. Os lembretes permanecem inativos até a autorização ser concedida.";
  }
  return null;
}

function buildStatusSummaryLabel(
  status: NotificationPermissionStatus,
  effectiveActive: boolean,
  pendingCount: number,
): string {
  if (status === "unavailable") return "Indisponíveis";
  if (status === "blocked") return "Bloqueadas";
  if (status === "denied") return "Permissão negada";
  if (!effectiveActive) return "Desativados";
  return pendingCount > 0 ? `Ativos (${pendingCount})` : "Ativos";
}

export function useWebReminders(
  user: User | null,
  onNavigateFromReminder: (target: ReminderNavigationTarget) => void,
): RemindersController {
  const serviceRef = useRef<WebReminderService | null>(null);
  const driverRef = useRef<BrowserNotificationDriver | null>(null);
  const navCallbackRef = useRef(onNavigateFromReminder);

  const [permissionStatus, setPermissionStatus] = useState<NotificationPermissionStatus>("undetermined");
  const [preferences, setPreferences] = useState<ReminderPreferences>(() => createDefaultReminderPreferences());
  const [managedReminders, setManagedReminders] = useState<ScheduledReminderRecord[]>([]);
  const [showPermissionRationale, setShowPermissionRationale] = useState(false);
  const [nonBlockingWarning, setNonBlockingWarning] = useState<string | null>(null);
  const [lastSyncResult, setLastSyncResult] = useState<ReconcileRemindersResult | null>(null);

  useEffect(() => {
    navCallbackRef.current = onNavigateFromReminder;
  }, [onNavigateFromReminder]);

  const syncLocalSnapshot = useCallback((service: WebReminderService) => {
    const status = service.getPermissionStatus();
    const prefs = service.getPreferences();
    const managed = service.getManagedReminders();
    setPermissionStatus(status);
    setPreferences(prefs);
    setManagedReminders(managed);
    setNonBlockingWarning(buildWarning(status, prefs));
  }, []);

  const syncAgendaWindow = useCallback(
    async (scopeTreatmentId?: string) => {
      const service = serviceRef.current;
      if (!service) return;
      try {
        const result = await service.syncWithAgendaWindow(
          (date) => api.agenda(date),
          { scopeTreatmentId },
        );
        setLastSyncResult(result);
        syncLocalSnapshot(service);
        if (result.failedCount > 0) {
          setNonBlockingWarning(
            "Alguns lembretes não puderam ser agendados. Sua agenda e o registro manual continuam disponíveis.",
          );
        }
      } catch {
        // Falha de sincronização de lembretes nunca impede uso da agenda ou tratamentos.
        setNonBlockingWarning(
          "Não foi possível sincronizar lembretes agora. Você pode consultar a agenda e registrar doses normalmente.",
        );
      }
    },
    [syncLocalSnapshot],
  );

  // Inicializa o serviço quando há usuário autenticado e limpa timers no logout/troca de conta.
  useEffect(() => {
    if (!user) {
      serviceRef.current?.dispose();
      serviceRef.current = null;
      driverRef.current = null;
      setManagedReminders([]);
      setShowPermissionRationale(false);
      setNonBlockingWarning(null);
      setLastSyncResult(null);
      return;
    }

    const driver = new BrowserNotificationDriver();
    const service = new WebReminderService({
      userId: user.id,
      driver,
      storage: new BrowserLocalStorageAdapter(),
    });
    driverRef.current = driver;
    serviceRef.current = service;

    syncLocalSnapshot(service);

    // Toque na notificação abre a dose na agenda sem registrar automaticamente.
    const unsubscribeClick = driver.onNotificationClick((payload) => {
      navCallbackRef.current({
        date: payload.date,
        plannedDoseId: payload.plannedDoseId,
        treatmentId: payload.treatmentId,
        medicationInUseId: payload.medicationInUseId,
      });
    });

    // Se o usuário já tinha lembretes ativos e permissão concedida, reconcilia a janela ao abrir.
    if (service.getPreferences().enabled && service.getPermissionStatus() === "granted") {
      void syncAgendaWindow();
    }

    // Revalida permissão ao voltar para a aba (ex.: após liberar permissão nas configurações do navegador).
    const onVisibilityOrFocus = () => {
      if (document.visibilityState === "visible") {
        syncLocalSnapshot(service);
        if (service.getPreferences().enabled && service.getPermissionStatus() === "granted") {
          void syncAgendaWindow();
        }
      }
    };
    window.addEventListener("focus", onVisibilityOrFocus);
    document.addEventListener("visibilitychange", onVisibilityOrFocus);

    return () => {
      unsubscribeClick();
      window.removeEventListener("focus", onVisibilityOrFocus);
      document.removeEventListener("visibilitychange", onVisibilityOrFocus);
      service.dispose();
      serviceRef.current = null;
      driverRef.current = null;
    };
  }, [user, syncAgendaWindow, syncLocalSnapshot]);

  const requestToggleReminders = useCallback(
    async (enable: boolean) => {
      const service = serviceRef.current;
      if (!service) return;

      if (!enable) {
        setShowPermissionRationale(false);
        await service.disableReminders();
        syncLocalSnapshot(service);
        return;
      }

      const currentStatus = service.getPermissionStatus();
      setPermissionStatus(currentStatus);

      if (currentStatus === "unavailable" || currentStatus === "blocked") {
        setNonBlockingWarning(buildWarning(currentStatus, { ...service.getPreferences(), enabled: true }));
        return;
      }

      if (currentStatus === "granted") {
        await service.enableReminders();
        syncLocalSnapshot(service);
        await syncAgendaWindow();
        return;
      }

      // undetermined / denied -> exibe explicação contextual antes de acionar Notification.requestPermission().
      setShowPermissionRationale(true);
    },
    [syncAgendaWindow, syncLocalSnapshot],
  );

  const confirmPermissionRationaleAndEnable = useCallback(async (): Promise<NotificationPermissionStatus> => {
    setShowPermissionRationale(false);
    const service = serviceRef.current;
    if (!service) return "unavailable";

    const nextStatus = await service.requestPermission();
    setPermissionStatus(nextStatus);

    if (nextStatus === "granted") {
      await service.enableReminders();
      syncLocalSnapshot(service);
      await syncAgendaWindow();
    } else {
      await service.disableReminders();
      syncLocalSnapshot(service);
      setNonBlockingWarning(buildWarning(nextStatus, { ...service.getPreferences(), enabled: true }));
    }
    return nextStatus;
  }, [syncAgendaWindow, syncLocalSnapshot]);

  const dismissPermissionRationale = useCallback(() => {
    setShowPermissionRationale(false);
  }, []);

  const toggleLockScreenPrivacy = useCallback(
    async (hideSensitive: boolean) => {
      const service = serviceRef.current;
      if (!service) return;
      service.setLockScreenPrivacy(hideSensitive);
      syncLocalSnapshot(service);
      if (service.getPreferences().enabled && service.getPermissionStatus() === "granted") {
        await syncAgendaWindow();
      }
    },
    [syncAgendaWindow, syncLocalSnapshot],
  );

  const syncAgendaDoses = useCallback(
    async (doses: Dose[], date: string, timezone?: string) => {
      const service = serviceRef.current;
      if (!service) return;
      try {
        const result = await service.reconcileReminders(doses, {
          reconciledDates: [date],
          timezone,
        });
        setLastSyncResult(result);
        syncLocalSnapshot(service);
      } catch {
        // Não bloqueia a consulta da agenda.
      }
    },
    [syncLocalSnapshot],
  );

  const handleScheduleUpdated = useCallback(
    async (treatmentId: string) => {
      await syncAgendaWindow(treatmentId);
    },
    [syncAgendaWindow],
  );

  const handleTreatmentClosed = useCallback(
    async (treatmentId: string) => {
      const service = serviceRef.current;
      if (!service) return;
      await service.cancelRemindersByTreatment(treatmentId);
      syncLocalSnapshot(service);
    },
    [syncLocalSnapshot],
  );

  const handleDoseRecorded = useCallback(
    async (plannedDoseId: string) => {
      const service = serviceRef.current;
      if (!service) return;
      await service.handleDoseRecorded(plannedDoseId);
      syncLocalSnapshot(service);
    },
    [syncLocalSnapshot],
  );

  const effectiveActive = preferences.enabled && permissionStatus === "granted";
  const pendingCount = effectiveActive ? managedReminders.length : 0;

  return {
    permissionStatus,
    preferences,
    effectiveActive,
    managedReminders,
    pendingCount,
    statusSummaryLabel: buildStatusSummaryLabel(permissionStatus, effectiveActive, pendingCount),
    showPermissionRationale,
    nonBlockingWarning,
    lastSyncResult,
    requestToggleReminders,
    confirmPermissionRationaleAndEnable,
    dismissPermissionRationale,
    toggleLockScreenPrivacy,
    syncAgendaDoses,
    syncAgendaWindow,
    handleScheduleUpdated,
    handleTreatmentClosed,
    handleDoseRecorded,
  };
}

