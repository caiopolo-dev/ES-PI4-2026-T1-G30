import type { PlannedDose } from '../types/api';
import type {
  PlatformNotificationContent,
  ReminderNotificationPayload,
  ReminderPreferences,
} from '../types/reminders';

export const DOSE_REMINDER_CHANNEL_ID = 'dosecerta-reminders';

/**
 * Monta o conteúdo de notificação local compatível com privacidade na tela bloqueada.
 *
 * Regra de negócio (Issue #37):
 * - Evitar exibir dados pessoais/de saúde desnecessários na tela bloqueada.
 * - O texto não deve sugerir que a dose já foi registrada automaticamente.
 */
export function buildReminderNotificationContent(
  plannedDose: PlannedDose,
  preferences: Pick<ReminderPreferences, 'hideSensitiveDetailsOnLockScreen'>,
): PlatformNotificationContent {
  const timeLabel = plannedDose.scheduledLocalTime || extractLocalTimeFallback(plannedDose.scheduledAt, plannedDose.timezone);
  const dateLabel = plannedDose.date || extractDateFallback(plannedDose.scheduledAt, plannedDose.timezone);
  const timezone = plannedDose.timezone || 'America/Sao_Paulo';

  const payload: ReminderNotificationPayload = {
    type: 'DOSE_REMINDER',
    plannedDoseId: plannedDose.id,
    treatmentId: plannedDose.treatmentId,
    medicationInUseId: plannedDose.medicationInUseId,
    date: dateLabel,
    scheduledAt: plannedDose.scheduledAt,
    scheduledLocalTime: timeLabel,
    timezone,
  };

  if (preferences.hideSensitiveDetailsOnLockScreen) {
    return {
      title: 'DoseCerta — Lembrete de horário',
      body: `Você tem uma dose programada para ${timeLabel}. Abra o aplicativo para consultar os detalhes.`,
      data: payload,
      channelId: DOSE_REMINDER_CHANNEL_ID,
      sound: true,
    };
  }

  return {
    title: `DoseCerta — Horário previsto (${timeLabel})`,
    body: `${plannedDose.medicationName} está programado para ${timeLabel}. Abra o app para confirmar manualmente.`,
    data: payload,
    channelId: DOSE_REMINDER_CHANNEL_ID,
    sound: true,
  };
}

function extractLocalTimeFallback(scheduledAtIso: string, timeZone = 'America/Sao_Paulo'): string {
  const parsed = new Date(scheduledAtIso);
  if (Number.isNaN(parsed.getTime())) {
    return '--:--';
  }
  const parts = new Intl.DateTimeFormat('pt-BR', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(parsed);
  const hour = parts.find((p) => p.type === 'hour')?.value ?? '00';
  const minute = parts.find((p) => p.type === 'minute')?.value ?? '00';
  return `${hour}:${minute}`;
}

function extractDateFallback(scheduledAtIso: string, timeZone = 'America/Sao_Paulo'): string {
  const parsed = new Date(scheduledAtIso);
  if (Number.isNaN(parsed.getTime())) {
    return '';
  }
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(parsed);
  const year = parts.find((p) => p.type === 'year')?.value ?? '1970';
  const month = parts.find((p) => p.type === 'month')?.value ?? '01';
  const day = parts.find((p) => p.type === 'day')?.value ?? '01';
  return `${year}-${month}-${day}`;
}

