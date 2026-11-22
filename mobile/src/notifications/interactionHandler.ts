import type {
  LocalNotificationDriver,
  ReminderNavigationTarget,
  ReminderNotificationPayload,
} from '../types/reminders';

/**
 * Converte o payload de um lembrete tocado pelo usuário em destino de navegação no app.
 *
 * Regra crítica de negócio (Issue #37):
 * - Abrir ou receber a notificação NUNCA registra dose como TAKEN ou SKIPPED.
 * - O destino apenas abre a agenda na data/ocorrência correspondente para que o usuário
 *   tome uma ação explícita ("Tomei" ou "Não tomei").
 */
export function buildNavigationTargetFromPayload(
  payload: ReminderNotificationPayload,
): ReminderNavigationTarget {
  return {
    screen: 'Agenda',
    params: {
      date: payload.date,
      highlightPlannedDoseId: payload.plannedDoseId,
      treatmentId: payload.treatmentId,
      medicationInUseId: payload.medicationInUseId,
    },
  };
}

/**
 * Conecta os listeners de interação com notificações locais tanto com o app ativo/minimizado
 * quanto na abertura a partir do app fechado (cold start via getInitialNotificationResponse).
 */
export function attachNotificationInteractionHandler(
  driver: LocalNotificationDriver,
  onNavigate: (target: ReminderNavigationTarget) => void,
): { dispose: () => void; checkInitialNotification: () => Promise<ReminderNavigationTarget | null> } {
  const handledColdStartIds = new Set<string>();

  const unsubscribe = driver.addResponseListener((payload) => {
    const target = buildNavigationTargetFromPayload(payload);
    onNavigate(target);
  });

  const checkInitialNotification = async (): Promise<ReminderNavigationTarget | null> => {
    const initialPayload = await driver.getInitialNotificationResponse();
    if (!initialPayload) {
      return null;
    }
    const dedupeKey = `${initialPayload.plannedDoseId}:${initialPayload.scheduledAt}`;
    if (handledColdStartIds.has(dedupeKey)) {
      return null;
    }
    handledColdStartIds.add(dedupeKey);
    const target = buildNavigationTargetFromPayload(initialPayload);
    onNavigate(target);
    return target;
  };

  return {
    dispose: unsubscribe,
    checkInitialNotification,
  };
}

