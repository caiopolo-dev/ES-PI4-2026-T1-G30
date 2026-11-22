import { DOSE_REMINDER_CHANNEL_ID } from './privacyFormatter';
import type {
  LocalNotificationDriver,
  NotificationPermissionStatus,
  PlatformNotificationContent,
  ReminderNotificationPayload,
} from '../types/reminders';

/**
 * Limite conservador abaixo do teto de 64 notificações locais pendentes do iOS
 * e seguro para gerenciamento de alarmes locais no Android.
 */
export const DEFAULT_PLATFORM_PENDING_LIMIT = 50;

interface ExpoPermissionResponse {
  status: 'granted' | 'denied' | 'undetermined';
  canAskAgain?: boolean;
}

interface ExpoNotificationRequest {
  identifier: string;
}

interface ExpoNotificationResponseEvent {
  notification?: {
    request?: {
      content?: {
        data?: unknown;
      };
    };
  };
}

interface ExpoNotificationsModule {
  setNotificationHandler?: (handler: {
    handleNotification: () => Promise<{
      shouldShowAlert: boolean;
      shouldPlaySound: boolean;
      shouldSetBadge: boolean;
    }>;
  }) => void;
  setNotificationChannelAsync?: (
    channelId: string,
    channel: Record<string, unknown>,
  ) => Promise<unknown>;
  getPermissionsAsync: () => Promise<ExpoPermissionResponse>;
  requestPermissionsAsync: (options?: Record<string, unknown>) => Promise<ExpoPermissionResponse>;
  scheduleNotificationAsync: (input: {
    content: Record<string, unknown>;
    trigger: Record<string, unknown>;
  }) => Promise<string>;
  cancelScheduledNotificationAsync: (identifier: string) => Promise<void>;
  getAllScheduledNotificationsAsync: () => Promise<ExpoNotificationRequest[]>;
  addNotificationResponseReceivedListener: (
    listener: (response: ExpoNotificationResponseEvent) => void,
  ) => { remove: () => void };
  getLastNotificationResponseAsync?: () => Promise<ExpoNotificationResponseEvent | null>;
  AndroidImportance?: { HIGH: number };
  AndroidNotificationVisibility?: { PRIVATE: number };
  SchedulableTriggerInputTypes?: { DATE: string };
}

function mapExpoPermission(response: ExpoPermissionResponse): NotificationPermissionStatus {
  if (response.status === 'granted') {
    return 'granted';
  }
  if (response.status === 'undetermined') {
    return 'undetermined';
  }
  if (response.canAskAgain === false) {
    return 'blocked';
  }
  return 'denied';
}

function extractPayload(event: ExpoNotificationResponseEvent | null | undefined): ReminderNotificationPayload | null {
  const data = event?.notification?.request?.content?.data;
  if (!data || typeof data !== 'object') {
    return null;
  }
  const candidate = data as Partial<ReminderNotificationPayload>;
  if (candidate.type !== 'DOSE_REMINDER' || typeof candidate.plannedDoseId !== 'string') {
    return null;
  }
  return {
    type: 'DOSE_REMINDER',
    plannedDoseId: candidate.plannedDoseId,
    treatmentId: typeof candidate.treatmentId === 'string' ? candidate.treatmentId : '',
    medicationInUseId: typeof candidate.medicationInUseId === 'string' ? candidate.medicationInUseId : '',
    date: typeof candidate.date === 'string' ? candidate.date : '',
    scheduledAt: typeof candidate.scheduledAt === 'string' ? candidate.scheduledAt : '',
    scheduledLocalTime: typeof candidate.scheduledLocalTime === 'string' ? candidate.scheduledLocalTime : '',
    timezone: typeof candidate.timezone === 'string' ? candidate.timezone : 'America/Sao_Paulo',
  };
}

/**
 * Adaptador fino específico para React Native usando expo-notifications.
 * Encapsula canal Android (com visibilidade PRIVATE na tela bloqueada), permissões iOS/Android,
 * agendamento por data exata (UTC -> trigger nativo) e listeners de toque na notificação.
 */
export class ExpoNotificationDriver implements LocalNotificationDriver {
  readonly platformName: 'ios' | 'android' | 'unsupported';
  readonly maxPendingNotifications: number;
  private readonly moduleLoader: () => Promise<ExpoNotificationsModule | null>;
  private cachedModule: ExpoNotificationsModule | null | undefined;
  private channelConfigured = false;

  constructor(options?: {
    platformName?: 'ios' | 'android' | 'unsupported';
    maxPendingNotifications?: number;
    moduleLoader?: () => Promise<ExpoNotificationsModule | null>;
  }) {
    this.platformName = options?.platformName ?? 'android';
    this.maxPendingNotifications = options?.maxPendingNotifications ?? DEFAULT_PLATFORM_PENDING_LIMIT;
    this.moduleLoader =
      options?.moduleLoader ??
      (async () => {
        try {
          const mod = await import('expo-notifications');
          return mod as unknown as ExpoNotificationsModule;
        } catch {
          return null;
        }
      });
  }

  private async getModule(): Promise<ExpoNotificationsModule | null> {
    if (this.cachedModule === undefined) {
      this.cachedModule = await this.moduleLoader();
      if (this.cachedModule?.setNotificationHandler) {
        this.cachedModule.setNotificationHandler({
          handleNotification: async () => ({
            shouldShowAlert: true,
            shouldPlaySound: true,
            shouldSetBadge: false,
          }),
        });
      }
    }
    return this.cachedModule;
  }

  isAvailable(): boolean {
    return this.platformName !== 'unsupported';
  }

  async ensureChannelConfigured(): Promise<void> {
    if (this.channelConfigured || this.platformName !== 'android') {
      return;
    }
    const mod = await this.getModule();
    if (!mod?.setNotificationChannelAsync) {
      return;
    }
    await mod.setNotificationChannelAsync(DOSE_REMINDER_CHANNEL_ID, {
      name: 'Lembretes de medicamentos',
      description: 'Avisos nos horários previstos das doses cadastradas no DoseCerta.',
      importance: mod.AndroidImportance?.HIGH ?? 4,
      lockscreenVisibility: mod.AndroidNotificationVisibility?.PRIVATE ?? 0,
      vibrationPattern: [0, 250, 200, 250],
      sound: 'default',
    });
    this.channelConfigured = true;
  }

  async getPermissionStatus(): Promise<NotificationPermissionStatus> {
    if (!this.isAvailable()) {
      return 'unavailable';
    }
    const mod = await this.getModule();
    if (!mod) {
      return 'unavailable';
    }
    try {
      const response = await mod.getPermissionsAsync();
      return mapExpoPermission(response);
    } catch {
      return 'unavailable';
    }
  }

  async requestPermission(): Promise<NotificationPermissionStatus> {
    if (!this.isAvailable()) {
      return 'unavailable';
    }
    const mod = await this.getModule();
    if (!mod) {
      return 'unavailable';
    }
    try {
      await this.ensureChannelConfigured();
      const response = await mod.requestPermissionsAsync({
        ios: {
          allowAlert: true,
          allowSound: true,
          allowBadge: false,
        },
      });
      return mapExpoPermission(response);
    } catch {
      return 'unavailable';
    }
  }

  async scheduleLocalNotification(content: PlatformNotificationContent, triggerDate: Date): Promise<string> {
    const mod = await this.getModule();
    if (!mod) {
      throw new Error('API de notificações nativas indisponível neste dispositivo.');
    }
    await this.ensureChannelConfigured();

    const triggerType = mod.SchedulableTriggerInputTypes?.DATE ?? 'date';
    return mod.scheduleNotificationAsync({
      content: {
        title: content.title,
        body: content.body,
        data: content.data,
        sound: content.sound !== false,
      },
      trigger: {
        type: triggerType,
        date: triggerDate,
        channelId: content.channelId ?? DOSE_REMINDER_CHANNEL_ID,
      },
    });
  }

  async cancelScheduledNotification(platformNotificationId: string): Promise<void> {
    const mod = await this.getModule();
    if (!mod) {
      return;
    }
    await mod.cancelScheduledNotificationAsync(platformNotificationId);
  }

  async getAllScheduledNotificationIds(): Promise<string[]> {
    const mod = await this.getModule();
    if (!mod) {
      return [];
    }
    const scheduled = await mod.getAllScheduledNotificationsAsync();
    return scheduled.map((item) => item.identifier);
  }

  addResponseListener(listener: (payload: ReminderNotificationPayload) => void): () => void {
    let subscription: { remove: () => void } | null = null;
    let disposed = false;

    void this.getModule().then((mod) => {
      if (disposed || !mod) {
        return;
      }
      subscription = mod.addNotificationResponseReceivedListener((event) => {
        const payload = extractPayload(event);
        if (payload) {
          listener(payload);
        }
      });
    });

    return () => {
      disposed = true;
      subscription?.remove();
    };
  }

  async getInitialNotificationResponse(): Promise<ReminderNotificationPayload | null> {
    const mod = await this.getModule();
    if (!mod?.getLastNotificationResponseAsync) {
      return null;
    }
    const event = await mod.getLastNotificationResponseAsync();
    return extractPayload(event);
  }
}

