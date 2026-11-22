import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { attachNotificationInteractionHandler } from '../notifications/interactionHandler';
import { createDefaultReminderPreferences } from '../notifications/preferencesStore';
import type { ReactNativeNotificationService } from '../notifications/notificationService';
import type { DoseCertaAgendaClient, PlannedDose } from '../types/api';
import type {
  LocalNotificationDriver,
  NotificationPermissionStatus,
  ReconcileOptions,
  ReconcileRemindersResult,
  ReminderNavigationTarget,
  ReminderPreferences,
} from '../types/reminders';

export interface UseMedicationRemindersParams {
  service: ReactNativeNotificationService;
  driver: LocalNotificationDriver;
  agendaClient: Pick<DoseCertaAgendaClient, 'getDailyAgenda'>;
  onNavigateFromReminder?: (target: ReminderNavigationTarget) => void;
}

export interface UseMedicationRemindersState {
  loading: boolean;
  permissionStatus: NotificationPermissionStatus;
  preferences: ReminderPreferences;
  /** Só é true quando a preferência do usuário está habilitada E a permissão do SO foi concedida. */
  effectiveActive: boolean;
  pendingCount: number;
  showPermissionRationale: boolean;
  nonBlockingWarning: string | null;
  lastSyncResult: ReconcileRemindersResult | null;
  refreshState: () => Promise<void>;
  requestToggleReminders: (enable: boolean) => Promise<void>;
  confirmPermissionRationaleAndEnable: () => Promise<NotificationPermissionStatus>;
  dismissPermissionRationale: () => void;
  toggleLockScreenPrivacy: (hideSensitive: boolean) => Promise<void>;
  syncAgendaDoses: (doses: PlannedDose[], options?: ReconcileOptions) => Promise<ReconcileRemindersResult | null>;
  syncAgendaWindow: (scopeTreatmentId?: string) => Promise<ReconcileRemindersResult | null>;
  handleScheduleUpdated: (treatmentId: string) => Promise<ReconcileRemindersResult | null>;
  handleTreatmentClosed: (treatmentId: string) => Promise<number>;
  handleDoseRecorded: (plannedDoseId: string) => Promise<boolean>;
}

/**
 * Hook React Native que integra a camada de lembretes ao ciclo de vida do aplicativo (AppState),
 * permissões contextuais, reconciliação idempotente com GET /doses e navegação por toque.
 */
export function useMedicationReminders(
  params: UseMedicationRemindersParams,
): UseMedicationRemindersState {
  const { service, driver, agendaClient, onNavigateFromReminder } = params;

  const [loading, setLoading] = useState(true);
  const [permissionStatus, setPermissionStatus] = useState<NotificationPermissionStatus>('undetermined');
  const [preferences, setPreferences] = useState<ReminderPreferences>(() =>
    createDefaultReminderPreferences(),
  );
  const [pendingCount, setPendingCount] = useState(0);
  const [showPermissionRationale, setShowPermissionRationale] = useState(false);
  const [nonBlockingWarning, setNonBlockingWarning] = useState<string | null>(null);
  const [lastSyncResult, setLastSyncResult] = useState<ReconcileRemindersResult | null>(null);

  const mountedRef = useRef(true);

  const effectiveActive = preferences.enabled && permissionStatus === 'granted';

  const refreshState = useCallback(async () => {
    try {
      const [status, prefs, managed] = await Promise.all([
        service.getPermissionStatus(),
        service.getPreferences(),
        service.getManagedReminders(),
      ]);
      if (!mountedRef.current) {
        return;
      }
      setPermissionStatus(status);
      setPreferences(prefs);
      setPendingCount(managed.length);
      setNonBlockingWarning(buildWarningMessage(status, prefs));
    } catch {
      if (mountedRef.current) {
        setNonBlockingWarning(
          'Não foi possível verificar o estado dos lembretes. A agenda e o registro manual continuam disponíveis.',
        );
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, [service]);

  const syncAgendaWindow = useCallback(
    async (scopeTreatmentId?: string): Promise<ReconcileRemindersResult | null> => {
      try {
        const result = await service.syncWithAgendaWindow(agendaClient, { scopeTreatmentId });
        const managed = await service.getManagedReminders();
        if (mountedRef.current) {
          setPermissionStatus(result.permissionStatus);
          setLastSyncResult(result);
          setPendingCount(managed.length);
          if (result.failedCount > 0) {
            setNonBlockingWarning(
              'Alguns lembretes não puderam ser agendados. Sua agenda e o registro manual continuam funcionando normalmente.',
            );
          } else {
            const prefs = await service.getPreferences();
            if (mountedRef.current) {
              setNonBlockingWarning(buildWarningMessage(result.permissionStatus, prefs));
            }
          }
        }
        return result;
      } catch {
        // Falha de reconciliação nunca bloqueia a agenda nem o registro manual.
        if (mountedRef.current) {
          setNonBlockingWarning(
            'Não foi possível sincronizar lembretes agora. Você pode consultar a agenda e registrar doses normalmente.',
          );
        }
        return null;
      }
    },
    [agendaClient, service],
  );

  const syncAgendaDoses = useCallback(
    async (doses: PlannedDose[], options?: ReconcileOptions): Promise<ReconcileRemindersResult | null> => {
      try {
        const result = await service.reconcileReminders(doses, options);
        const managed = await service.getManagedReminders();
        if (mountedRef.current) {
          setPermissionStatus(result.permissionStatus);
          setLastSyncResult(result);
          setPendingCount(managed.length);
        }
        return result;
      } catch {
        if (mountedRef.current) {
          setNonBlockingWarning(
            'Falha ao atualizar agendamentos de lembretes. A agenda e o registro manual seguem disponíveis.',
          );
        }
        return null;
      }
    },
    [service],
  );

  const requestToggleReminders = useCallback(
    async (enable: boolean) => {
      if (!enable) {
        setShowPermissionRationale(false);
        const { preferences: updated } = await service.disableReminders();
        if (mountedRef.current) {
          setPreferences(updated);
          setPendingCount(0);
          setNonBlockingWarning(null);
        }
        return;
      }

      const currentStatus = await service.getPermissionStatus();
      if (mountedRef.current) {
        setPermissionStatus(currentStatus);
      }

      if (currentStatus === 'unavailable') {
        if (mountedRef.current) {
          setNonBlockingWarning(
            'As notificações locais não estão disponíveis neste dispositivo/ambiente.',
          );
        }
        return;
      }

      if (currentStatus === 'blocked') {
        if (mountedRef.current) {
          setNonBlockingWarning(
            'Permissão bloqueada pelo sistema. Abra as configurações do aparelho para permitir notificações do DoseCerta.',
          );
        }
        return;
      }

      if (currentStatus === 'granted') {
        const { preferences: updated } = await service.enableReminders();
        if (mountedRef.current) {
          setPreferences(updated);
          setNonBlockingWarning(null);
        }
        await syncAgendaWindow();
        return;
      }

      // undetermined ou denied -> exibe explicação contextual antes do prompt nativo do SO.
      if (mountedRef.current) {
        setShowPermissionRationale(true);
      }
    },
    [service, syncAgendaWindow],
  );

  const confirmPermissionRationaleAndEnable = useCallback(async (): Promise<NotificationPermissionStatus> => {
    if (mountedRef.current) {
      setShowPermissionRationale(false);
    }
    const newStatus = await service.requestPermission();
    if (mountedRef.current) {
      setPermissionStatus(newStatus);
    }

    if (newStatus === 'granted') {
      const { preferences: updated } = await service.enableReminders();
      if (mountedRef.current) {
        setPreferences(updated);
        setNonBlockingWarning(null);
      }
      await syncAgendaWindow();
    } else {
      // Não apresenta como ativo quando a permissão não foi concedida.
      const { preferences: updated } = await service.disableReminders();
      if (mountedRef.current) {
        setPreferences(updated);
        setPendingCount(0);
        setNonBlockingWarning(buildWarningMessage(newStatus, { ...updated, enabled: true }));
      }
    }

    return newStatus;
  }, [service, syncAgendaWindow]);

  const dismissPermissionRationale = useCallback(() => {
    setShowPermissionRationale(false);
  }, []);

  const toggleLockScreenPrivacy = useCallback(
    async (hideSensitive: boolean) => {
      const updated = await service.setLockScreenPrivacy(hideSensitive);
      if (mountedRef.current) {
        setPreferences(updated);
      }
      if (updated.enabled && permissionStatus === 'granted') {
        await syncAgendaWindow();
      }
    },
    [permissionStatus, service, syncAgendaWindow],
  );

  const handleScheduleUpdated = useCallback(
    async (treatmentId: string) => syncAgendaWindow(treatmentId),
    [syncAgendaWindow],
  );

  const handleTreatmentClosed = useCallback(
    async (treatmentId: string) => {
      const cancelled = await service.cancelRemindersByTreatment(treatmentId);
      const managed = await service.getManagedReminders();
      if (mountedRef.current) {
        setPendingCount(managed.length);
      }
      return cancelled;
    },
    [service],
  );

  const handleDoseRecorded = useCallback(
    async (plannedDoseId: string) => {
      const cancelled = await service.handleDoseRecorded(plannedDoseId);
      const managed = await service.getManagedReminders();
      if (mountedRef.current) {
        setPendingCount(managed.length);
      }
      return cancelled;
    },
    [service],
  );

  // Carrega estado inicial + verifica toque em notificação (cold-start e foreground/background).
  useEffect(() => {
    mountedRef.current = true;
    void refreshState();

    const interaction = attachNotificationInteractionHandler(driver, (target) => {
      onNavigateFromReminder?.(target);
    });
    void interaction.checkInitialNotification();

    // Quando o usuário retorna das configurações do SO (AppState -> active), revalida permissão.
    const appStateSub = AppState.addEventListener('change', (nextState) => {
      if (nextState === 'active') {
        void refreshState().then(async () => {
          const [status, prefs] = await Promise.all([
            service.getPermissionStatus(),
            service.getPreferences(),
          ]);
          if (status === 'granted' && prefs.enabled) {
            await syncAgendaWindow();
          }
        });
      }
    });

    return () => {
      mountedRef.current = false;
      interaction.dispose();
      appStateSub.remove();
    };
  }, [driver, onNavigateFromReminder, refreshState, service, syncAgendaWindow]);

  return {
    loading,
    permissionStatus,
    preferences,
    effectiveActive,
    pendingCount,
    showPermissionRationale,
    nonBlockingWarning,
    lastSyncResult,
    refreshState,
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

function buildWarningMessage(
  status: NotificationPermissionStatus,
  prefs: ReminderPreferences,
): string | null {
  if (status === 'unavailable') {
    return 'Notificações locais indisponíveis neste dispositivo. Você pode consultar a agenda e registrar doses manualmente.';
  }
  if (status === 'blocked') {
    return 'As notificações estão bloqueadas nas configurações do sistema. Ative-as nos ajustes do aparelho para receber lembretes.';
  }
  if (status === 'denied' && prefs.enabled) {
    return 'Permissão de notificações negada. Os lembretes permanecem inativos até a autorização ser concedida.';
  }
  return null;
}

