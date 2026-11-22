import { useState } from "react";
import { api, ApiError } from "./api/client";
import type { Day, Frequency, Medication, ScheduleResponse, Treatment } from "./types/api";
import { Field, Header, Loading, Notice, civilDate, localDateTime, toInstant, useAction, useResource, zone } from "./ui";

export const dayNames: Record<Day, string> = { MONDAY: "Seg", TUESDAY: "Ter", WEDNESDAY: "Qua", THURSDAY: "Qui", FRIDAY: "Sex", SATURDAY: "Sáb", SUNDAY: "Dom" };
export function ScheduleEditor({ treatment, med, done, cancel }: { treatment: Treatment; med: Medication; done: () => void; cancel: () => void }) {
  const query = useResource(treatment.id + med.id, () => api.schedule(treatment.id, med.id));
  return <div className="screen form-screen"><Header title="Cronograma" subtitle={med.name + " " + med.dose} back={cancel} /><main className="content form-content">
    {!query.data ? <Loading error={query.error} retry={query.reload} /> : <Form key={query.data.revision} treatment={treatment} med={med} model={query.data} reload={query.reload} done={done} />}
  </main></div>;
}
function Form({ treatment, med, model, reload, done }: { treatment: Treatment; med: Medication; model: ScheduleResponse; reload: () => void; done: () => void }) {
  const latest = model.versions.reduce<typeof model.current>((current, item) => !current || item.version > current.version ? item : current, null);
  const timezone = latest?.timezone || zone;
  const today = civilDate(new Date(), timezone);
  const [start, setStart] = useState(latest?.startDate || treatment.startDate || today);
  const [end, setEnd] = useState(latest?.endDate || treatment.endDate || "");
  const [frequency, setFrequency] = useState<Frequency>(latest?.frequency || "EVERY_DAY");
  const [days, setDays] = useState<Day[]>(latest?.daysOfWeek || []);
  const [times, setTimes] = useState<string[]>(latest?.times || [""]);
  const [effective, setEffective] = useState(() => {
    if (!latest && treatment.status === "DRAFT") return start + "T00:00";
    const after = Math.max(Date.now() + 120000, latest?.validFrom ? Date.parse(latest.validFrom) + 60000 : 0);
    return [start + "T00:00", localDateTime(new Date(after), timezone)].sort()[1];
  });
  const [conflict, setConflict] = useState(false);
  const action = useAction();
  const toggle = (day: Day) => setDays(days.includes(day) ? days.filter(d => d !== day) : [...days, day]);
  const save = () => action.run(async () => {
    if (frequency === "DAYS_OF_WEEK" && !days.length) throw new Error("Selecione pelo menos um dia.");
    if (new Set(times).size !== times.length) throw new Error("Informe horários diferentes.");
    const validFrom = toInstant(effective, timezone);
    if (treatment.status === "ACTIVE" && Date.parse(validFrom) <= Date.now()) throw new Error("Escolha uma vigência futura.");
    if (effective.slice(0,10) < start || (end && effective.slice(0,10) > end)) throw new Error("A vigência precisa estar dentro do período do cronograma.");
    try {
      await api.saveSchedule(treatment.id, med.id, { frequency, daysOfWeek: frequency === "EVERY_DAY" ? null : days, times: [...times].sort(), startDate: start, endDate: end || null, timezone, validFrom, revision: model.revision });
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) setConflict(true);
      throw e;
    }
    done();
  });
  return <form className="form-stack" onSubmit={e => { e.preventDefault(); void save(); }}>
    <Notice>{model.current ? "Vigente: " + model.current.times.join(", ") : "Sem versão vigente neste momento."}{latest && latest.version !== model.current?.version ? " Há uma versão programada." : ""} As alterações são gravadas em uma nova versão.</Notice>
    <label className="field"><span>Frequência</span><div className="field-box"><select value={frequency} onChange={e => setFrequency(e.target.value as Frequency)}><option value="EVERY_DAY">Todos os dias</option><option value="DAYS_OF_WEEK">Dias da semana</option></select></div></label>
    {frequency === "DAYS_OF_WEEK" && <div className="filter-row" style={{ flexWrap: "wrap" }}>{(Object.keys(dayNames) as Day[]).map(day => <button key={day} type="button" className={"filter" + (days.includes(day) ? " active" : "")} aria-pressed={days.includes(day)} onClick={() => toggle(day)}>{dayNames[day]}</button>)}</div>}
    {times.map((time, index) => <div key={index} className="field-grid"><Field label={"Horário " + (index + 1)} type="time" required value={time} onChange={e => setTimes(times.map((v,i) => i === index ? e.target.value : v))} />{times.length > 1 && <button type="button" className="text-link" onClick={() => setTimes(times.filter((_, i) => i !== index))}>Remover</button>}</div>)}
    <button type="button" className="btn btn-secondary" onClick={() => setTimes([...times, ""])}>Adicionar horário</button>
    <div className="field-grid"><Field label="Início" type="date" min={treatment.startDate || undefined} required value={start} onChange={e => setStart(e.target.value)} /><Field label="Término" type="date" min={start} max={treatment.endDate || undefined} value={end} onChange={e => setEnd(e.target.value)} /></div>
    <Field type="datetime-local" required label={"Válido a partir de (" + timezone + ")"} value={effective} onChange={e => setEffective(e.target.value)} />
    <small>Notificações de lembrete indisponíveis.</small>
    {action.error && <Notice error>{action.error}</Notice>}
    {conflict ? <button type="button" className="btn btn-secondary" onClick={reload}>Recarregar a revisão atual</button> : <button className="btn btn-primary" disabled={action.pending}>Salvar cronograma</button>}
  </form>;
}
