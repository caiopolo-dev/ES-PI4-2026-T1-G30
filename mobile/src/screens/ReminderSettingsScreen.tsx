import React from 'react';
import {
  Linking,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import type { UseMedicationRemindersState } from '../hooks/useMedicationReminders';
import type { NotificationPermissionStatus } from '../types/reminders';

export interface ReminderSettingsScreenProps {
  reminders: UseMedicationRemindersState;
  onBack?: () => void;
}

/**
 * Tela de configurações de lembretes em React Native (Issue #37).
 *
 * Requisitos garantidos:
 * - Explicação contextual antes da solicitação de permissão ao SO;
 * - Tratamento claro de todos os estados (granted, undetermined, denied, blocked, unavailable);
 * - Nunca exibe lembretes como ativos quando a permissão não foi concedida;
 * - Orientação e atalho para configurações do sistema quando bloqueada;
 * - Controle de privacidade na tela bloqueada (ocultar dados clínicos por padrão);
 * - Comunicação transparente das limitações reais da plataforma móvel.
 */
export function ReminderSettingsScreen({ reminders, onBack }: ReminderSettingsScreenProps) {
  const {
    permissionStatus,
    preferences,
    effectiveActive,
    pendingCount,
    showPermissionRationale,
    nonBlockingWarning,
    lastSyncResult,
    requestToggleReminders,
    confirmPermissionRationaleAndEnable,
    dismissPermissionRationale,
    toggleLockScreenPrivacy,
  } = reminders;

  const statusMeta = getStatusPresentation(permissionStatus, effectiveActive, pendingCount);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        {onBack && (
          <Pressable onPress={onBack} style={styles.backButton} accessibilityRole="button">
            <Text style={styles.backButtonText}>Voltar</Text>
          </Pressable>
        )}
        <Text style={styles.title}>Lembretes de medicamentos</Text>
        <Text style={styles.subtitle}>
          Configure alertas locais com base nos horários reais da sua agenda do DoseCerta.
        </Text>
      </View>

      {/* Card de status coerente com o estado real da permissão */}
      <View style={[styles.card, styles.statusCard]}>
        <Text style={styles.statusBadge}>{statusMeta.badge}</Text>
        <Text style={styles.statusDescription}>{statusMeta.description}</Text>

        {permissionStatus === 'blocked' && (
          <Pressable
            style={styles.primaryButton}
            onPress={() => void Linking.openSettings()}
            accessibilityRole="button"
          >
            <Text style={styles.primaryButtonText}>Abrir configurações do sistema</Text>
          </Pressable>
        )}

        {permissionStatus === 'denied' && (
          <Pressable
            style={styles.secondaryButton}
            onPress={() => void requestToggleReminders(true)}
            accessibilityRole="button"
          >
            <Text style={styles.secondaryButtonText}>Tentar autorizar novamente</Text>
          </Pressable>
        )}
      </View>

      {/* Controle principal: nunca aparece ativo quando lembretes não podem ser entregues */}
      <View style={styles.card}>
        <View style={styles.row}>
          <View style={styles.rowText}>
            <Text style={styles.rowTitle}>Ativar lembretes</Text>
            <Text style={styles.rowHint}>
              Receba avisos nos horários previstos das próximas doses agendadas.
            </Text>
          </View>
          <Switch
            value={effectiveActive}
            disabled={permissionStatus === 'unavailable'}
            onValueChange={(nextValue) => void requestToggleReminders(nextValue)}
            accessibilityLabel="Ativar lembretes de medicamentos"
          />
        </View>

        <View style={styles.divider} />

        <View style={styles.row}>
          <View style={styles.rowText}>
            <Text style={styles.rowTitle}>Privacidade na tela bloqueada</Text>
            <Text style={styles.rowHint}>
              Oculta o nome do medicamento e exibe apenas o horário do lembrete.
            </Text>
          </View>
          <Switch
            value={preferences.hideSensitiveDetailsOnLockScreen}
            onValueChange={(nextValue) => void toggleLockScreenPrivacy(nextValue)}
            accessibilityLabel="Ocultar detalhes sensíveis na tela bloqueada"
          />
        </View>
      </View>

      {nonBlockingWarning && (
        <View style={styles.warningCard}>
          <Text style={styles.warningTitle}>Aviso</Text>
          <Text style={styles.warningText}>{nonBlockingWarning}</Text>
        </View>
      )}

      {lastSyncResult?.truncatedByPlatformLimit && (
        <View style={styles.infoCard}>
          <Text style={styles.infoTitle}>Janela de programação ativa</Text>
          <Text style={styles.infoText}>
            Para respeitar o limite de agendamentos do sistema operacional, apenas as próximas doses
            mais próximas foram programadas agora. Abra o app periodicamente para manter a janela
            atualizada.
          </Text>
        </View>
      )}

      {/* Transparência sobre limitações da plataforma (sem prometer entrega garantida sob Doze/economia) */}
      <View style={styles.infoCard}>
        <Text style={styles.infoTitle}>Como os lembretes funcionam e limitações</Text>
        <Text style={styles.infoText}>
          • Os lembretes são notificações locais agendadas com base nas doses previstas pela sua
          agenda.{'\n'}
          • Receber ou abrir um lembrete nunca marca a dose como tomada ou não tomada
          automaticamente — o registro é sempre uma ação explícita sua.{'\n'}
          • Modos de economia de energia (Doze/Otimização de bateria), modo Não Perturbe ou
          restrições do sistema operacional podem atrasar ou impedir a entrega exata das
          notificações.{'\n'}
          • Mesmo com lembretes desativados ou indisponíveis, sua agenda e o registro manual de
          doses continuam funcionando normalmente.
        </Text>
      </View>

      {/* Modal de explicação contextual antes de disparar o prompt nativo do SO */}
      <Modal
        visible={showPermissionRationale}
        transparent
        animationType="fade"
        onRequestClose={dismissPermissionRationale}
      >
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Permitir lembretes do DoseCerta?</Text>
            <Text style={styles.modalBody}>
              O DoseCerta usa notificações locais apenas para avisar sobre os horários das suas
              doses previstas. Por privacidade, o nome do medicamento fica oculto na tela bloqueada
              por padrão, e nenhuma dose é registrada automaticamente ao abrir o aviso.
            </Text>
            <View style={styles.modalActions}>
              <Pressable
                style={styles.secondaryButton}
                onPress={dismissPermissionRationale}
                accessibilityRole="button"
              >
                <Text style={styles.secondaryButtonText}>Agora não</Text>
              </Pressable>
              <Pressable
                style={styles.primaryButton}
                onPress={() => void confirmPermissionRationaleAndEnable()}
                accessibilityRole="button"
              >
                <Text style={styles.primaryButtonText}>Continuar e permitir</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </ScrollView>
  );
}

function getStatusPresentation(
  permissionStatus: NotificationPermissionStatus,
  effectiveActive: boolean,
  pendingCount: number,
): { badge: string; description: string } {
  if (permissionStatus === 'unavailable') {
    return {
      badge: 'Indisponível',
      description:
        'A API de notificações locais não está disponível neste dispositivo. Você ainda pode consultar sua agenda e registrar doses manualmente.',
    };
  }
  if (permissionStatus === 'blocked') {
    return {
      badge: 'Bloqueado nas configurações do sistema',
      description:
        'A permissão de notificações foi bloqueada no sistema operacional. Para receber lembretes, habilite as notificações do DoseCerta nas configurações do aparelho.',
    };
  }
  if (permissionStatus === 'denied') {
    return {
      badge: 'Permissão negada',
      description:
        'Sem permissão de notificações os lembretes não podem ser entregues. Autorize o recurso quando desejar ativar os avisos.',
    };
  }
  if (permissionStatus === 'undetermined') {
    return {
      badge: 'Desativado',
      description:
        'Ative o controle abaixo para conceder permissão e começar a agendar lembretes para suas próximas doses.',
    };
  }
  if (!effectiveActive) {
    return {
      badge: 'Desativado por você',
      description:
        'A permissão está concedida, mas você desativou os lembretes. Nenhum aviso pendente está agendado.',
    };
  }
  return {
    badge: `Ativo (${pendingCount} ${pendingCount === 1 ? 'lembrete agendado' : 'lembretes agendados'})`,
    description:
      'Os lembretes futuros dentro da janela suportada estão sincronizados com sua agenda.',
  };
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F7F9FB' },
  content: { padding: 20, gap: 16 },
  header: { gap: 6 },
  backButton: { alignSelf: 'flex-start', paddingVertical: 6 },
  backButtonText: { color: '#0F62FE', fontSize: 15, fontWeight: '600' },
  title: { fontSize: 22, fontWeight: '700', color: '#111827' },
  subtitle: { fontSize: 14, color: '#4B5563', lineHeight: 20 },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    gap: 12,
  },
  statusCard: { backgroundColor: '#F0F6FF', borderColor: '#BFDBFE' },
  statusBadge: { fontSize: 15, fontWeight: '700', color: '#1E3A8A' },
  statusDescription: { fontSize: 14, color: '#1F2937', lineHeight: 20 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  rowText: { flex: 1, gap: 4 },
  rowTitle: { fontSize: 15, fontWeight: '600', color: '#111827' },
  rowHint: { fontSize: 13, color: '#6B7280', lineHeight: 18 },
  divider: { height: 1, backgroundColor: '#E5E7EB' },
  warningCard: {
    backgroundColor: '#FEF3C7',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#F59E0B',
    gap: 4,
  },
  warningTitle: { fontSize: 14, fontWeight: '700', color: '#92400E' },
  warningText: { fontSize: 13, color: '#78350F', lineHeight: 19 },
  infoCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    gap: 6,
  },
  infoTitle: { fontSize: 14, fontWeight: '700', color: '#111827' },
  infoText: { fontSize: 13, color: '#4B5563', lineHeight: 20 },
  primaryButton: {
    backgroundColor: '#0F62FE',
    borderRadius: 8,
    paddingVertical: 11,
    paddingHorizontal: 14,
    alignItems: 'center',
  },
  primaryButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '600' },
  secondaryButton: {
    backgroundColor: '#E5E7EB',
    borderRadius: 8,
    paddingVertical: 11,
    paddingHorizontal: 14,
    alignItems: 'center',
  },
  secondaryButtonText: { color: '#111827', fontSize: 14, fontWeight: '600' },
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(17, 24, 39, 0.55)',
    justifyContent: 'center',
    padding: 20,
  },
  modalCard: { backgroundColor: '#FFFFFF', borderRadius: 14, padding: 20, gap: 14 },
  modalTitle: { fontSize: 18, fontWeight: '700', color: '#111827' },
  modalBody: { fontSize: 14, color: '#374151', lineHeight: 21 },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 10 },
});

