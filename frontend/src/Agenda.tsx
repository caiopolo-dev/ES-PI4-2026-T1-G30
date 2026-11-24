import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "./api/client";
import type { Action, DisplayStatus, Dose, User } from "./types/api";
import { Badge, BottomNav, Button, Field, Header, Icon, Loading, Notice, civilDate, shiftDate, useAction, useResource, zone, type Navigate } from "./ui";
import type { RemindersController } from "./notifications/useWebReminders";
import type { ReminderNavigationTarget } from "./notifications/webReminders";

const labels: Record<DisplayStatus, string> = { UPCOMING: "Prevista", DUE: "No horário", OVERDUE: "Em atraso", TAKEN: "Tomada", SKIPPED: "Não tomada" };
export function Home({
  user,
  navigate,
  reminders,
  reminderTarget,
  onClearReminderTarget,
}: {
  user: User;
  navigate: Navigate;
  reminders: RemindersController;
  reminderTarget: ReminderNavigationTarget | null;
  onClearReminderTarget: () => void;
}) {
  const today = civilDate();
  const [date, setDate] = useState(reminderTarget?.date || today);
  const [selected, setSelected] = useState<Dose | null>(null);
  const [skip, setSkip] = useState(false);
  const [pickDate, setPickDate] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [collapsed, setCollapsed] = useState(false);
  const track = useRef<HTMLDivElement>(null);
  const [card,setCard] = useState(0);
  const action = useAction();
  const result = useResource(date, () => Promise.all([api.agenda(date), api.treatments()]));
  const [agenda, treatments] = result.data || [];
  const doses = agenda?.doses || [];
  const pending = doses.filter(dose => !dose.record);
  const taken = doses.filter(dose => dose.record?.action === "TAKEN").length;
  const registered = doses.length - pending.length;
  const percent = doses.length ? Math.round(100 * registered / doses.length) : null;
  const name = (dose: Dose) => treatments?.find(t => t.id === dose.treatmentId)?.name;
  const open = (dose: Dose) => { setSelected(dose); setSkip(false); setFeedback(""); };
  const choose = (day: string) => { if (day) { setDate(day); setCard(0); setFeedback(""); } };
  const syncAgendaDoses = reminders.syncAgendaDoses;

  // Reconciliação idempotente com base nas doses reais retornadas por GET /doses?date=...
  useEffect(() => {
    if (agenda) {
      void syncAgendaDoses(agenda.doses, agenda.date, agenda.timezone);
    }
  }, [agenda, syncAgendaDoses]);

  // Direciona para a dose tocada na notificação sem registrar automaticamente.
  useEffect(() => {
    if (!reminderTarget) return;
    if (reminderTarget.date && reminderTarget.date !== date) {
      setDate(reminderTarget.date);
      return;
    }
    if (!agenda || agenda.date !== date) return;
    const match = agenda.doses.find(d => d.id === reminderTarget.plannedDoseId);
    if (match && !match.record) {
      setSelected(match);
      setSkip(false);
    }
    onClearReminderTarget();
  }, [agenda, date, onClearReminderTarget, reminderTarget]);

  const record = (value: Action) => action.run(async () => {
    if (!selected) return;
    const recordedDoseId = selected.id;
    try { await api.record(recordedDoseId, value); }
    catch (e) {
      if (e instanceof ApiError && (e.status === 409 || e.status === 404)) { setSelected(null); result.reload(); }
      throw e;
    }
    // Cancela eventual lembrete pendente da dose já registrada, sem modificar histórico.
    void reminders.handleDoseRecorded(recordedDoseId);
    setSelected(null);
    setFeedback(value === "TAKEN" ? "Dose registrada como tomada." : "Dose registrada como não tomada.");
    result.reload();
  });
  const selectedHasReminder = selected ? reminders.managedReminders.some(item => item.plannedDoseId === selected.id) : false;
  return <div className="screen with-nav">
    <div className={"home-hero" + (collapsed ? " collapsed" : "")}><Header brand onProfile={() => navigate("profile")} />
      <div className="hero-greeting"><div className="hero-greeting-inner"><div><span>{new Intl.DateTimeFormat("pt-BR", { timeZone: zone, weekday: "long", day: "numeric", month: "long" }).format(new Date())}</span><div role="heading" aria-level={1}>Olá, {user.name.split(" ")[0]}!</div></div><button className="bell-button" aria-label="Notificações" onClick={() => navigate("notifications")}><Icon name="bell" />{reminders.pendingCount > 0 && <span />}</button></div></div></div>
    <main className="content home-content" onScroll={e => setCollapsed(e.currentTarget.scrollTop > 48)}>
      {feedback && <Notice>{feedback}</Notice>}{!selected && action.error && <Notice error>{action.error}</Notice>}
      {reminders.nonBlockingWarning && reminders.preferences.enabled && <Notice>{reminders.nonBlockingWarning}</Notice>}
      {agenda && <><div className="summary-card"><div className="summary-top"><div><span>Resumo {date === today ? "de hoje" : "do dia"}</span><strong>{registered} de {doses.length} doses registradas</strong></div><div className="progress-number">{percent === null ? "—" : percent + "%"}</div></div><div className="progress-track"><div style={{ width: (percent || 0) + "%" }} /></div><div className="summary-stats"><span><i className="dot success" />{taken} tomadas</span><span><i className="dot warning" />{pending.length} pendentes</span></div></div>
      <section className="next-section"><div className="section-heading"><div role="heading" aria-level={2}>Doses pendentes</div></div>
      {pending.length ? <div className="dose-carousel" ref={track} onScroll={e => setCard(Math.round(e.currentTarget.scrollLeft / (e.currentTarget.clientWidth + 12)))} aria-label="Doses pendentes do dia">{pending.map(dose => <div key={dose.id} className="next-dose-card carousel-card"><div className="next-accent" /><div className="dose-card-top"><div className="medicine-icon"><Icon name="pill" /></div><Badge tone="warning">{labels[dose.displayStatus]}</Badge></div><div className="medicine-name">{dose.medicationName}</div><div className="dose-meta-grid"><div><Icon name="clock" /><span><small>Horário</small><strong>{dose.scheduledLocalTime}</strong></span></div><div><Icon name="pill" /><span><small>Quantidade prevista</small><strong>{dose.plannedQuantity}</strong></span></div></div><div className="treatment-line">{name(dose)}</div><Button onClick={() => open(dose)}>Registrar dose</Button></div>)}</div> : <Notice>Nenhuma dose pendente neste dia.</Notice>}
        {pending.length > 1 && <div className="carousel-pagination">{pending.map((dose,index) => <button key={dose.id} className={index === Math.min(card,pending.length-1) ? "active" : ""} aria-label={"Ver dose das " + dose.scheduledLocalTime} onClick={() => {if (track.current) track.current.scrollTo({left: index * (track.current.clientWidth+12), behavior:"smooth"});}} />)}</div>}</section></>}
      <section><div className="section-heading"><div role="heading" aria-level={2}>Agenda do dia</div><button onClick={() => setPickDate(!pickDate)}>Escolher data</button></div>
        {pickDate && <Field type="date" label="Data da agenda" value={date} onChange={e => choose(e.target.value)} />}
        <div className="date-strip">{Array.from({length: 7}, (_, index) => shiftDate(date, index - 3)).map(day => <button key={day} aria-label={day} className={"date-item" + (date === day ? " active" : "")} onClick={() => choose(day)}><span>{new Intl.DateTimeFormat("pt-BR", {weekday: "narrow",timeZone: "UTC"}).format(new Date(day + "T12:00Z"))}</span><strong>{day.slice(-2)}</strong></button>)}</div>
        {!agenda ? <Loading error={result.error} retry={result.reload} /> : <div className="timeline">{doses.map(dose => <div className="dose-row" key={dose.id}><div className="time-col"><strong>{dose.scheduledLocalTime}</strong><span /></div><div className="dose-row-card"><div className="mini-pill"><Icon name="pill" /></div><div className="dose-row-copy"><strong>{dose.medicationName}</strong><span>{name(dose)}</span><Badge tone={dose.displayStatus === "TAKEN" ? "success" : "warning"}>{labels[dose.displayStatus]}</Badge></div></div></div>)}</div>}
      </section></main><BottomNav active="home" navigate={navigate} />
      {selected && <div className="modal-overlay"><div className="bottom-sheet" role="dialog" aria-modal="true" aria-label="Registrar dose"><div className="sheet-handle" /><button className="sheet-close" disabled={action.pending} onClick={() => setSelected(null)} aria-label="Fechar"><Icon name="close" /></button><div className="sheet-pill"><Icon name="pill" /></div><div className="sheet-title">{skip ? "Confirmar dose não tomada?" : "Você tomou esta dose?"}</div><p>Ocorrência das <strong>{selected.scheduledLocalTime}</strong>.</p><div className="sheet-med"><strong>{selected.medicationName}</strong><span>Quantidade prevista: {selected.plannedQuantity} · {name(selected)}</span></div>
      {action.error && <Notice error>{action.error}</Notice>}<div className="sheet-actions">{skip ? <><Button disabled={action.pending} variant="danger" onClick={() => record("SKIPPED")}>Confirmar que não tomei</Button><Button disabled={action.pending} variant="secondary" onClick={() => setSkip(false)}>Voltar</Button></> : <><Button disabled={action.pending} onClick={() => record("TAKEN")}>Tomei agora</Button><Button disabled={action.pending} variant="secondary" onClick={() => setSkip(true)}>Não tomei</Button><small>{selectedHasReminder ? "Lembrete local agendado para esta dose." : reminders.effectiveActive ? "Lembretes locais ativos para próximas doses." : "Lembretes locais desativados — configure em Notificações."}</small></>}</div></div></div>}
    </div>;
}
