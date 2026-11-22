import { useState } from "react";
import { Badge, Button, Header, Icon, Notice, formatDate, type Navigate } from "./ui";
import type { RemindersController } from "./notifications/useWebReminders";
import type { NotificationPermissionStatus, ReminderNavigationTarget } from "./notifications/webReminders";

function getPermissionCopy(
  status: NotificationPermissionStatus,
  effectiveActive: boolean,
  pendingCount: number,
): { title: string; description: string; active: boolean } {
  if (status === "unavailable") {
    return {
      title: "Notificações indisponíveis neste ambiente",
      description:
        "Este navegador ou contexto não oferece suporte à API de notificações. Você ainda pode consultar sua agenda e registrar doses manualmente.",
      active: false,
    };
  }
  if (status === "blocked") {
    return {
      title: "Permissão bloqueada no navegador",
      description:
        "Para ativar os lembretes, clique no ícone de cadeado/configurações na barra de endereço do navegador, libere Notificações para este site e volte para esta aba.",
      active: false,
    };
  }
  if (status === "denied") {
    return {
      title: "Permissão negada",
      description:
        "Os lembretes permanecem desativados sem a permissão do navegador. Autorize quando desejar receber avisos.",
      active: false,
    };
  }
  if (status === "undetermined") {
    return {
      title: "Lembretes desativados",
      description:
        "Ative o controle abaixo para conceder permissão ao navegador e agendar avisos para suas próximas doses previstas.",
      active: false,
    };
  }
  if (!effectiveActive) {
    return {
      title: "Desativados por você",
      description:
        "A permissão do navegador está concedida, mas os lembretes estão desligados. Nenhum aviso pendente está agendado.",
      active: false,
    };
  }
  return {
    title:
      pendingCount === 1
        ? "Lembretes ativos · 1 dose programada"
        : `Lembretes ativos · ${pendingCount} doses programadas`,
    description:
      "Os lembretes futuros dentro da janela suportada estão sincronizados com sua agenda do DoseCerta.",
    active: true,
  };
}

export function NotificationsScreen({
  reminders,
  navigate,
  onOpenDose,
}: {
  reminders: RemindersController;
  navigate: Navigate;
  onOpenDose: (target: ReminderNavigationTarget) => void;
}) {
  const [syncing, setSyncing] = useState(false);
  const {
    permissionStatus,
    preferences,
    effectiveActive,
    managedReminders,
    pendingCount,
    showPermissionRationale,
    nonBlockingWarning,
    lastSyncResult,
    requestToggleReminders,
    confirmPermissionRationaleAndEnable,
    dismissPermissionRationale,
    toggleLockScreenPrivacy,
    syncAgendaWindow,
  } = reminders;

  const statusCopy = getPermissionCopy(permissionStatus, effectiveActive, pendingCount);

  const handleManualSync = async () => {
    setSyncing(true);
    try {
      await syncAgendaWindow();
    } finally {
      setSyncing(false);
    }
  };

  return (
    <div className="screen notification-screen">
      <Header title="Notificações e lembretes" back={() => navigate("home")} />
      <main className="content notification-content">
        <div className={statusCopy.active ? "permission-card" : "permission-card disabled"}>
          <span>
            <Icon name={statusCopy.active ? "bell" : "shield"} size={21} />
          </span>
          <div>
            <strong>{statusCopy.title}</strong>
            <p>{statusCopy.description}</p>
          </div>
        </div>

        <div className="settings-group">
          <div className="group-label">Preferências</div>
          <div className="settings-card">
            <label className="toggle-row">
              <div>
                <strong>Ativar lembretes</strong>
                <span>Receber avisos nos horários previstos pela sua agenda.</span>
              </div>
              <input
                type="checkbox"
                checked={effectiveActive}
                disabled={permissionStatus === "unavailable"}
                onChange={(e) => void requestToggleReminders(e.target.checked)}
                aria-label="Ativar lembretes de medicamentos"
              />
              <i />
            </label>

            <label className="toggle-row">
              <div>
                <strong>Privacidade na notificação</strong>
                <span>Oculta o nome do medicamento e exibe apenas o horário previsto.</span>
              </div>
              <input
                type="checkbox"
                checked={preferences.hideSensitiveDetailsOnLockScreen}
                onChange={(e) => void toggleLockScreenPrivacy(e.target.checked)}
                aria-label="Ocultar nome do medicamento na notificação"
              />
              <i />
            </label>
          </div>
        </div>

        {nonBlockingWarning && <Notice error={permissionStatus === "blocked"}>{nonBlockingWarning}</Notice>}

        {lastSyncResult?.truncatedByPlatformLimit && (
          <Notice>
            Apenas as próximas 50 doses mais próximas dentro da janela de 7 dias foram programadas
            para respeitar o limite da plataforma.
          </Notice>
        )}

        <div className="settings-group">
          <div className="notification-toolbar">
            <span>
              Próximos lembretes ({effectiveActive ? managedReminders.length : 0})
            </span>
            <button
              type="button"
              disabled={!effectiveActive || syncing}
              onClick={() => void handleManualSync()}
            >
              <Icon name="clock" size={15} />
              {syncing ? "Sincronizando…" : "Atualizar agenda"}
            </button>
          </div>

          {effectiveActive && managedReminders.length > 0 ? (
            <div className="notification-list">
              {managedReminders.map((item) => (
                <button
                  key={item.plannedDoseId}
                  type="button"
                  className="notification-card unread"
                  onClick={() =>
                    onOpenDose({
                      date: item.date,
                      plannedDoseId: item.plannedDoseId,
                      treatmentId: item.treatmentId,
                      medicationInUseId: item.medicationInUseId,
                    })
                  }
                >
                  <span className="notification-icon brand">
                    <Icon name="bell" size={20} />
                  </span>
                  <div className="notification-copy">
                    <div className="notification-title-row">
                      <strong>
                        {preferences.hideSensitiveDetailsOnLockScreen
                          ? "Lembrete de horário"
                          : item.medicationName}
                      </strong>
                      <small>
                        {formatDate(item.date)} · {item.scheduledLocalTime}
                      </small>
                    </div>
                    <span>
                      {preferences.hideSensitiveDetailsOnLockScreen
                        ? `Dose programada para ${item.scheduledLocalTime}. Detalhes protegidos por privacidade.`
                        : `Programado para ${item.scheduledLocalTime}. A confirmação é sempre manual na agenda.`}
                    </span>
                    <div className="notification-link">
                      <Badge tone="brand">Ver na agenda</Badge>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          ) : (
            <div className="record-empty">
              {effectiveActive
                ? "Nenhuma dose futura pendente nos próximos 7 dias."
                : "Nenhum lembrete agendado enquanto o recurso estiver inativo."}
            </div>
          )}
        </div>

        <div className="support-note strong">
          <Icon name="info" />
          <span>
            <strong>Limitações no navegador (Web):</strong> os lembretes locais funcionam enquanto
            esta aba/navegador estiver aberto (não há serviço de Web Push em segundo plano neste
            MVP). Modo Não Perturbe ou economia de energia podem atrasar avisos. Receber ou abrir
            um lembrete nunca registra uma dose automaticamente — a confirmação ("Tomei" ou "Não
            tomei") é sempre explícita.
          </span>
        </div>
      </main>

      {showPermissionRationale && (
        <div className="modal-overlay">
          <div
            className="bottom-sheet"
            role="dialog"
            aria-modal="true"
            aria-label="Permitir lembretes do DoseCerta"
          >
            <div className="sheet-handle" />
            <button
              type="button"
              className="sheet-close"
              onClick={dismissPermissionRationale}
              aria-label="Fechar"
            >
              <Icon name="close" />
            </button>
            <div className="sheet-pill">
              <Icon name="bell" />
            </div>
            <div className="sheet-title">Ativar lembretes de doses?</div>
            <p>
              O DoseCerta usa notificações locais apenas para avisar os horários das suas doses
              previstas enquanto o aplicativo estiver aberto no navegador.
            </p>
            <div className="sheet-med">
              <strong>Privacidade por padrão</strong>
              <span>
                O nome do medicamento fica oculto na notificação e nenhuma dose é marcada como
                tomada automaticamente.
              </span>
            </div>
            <div className="sheet-actions">
              <Button onClick={() => void confirmPermissionRationaleAndEnable()}>
                Continuar e permitir
              </Button>
              <Button variant="secondary" onClick={dismissPermissionRationale}>
                Agora não
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

