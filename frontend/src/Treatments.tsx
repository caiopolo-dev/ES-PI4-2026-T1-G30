import { useState } from "react";
import { api, ApiError } from "./api/client";
import type { Medication, MedicationInput, MedicationPresentation, Treatment, TreatmentInput, TreatmentStatus } from "./types/api";
import { Badge, BottomNav, Button, Field, Header, Icon, Loading, Notice, civilDate, formatDate, localDateTime, toInstant, useAction, useResource, zone, type Navigate } from "./ui";
import { dayNames, ScheduleEditor } from "./Schedule";
import { MedicationPresentationSearch, presentationPackageDescription } from "./MedicationPresentationSearch";

const statuses: Record<TreatmentStatus,string> = { ACTIVE: "Ativos", DRAFT: "Rascunhos", CLOSED: "Encerrados" };
const singular: Record<TreatmentStatus,string> = { ACTIVE: "Ativo", DRAFT: "Rascunho", CLOSED: "Encerrado" };
export function Treatments({ navigate, select, create }: { navigate: Navigate; select: (id: string) => void; create: () => void }) {
  const result = useResource("list", api.treatments);
  const [filter, setFilter] = useState<TreatmentStatus>("ACTIVE");
  const filtered = result.data?.filter(t => t.status === filter);
  return <div className="screen with-nav"><Header title="Meus tratamentos" onProfile={() => navigate("profile")} /><main className="content">
    <Button icon="plus" onClick={create}>Novo tratamento</Button><div className="segmented">{(Object.keys(statuses) as TreatmentStatus[]).map(status => <button key={status} className={filter === status ? "active" : ""} onClick={() => setFilter(status)}>{statuses[status]} {result.data && <b>{result.data.filter(t => t.status === status).length}</b>}</button>)}</div>
    {!filtered ? <Loading error={result.error} retry={result.reload} /> : filtered.length ? <div className="card-stack">{filtered.map(t => <button key={t.id} className="treatment-card" onClick={() => select(t.id)}><div className="treatment-card-top"><div className="medicine-icon soft"><Icon name="pill" /></div><Badge tone={t.status === "ACTIVE" ? "success" : "neutral"}>{singular[t.status]}</Badge></div><strong>{t.name || "Rascunho sem nome"}</strong><span>{t.medications.filter(m => m.active).map(m => m.name + " " + m.dose).join(", ") || "Sem medicamentos ativos"}</span><div className="treatment-card-foot"><span><Icon name="calendar" size={17} />{t.startDate ? formatDate(t.startDate) : "Data a definir"}{t.endDate ? " a " + formatDate(t.endDate) : ""}</span><Icon name="chevron" /></div></button>)}</div> : <Notice>Nenhum tratamento nesta categoria.</Notice>}
    </main><BottomNav active="treatments" navigate={navigate} /></div>;
}

function MetaForm({ treatment, done, advance, draft, readonlyPeriod = false }: { treatment?: Treatment; done: (t: Treatment, leave: boolean) => void; advance: string; draft?: boolean; readonlyPeriod?: boolean }) {
  const [name, setName] = useState(treatment?.name || "");
  const [description, setDescription] = useState(treatment?.description || "");
  const [start, setStart] = useState(treatment?.startDate || civilDate());
  const [end, setEnd] = useState(treatment?.endDate || "");
  const [continuous, setContinuous] = useState(!end);
  const [uncertain, setUncertain] = useState(false);
  const action = useAction();
  const save = (leave: boolean) => action.run(async () => {
    if (!name.trim() || !start) throw new Error("Informe nome e início do tratamento.");
    if (!continuous && (!end || end < start)) throw new Error("Informe um término igual ou posterior ao início.");
    const body: TreatmentInput = {name: name.trim(), description: description.trim() || null, startDate: start, endDate: continuous ? null : end};
    try {
      const saved = treatment ? await api.update(treatment.id, body) : await api.create(body);
      done(saved, leave);
    } catch (e) {
      if (!treatment && (!(e instanceof ApiError) || e.status >= 500)) setUncertain(true);
      throw e;
    }
  });
  return <form className="form-stack" onSubmit={e => { e.preventDefault(); void save(false); }}>
    <Field label="Nome do tratamento *" value={name} required onChange={e => setName(e.target.value)} />
    <Field label="Data de início *" type="date" required disabled={readonlyPeriod} value={start} onChange={e => setStart(e.target.value)} />
    <label className="check-row"><input type="checkbox" disabled={readonlyPeriod} checked={continuous} onChange={e => setContinuous(e.target.checked)} /><span><b>Tratamento contínuo</b><small>Sem data prevista para terminar</small></span></label>
    {!continuous && <Field label="Data de término" type="date" required disabled={readonlyPeriod} min={start} value={end} onChange={e => setEnd(e.target.value)} />}
    <label className="field"><span>Observações</span><textarea value={description} onChange={e => setDescription(e.target.value)} /></label>
    {action.error && <Notice error>{action.error}</Notice>}{uncertain && <Notice>Confira a lista de rascunhos antes de criar novamente. O servidor pode ter recebido o pedido.</Notice>}
    <button className="btn btn-primary" disabled={action.pending || uncertain}>{advance}</button>{draft && <button type="button" className="btn btn-ghost" disabled={action.pending || uncertain} onClick={() => save(true)}>Salvar rascunho</button>}
  </form>;
}

function MedicationForm({ treatment, medication, done, wizard = false }: { treatment: Treatment; medication?: Medication; done: (next: "another" | "continue" | "leave") => void; wizard?: boolean }) {
  const [mode, setMode] = useState<"search" | "form">(medication ? "form" : "search");
  const [selectedPresentation, setSelectedPresentation] = useState<MedicationPresentation | null>(null);
  const [name, setName] = useState(medication?.name || "");
  const [dose, setDose] = useState(medication?.dose || "");
  const [quantity, setQuantity] = useState(medication?.quantity?.toString() || "");
  const [unit, setUnit] = useState(medication?.unit || "");
  const [notes, setNotes] = useState(medication?.observations || "");
  const [validFrom, setValidFrom] = useState(localDateTime(new Date(Date.now() + 3600000)));
  const [reconcile, setReconcile] = useState(false);
  const action = useAction();
  const changedDosage = medication && medication.schedules.length > 0 && (medication.quantity !== (quantity ? Number(quantity) : null) || medication.unit !== (unit.trim() || null));
  const save = (next: "another" | "continue" | "leave") => action.run(async () => {
    if (wizard && !name.trim() && !dose.trim() && !quantity && !unit && !notes && next !== "another" && (next === "leave" || treatment.medications.some(m => m.active))) { done(next); return; }
    if (!name.trim() || !dose.trim() || (quantity && (!Number.isInteger(Number(quantity)) || Number(quantity) < 1))) throw new Error("Informe nome, dosagem e uma quantidade inteira positiva quando preenchida.");
    if ((next !== "leave" || treatment.status === "ACTIVE") && (!quantity || !unit.trim())) throw new Error("Informe a quantidade e a unidade.");
    const body: MedicationInput = { name: name.trim(), informedDose: dose.trim(), quantityPerAdministration: quantity ? Number(quantity) : null, unit: unit.trim() || null, notes: notes.trim() || null };
    try {
      if (medication) {
        const effective = changedDosage ? toInstant(validFrom, zone) : undefined;
        if (effective && Date.parse(effective) <= Date.now()) throw new Error("Escolha uma vigência futura para a posologia.");
        await api.updateMedication(treatment.id, medication.id, { ...body, revision: medication.revision, validFrom: effective });
      } else await api.addMedication(treatment.id, body);
    } catch (e) {
      if (e instanceof TypeError || e instanceof ApiError && (e.status === 409 || e.status >= 500)) setReconcile(true);
      throw e;
    }
    done(next);
  });
  const selectPresentation = (presentation: MedicationPresentation) => {
    setSelectedPresentation(presentation);
    setName(presentation.name || presentation.activeIngredient || "");
    setDose(presentation.concentration || "");
    setUnit(presentation.dosageForm || "");
    // O conteúdo da embalagem nunca representa a quantidade administrada.
    setQuantity("");
    setNotes("");
    setMode("form");
  };
  const manual = () => {
    setSelectedPresentation(null);
    setName("");
    setDose("");
    setQuantity("");
    setUnit("");
    setNotes("");
    setMode("form");
  };
  if (mode === "search") {
    return <MedicationPresentationSearch
      onSelect={selectPresentation}
      onManual={manual}
      onContinue={wizard && treatment.medications.some(item => item.active) ? () => done("continue") : undefined}
      onLeave={wizard ? () => done("leave") : undefined}
    />;
  }
  return <form className="form-stack" onSubmit={e => { e.preventDefault(); void save("continue"); }}>
    {selectedPresentation && <><div className="product-ident"><span>Apresentação experimental selecionada</span><strong>{selectedPresentation.name} {selectedPresentation.concentration}</strong><small>{[selectedPresentation.activeIngredient !== selectedPresentation.name ? selectedPresentation.activeIngredient : null, selectedPresentation.dosageForm, selectedPresentation.manufacturer, presentationPackageDescription(selectedPresentation)].filter(Boolean).join(" · ")}</small></div><Notice>O conteúdo da embalagem é apenas informativo. Preencha abaixo quanto é administrado em cada horário.</Notice></>}
    <Field label="Medicamento / princípio ativo" value={name} onChange={e => setName(e.target.value)} />
    <Field label="Apresentação / concentração" value={dose} onChange={e => setDose(e.target.value)} /><div className="field-grid"><Field label="Quantidade por administração" type="number" min="1" step="1" value={quantity} onChange={e => setQuantity(e.target.value)} /><Field label="Unidade de administração" value={unit} onChange={e => setUnit(e.target.value)} /></div>
    <Field label="Observações opcionais" value={notes} onChange={e => setNotes(e.target.value)} />{changedDosage && <Field label="Nova posologia a partir de (São Paulo)" type="datetime-local" required value={validFrom} onChange={e => setValidFrom(e.target.value)} />}
    {action.error && <Notice error>{action.error}</Notice>}{reconcile ? <button type="button" className="btn btn-secondary" onClick={() => done("another")}>Reconsultar medicamentos gravados</button> : <><button className="btn btn-primary" disabled={action.pending}>{wizard ? "Continuar" : "Salvar medicamento"}</button>
    {wizard && <><button type="button" className="btn btn-secondary" disabled={action.pending} onClick={() => save("another")}>Adicionar outro medicamento</button><button type="button" className="btn btn-ghost" disabled={action.pending} onClick={() => save("leave")}>Salvar rascunho</button></>}{!medication && <button type="button" className="btn btn-ghost" disabled={action.pending} onClick={() => setMode("search")}>Buscar no catálogo experimental</button>}</>}</form>;
}

type DetailMode = { kind: "view" | "meta" | "add" } | { kind: "schedule" | "med"; med: Medication };
export function Detail({ id, back, resume }: { id: string; back: () => void; resume: () => void }) {
  const result = useResource(id, () => api.treatment(id));
  const [mode, setMode] = useState<DetailMode>({kind: "view"});
  const [confirm, setConfirm] = useState("");
  const [notice, setNotice] = useState("");
  const action = useAction();
  const t = result.data;
  const refresh = () => { setMode({kind:"view"}); setConfirm(""); result.reload(); };
  if (!t) return <div className="screen"><Header title="Detalhes do tratamento" back={back} /><main className="content"><Loading error={result.error} retry={result.reload} /></main></div>;
  if (mode.kind === "schedule") return <ScheduleEditor treatment={t} med={mode.med} cancel={refresh} done={refresh} />;
  const edit = mode.kind !== "view";
  const mutate = () => action.run(async () => {
    try {
      if (confirm === "close") await api.close(id);
      else await api.deactivateMedication(id, confirm);
      setNotice(confirm === "close" ? "Tratamento encerrado." : "Medicamento desativado.");
    } finally { refresh(); }
  });
  return <div className="screen"><Header title="Detalhes do tratamento" back={edit ? refresh : back} /><main className="content detail-content">
    {notice && <Notice>{notice}</Notice>}{action.error && <Notice error>{action.error}</Notice>}
    <div className="detail-hero"><div><Badge tone={t.status === "ACTIVE" ? "success" : "neutral"}>{singular[t.status]}</Badge><div role="heading" aria-level={1}>{t.name || "Rascunho sem nome"}</div><p>{t.startDate ? "Início em " + formatDate(t.startDate) : "Início a definir"} · {t.endDate ? formatDate(t.endDate) : "Contínuo"}</p></div>{t.status !== "CLOSED" && !edit && <button className="icon-button raised" aria-label="Editar tratamento" onClick={() => setMode({kind:"meta"})}><Icon name="edit" /></button>}</div>
    {mode.kind === "meta" ? <MetaForm treatment={t} readonlyPeriod={t.status === "ACTIVE"} done={refresh} advance="Salvar alterações" /> : mode.kind === "add" || mode.kind === "med" ? <MedicationForm treatment={t} medication={mode.kind === "med" ? mode.med : undefined} done={refresh} /> : <>
    {t.status === "DRAFT" && <Button onClick={resume}>Continuar rascunho</Button>}
    <div className="section-heading"><div role="heading" aria-level={2}>Medicamentos do tratamento</div></div>
    {t.medications.map(m => <div className="detail-med-card" key={m.id}><div className="detail-med-top"><div className="medicine-icon"><Icon name="pill" /></div><div><strong>{m.name} {m.dose}</strong><span>{m.quantity ?? "—"} {m.unit} por administração · {m.active ? "Ativo" : "Inativo"}</span></div></div>
      <ScheduleOverview tid={id} medication={m} />
      {m.observations && <p>{m.observations}</p>}{m.active && t.status !== "CLOSED" && <><Button variant="secondary" onClick={() => setMode({kind:"schedule",med:m})}>Editar cronograma</Button><Button variant="ghost" onClick={() => setMode({kind:"med",med:m})}>Editar medicamento</Button><Button variant="ghost" onClick={() => setConfirm(m.id)}>Desativar medicamento</Button></>}</div>)}
    {t.status !== "CLOSED" && <Button variant="secondary" onClick={() => setMode({kind:"add"})}>Adicionar medicamento</Button>}
    <div className="info-card"><span>Observações</span><p>{t.description || "Sem observações."}</p></div>
    {t.status === "ACTIVE" && <Button variant="danger" onClick={() => setConfirm("close")}>Encerrar tratamento</Button>}
    {confirm && <div className="info-card"><p>{confirm === "close" ? "Deseja encerrar este tratamento?" : "Deseja desativar este medicamento?"}</p><Button variant="danger" disabled={action.pending} onClick={mutate}>Confirmar</Button><Button variant="ghost" disabled={action.pending} onClick={() => setConfirm("")}>Voltar</Button></div>}</>}</main></div>;
}
function ScheduleOverview({ tid, medication }: {tid: string; medication: Medication}) {
  const r = useResource(tid + medication.id + medication.revision, () => api.schedule(tid, medication.id));
  if (!r.data) return <Loading error={r.error} retry={r.reload} />;
  const current = r.data.current;
  return <div className="detail-lines"><span><Icon name="clock" />{current ? (current.frequency === "EVERY_DAY" ? "Todos os dias" : current.daysOfWeek?.map(d => dayNames[d]).join(", ")) + " · " + current.times.join(", ") : "Sem cronograma vigente"}</span>{r.data.versions.filter(v => v.validFrom && Date.parse(v.validFrom) > Date.now()).map(v => <span key={v.version}>Programado para {localDateTime(new Date(v.validFrom!), v.timezone).replace("T", " ")} · {v.times.join(", ")}</span>)}</div>;
}

const steps = ["Informações", "Medicamentos", "Cronograma", "Revisão"];
export function Wizard({ initialId, back, showDetail }: {initialId: string; back: () => void; showDetail: (id: string) => void}) {
  const [id, setId] = useState(initialId);
  const [step, setStep] = useState(0);
  const [editing, setEditing] = useState<Medication | null>(null);
  const [activated, setActivated] = useState(false);
  const result = useResource(id, () => id ? api.treatment(id) : Promise.resolve(null));
  const action = useAction();
  const t = result.data;
  if (id && !t) return <div className="screen"><Header title="Novo tratamento" back={back} /><main className="content"><Loading error={result.error} retry={result.reload} /></main></div>;
  if (activated || t?.status === "ACTIVE") return <div className="screen center-success"><div className="big-success"><Icon name="check" /></div><div role="heading" aria-level={1}>Tratamento ativo</div><Button onClick={() => showDetail(id)}>Ver tratamento</Button></div>;
  if (t && editing) return <ScheduleEditor treatment={t} med={editing} cancel={() => setEditing(null)} done={() => {setEditing(null); result.reload();}} />;
  const meds = t?.medications.filter(m => m.active) || [];
  const activate = () => action.run(async () => {
    try { await api.activate(id); setActivated(true); }
    catch (e) { result.reload(); throw e; }
  });
  return <div className="screen form-screen"><Header title={initialId ? "Continuar tratamento" : "Novo tratamento"} subtitle={"Etapa " + (step+1) + " de 4"} back={() => step ? setStep(step-1) : back()} /><div className="stepper">{steps.map((label,i) => <div key={label} className={"step" + (i <= step ? " active" : "")}><i>{i < step ? <Icon name="check" size={12} /> : i+1}</i><span>{label}</span></div>)}</div>
    <main className="content form-content"><div className="step-intro"><div role="heading" aria-level={1}>{["Sobre o tratamento","Adicione o medicamento","Defina o cronograma","Revise as informações"][step]}</div></div>
    {step === 0 && <MetaForm treatment={t || undefined} draft advance="Continuar" done={(saved,leave) => { if (leave) showDetail(saved.id); else {setId(saved.id); result.reload(); setStep(1);} }} />}
    {step === 1 && t && <>{meds.map(m => <div key={m.id} className="info-card"><strong>{m.name} {m.dose}</strong><p>{m.quantity ?? "—"} {m.unit}</p></div>)}<MedicationForm treatment={t} wizard done={next => { if (next === "leave") showDetail(id); else { result.reload(); if (next === "continue") setStep(2); } }} /></>}
    {step === 2 && t && <>{meds.map(m => <div className="detail-med-card" key={m.id}><strong>{m.name} {m.dose}</strong><ScheduleOverview tid={id} medication={m} /><Button variant="secondary" onClick={() => setEditing(m)}>{m.schedules.length ? "Editar cronograma" : "Definir cronograma"}</Button></div>)}<Button disabled={!meds.some(m => m.schedules.length)} onClick={() => setStep(3)}>Continuar</Button><Button variant="ghost" onClick={() => showDetail(id)}>Salvar rascunho</Button></>}
    {step === 3 && t && <><div className="review-card"><div className="review-section"><Icon name="pill" /><div><small>Tratamento</small><strong>{t.name}</strong><span>{t.startDate && formatDate(t.startDate)} · {t.endDate ? formatDate(t.endDate) : "Contínuo"}</span></div></div>{meds.map(m => <div className="review-section" key={m.id}><Icon name="clock" /><div><strong>{m.name} {m.dose}</strong><span>{m.quantity ?? "—"} {m.unit}</span><ScheduleOverview tid={id} medication={m} /></div></div>)}</div>
    {action.error && <Notice error>{action.error}</Notice>}<Button disabled={action.pending} onClick={activate}>Ativar tratamento</Button><Button disabled={action.pending} variant="ghost" onClick={() => showDetail(id)}>Salvar rascunho</Button></>}</main></div>;
}
