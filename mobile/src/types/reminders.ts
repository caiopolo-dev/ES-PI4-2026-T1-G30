import type { PlannedDose } from './api';

/**
 * Estados de permissão suportados na camada de notificações React Native.
 * - granted: permissão concedida pelo SO; lembretes podem ser agendados.
 * - undetermined: ainda não solicitada ao usuário.
 * - denied: negada, mas ainda pode ser solicitada novamente.
 * - blocked: negada permanentemente (exige habilitação nas configurações do sistema).
 * - unavailable: módulo nativo indisponível ou ambiente sem suporte a notificações locais.
 */
export type NotificationPermissionStatus =
  | 'granted'
  | 'undetermined'
  | 'denied'
  | 'blocked'
  | 'unavailable';

export interface ReminderPreferences {
  /** Preferência explícita do usuário (ligar/desligar lembretes). */
  enabled: boolean;
  /**
   * Privacidade na tela bloqueada: ativado por padrão para evitar expor
   * nome do medicamento ou dados clínicos sensíveis na tela bloqueada.
   */
  hideSensitiveDetailsOnLockScreen: boolean;
  updatedAt: string;
}

/**
 * Associação local persistida entre a identidade estável da DosePrevista (plannedDoseId)
 * e o identificador da notificação agendada no sistema operacional.
 */
export interface ScheduledReminderRecord {
  plannedDoseId: string;
  platformNotificationId: string;
  userId: string;
  treatmentId: string;
  medicationInUseId: string;
  scheduledAt: string;
  scheduledLocalTime: string;
  timezone: string;
  date: string;
  scheduleVersion?: number;
  hideSensitiveDetailsOnLockScreen: boolean;
  scheduledOnDeviceAt: string;
}

/**
 * Payload anexado à notificação local para direcionar o usuário ao tocar no lembrete.
 * Não contém ação implícita de registro de dose.
 */
export interface ReminderNotificationPayload {
  type: 'DOSE_REMINDER';
  plannedDoseId: string;
  treatmentId: string;
  medicationInUseId: string;
  date: string;
  scheduledAt: string;
  scheduledLocalTime: string;
  timezone: string;
}

export interface ScheduleReminderResult {
  plannedDoseId: string;
  status: 'scheduled' | 'skipped_already_scheduled' | 'skipped_ineligible' | 'blocked_or_disabled' | 'failed';
  platformNotificationId?: string;
  reason?: string;
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

export interface ReminderNavigationTarget {
  screen: 'Agenda';
  params: {
    date: string;
    highlightPlannedDoseId: string;
    treatmentId: string;
    medicationInUseId: string;
  };
}

/**
 * Contrato operacional da camada de notificações do frontend React Native.
 */
export interface MedicationNotificationService {
  requestPermission(): Promise<NotificationPermissionStatus>;
  getPermissionStatus(): Promise<NotificationPermissionStatus>;
  scheduleReminder(plannedDose: PlannedDose): Promise<ScheduleReminderResult>;
  cancelReminder(plannedDoseId: string): Promise<boolean>;
  reconcileReminders(plannedDoses: PlannedDose[], options?: ReconcileOptions): Promise<ReconcileRemindersResult>;
  cancelRemindersByTreatment(treatmentId: string): Promise<number>;
  cancelAllManagedReminders(): Promise<number>;
  handleDoseRecorded(plannedDoseId: string): Promise<boolean>;
}

export interface ReconcileOptions {
  /**
   * Quando informado, restringe o cancelamento de lembretes órfãos apenas às datas
   * consultadas na agenda, evitando cancelar lembretes válidos de dias fora da consulta parcial.
   */
  reconciledDates?: string[];
  /**
   * Quando informado (ex.: após edição de cronograma de um tratamento/medicamento),
   * garante cancelamento de lembretes futuros antigos vinculados àquele tratamento.
   */
  scopeTreatmentId?: string;
}

export interface PlatformNotificationContent {
  title: string;
  body: string;
  data: ReminderNotificationPayload;
  channelId?: string;
  sound?: boolean;
}

/**
 * Interface mínima do driver nativo de notificações locais (implementada via expo-notifications
 * no app React Native e facilmente simulável em testes automatizados reproduzíveis).
 */
export interface LocalNotificationDriver {
  readonly platformName: 'ios' | 'android' | 'unsupported';
  readonly maxPendingNotifications: number;
  isAvailable(): boolean;
  ensureChannelConfigured(): Promise<void>;
  getPermissionStatus(): Promise<NotificationPermissionStatus>;
  requestPermission(): Promise<NotificationPermissionStatus>;
  scheduleLocalNotification(content: PlatformNotificationContent, triggerDate: Date): Promise<string>;
  cancelScheduledNotification(platformNotificationId: string): Promise<void>;
  getAllScheduledNotificationIds(): Promise<string[]>;
  addResponseListener(
    listener: (payload: ReminderNotificationPayload) => void,
  ): () => void;
  getInitialNotificationResponse(): Promise<ReminderNotificationPayload | null>;
}

/**
 * Armazenamento chave-valor persistente (implementado com AsyncStorage no React Native).
 */
export interface KeyValueStorage {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}
