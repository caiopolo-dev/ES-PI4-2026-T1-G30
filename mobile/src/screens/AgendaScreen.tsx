import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import type { UseMedicationRemindersState } from '../hooks/useMedicationReminders';
import type { DoseAction, DoseCertaAgendaClient, PlannedDose } from '../types/api';

export interface AgendaScreenProps {
  date: string;
  apiClient: DoseCertaAgendaClient;
  reminders: UseMedicationRemindersState;
  highlightPlannedDoseId?: string | null;
  onOpenSettings: () => void;
}

/**
 * Tela de agenda diária em React Native integrada aos lembretes (Issue #37).
 *
 * Regras garantidas:
 * - Consulta GET /doses?date=AAAA-MM-DD como fonte de verdade;
 * - Reconcilia lembretes de forma idempotente (consultas repetidas não duplicam notificações);
 * - Permissão negada ou indisponibilidade de lembretes NUNCA bloqueia a agenda nem o registro manual;
 * - Registro de dose (PUT /doses/{id}/record) acontece APENAS por ação explícita ("Tomei" / "Não tomei").
 */
export function AgendaScreen({
  date,
  apiClient,
  reminders,
  highlightPlannedDoseId,
  onOpenSettings,
}: AgendaScreenProps) {
  const [doses, setDoses] = useState<PlannedDose[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [recordingId, setRecordingId] = useState<string | null>(null);

  const loadAgenda = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await apiClient.getDailyAgenda(date);
      setDoses(response.doses);
      // Reconciliação idempotente e não bloqueante com base nas doses reais do backend.
      void reminders.syncAgendaDoses(response.doses, { reconciledDates: [response.date] });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar agenda do dia.');
    } finally {
      setLoading(false);
    }
  }, [apiClient, date, reminders]);

  useEffect(() => {
    void loadAgenda();
  }, [loadAgenda]);

  /**
   * Ação explícita do usuário ("Tomei" ou "Não tomei"):
   * única via autorizada para chamar PUT /doses/{id}/record.
   */
  const handleExplicitRecord = useCallback(
    async (dose: PlannedDose, action: DoseAction) => {
      setRecordingId(dose.id);
      setError(null);
      try {
        await apiClient.recordDose(dose.id, action);
        // Política de dose registrada: cancela eventual lembrete pendente sem apagar histórico.
        await reminders.handleDoseRecorded(dose.id);
        await loadAgenda();
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Não foi possível registrar a dose.');
      } finally {
        setRecordingId(null);
      }
    },
    [apiClient, loadAgenda, reminders],
  );

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>Agenda do dia</Text>
          <Text style={styles.dateLabel}>{date}</Text>
        </View>
        <Pressable style={styles.settingsButton} onPress={onOpenSettings} accessibilityRole="button">
          <Text style={styles.settingsButtonText}>
            {reminders.effectiveActive ? 'Lembretes ativos' : 'Configurar lembretes'}
          </Text>
        </Pressable>
      </View>

      {/* Aviso não bloqueante caso notificações estejam negadas/bloqueadas/indisponíveis */}
      {reminders.nonBlockingWarning && (
        <Pressable style={styles.banner} onPress={onOpenSettings}>
          <Text style={styles.bannerText}>{reminders.nonBlockingWarning}</Text>
        </Pressable>
      )}

      {error && (
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      )}

      {loading ? (
        <ActivityIndicator style={styles.loader} size="large" color="#0F62FE" />
      ) : (
        <FlatList
          data={doses}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <Text style={styles.emptyText}>Nenhuma dose prevista para esta data.</Text>
          }
          renderItem={({ item }) => {
            const isHighlighted = item.id === highlightPlannedDoseId;
            const isBusy = recordingId === item.id;
            const alreadyRecorded = item.record !== null;

            return (
              <View style={[styles.doseCard, isHighlighted && styles.highlightedCard]}>
                <View style={styles.doseHeader}>
                  <Text style={styles.doseTime}>{item.scheduledLocalTime}</Text>
                  <Text style={styles.doseStatus}>{item.displayStatus}</Text>
                </View>
                <Text style={styles.doseName}>{item.medicationName}</Text>
                <Text style={styles.doseMeta}>Quantidade prevista: {item.plannedQuantity}</Text>

                {!alreadyRecorded && item.scheduleStatus === 'SCHEDULED' && (
                  <View style={styles.actionsRow}>
                    <Pressable
                      disabled={isBusy}
                      style={styles.takenButton}
                      onPress={() => void handleExplicitRecord(item, 'TAKEN')}
                      accessibilityRole="button"
                    >
                      <Text style={styles.takenButtonText}>Tomei</Text>
                    </Pressable>
                    <Pressable
                      disabled={isBusy}
                      style={styles.skippedButton}
                      onPress={() => void handleExplicitRecord(item, 'SKIPPED')}
                      accessibilityRole="button"
                    >
                      <Text style={styles.skippedButtonText}>Não tomei</Text>
                    </Pressable>
                  </View>
                )}
              </View>
            );
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F7F9FB', padding: 16, gap: 12 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { fontSize: 22, fontWeight: '700', color: '#111827' },
  dateLabel: { fontSize: 13, color: '#6B7280' },
  settingsButton: {
    backgroundColor: '#E0ECFF',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
  },
  settingsButtonText: { color: '#0F62FE', fontSize: 13, fontWeight: '600' },
  banner: {
    backgroundColor: '#FEF3C7',
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: '#F59E0B',
  },
  bannerText: { fontSize: 13, color: '#78350F', lineHeight: 18 },
  errorBox: { backgroundColor: '#FEE2E2', borderRadius: 10, padding: 12 },
  errorText: { color: '#991B1B', fontSize: 13 },
  loader: { marginTop: 32 },
  list: { gap: 12, paddingBottom: 24 },
  emptyText: { textAlign: 'center', color: '#6B7280', marginTop: 32, fontSize: 14 },
  doseCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    gap: 6,
  },
  highlightedCard: { borderColor: '#0F62FE', borderWidth: 2, backgroundColor: '#F0F6FF' },
  doseHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  doseTime: { fontSize: 16, fontWeight: '700', color: '#111827' },
  doseStatus: { fontSize: 12, fontWeight: '600', color: '#374151' },
  doseName: { fontSize: 15, fontWeight: '600', color: '#1F2937' },
  doseMeta: { fontSize: 13, color: '#6B7280' },
  actionsRow: { flexDirection: 'row', gap: 10, marginTop: 8 },
  takenButton: {
    flex: 1,
    backgroundColor: '#15803D',
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: 'center',
  },
  takenButtonText: { color: '#FFFFFF', fontWeight: '600', fontSize: 14 },
  skippedButton: {
    flex: 1,
    backgroundColor: '#E5E7EB',
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: 'center',
  },
  skippedButtonText: { color: '#111827', fontWeight: '600', fontSize: 14 },
});

