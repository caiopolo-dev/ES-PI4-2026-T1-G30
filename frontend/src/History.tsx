import { useState } from "react";
import { api, periodQuery } from "./api/client";
import type { HistoryItem } from "./types/api";
import { Badge, BottomNav, Button, Field, Header, Icon, Loading, Notice, civilDate, formatDate, localDateTime, shiftDate, useResource, type Navigate } from "./ui";

function buckets(from: string, to: string) {
  const days = Math.round((Date.parse(to) - Date.parse(from)) / 86400000) + 1;
  const width = days <= 7 ? 1 : days <= 56 ? 7 : Math.ceil(days / 7);
  return Array.from({ length: Math.ceil(days/width) }, (_,i) => {
    const start = shiftDate(from, i * width);
    const end = shiftDate(start, width-1);
    return { from: start, to: end > to ? to : end };
  });
}
export function History({ navigate }: { navigate: Navigate }) {
  const list = useResource("treatments", api.treatments);
  const [treatment, setTreatment] = useState("");
  const today = civilDate();
  const [range, setRange] = useState({ from: shiftDate(today,-6), to: today, mode: "7" });
  const [sheet, setSheet] = useState<"period" | "treatment" | null>(null);
  const [start, setStart] = useState(range.from);
  const [end, setEnd] = useState(range.to);
  const selected = list.data?.find(t => t.id === treatment);
  const earliest = (selected ? [selected] : list.data || []).flatMap(t => t.startDate ? [t.startDate] : []).sort()[0];
  const from = range.mode === "all" && earliest && earliest <= today ? earliest : range.from;
  function apply(from: string, to: string, mode: string) { setRange({from,to,mode}); setSheet(null); }
  return <div className="screen with-nav"><Header title="Histórico de adesão" onProfile={() => navigate("profile")} /><main className="content history-content">
    <div className="treatment-analysis"><span>Tratamento analisado</span><button onClick={() => setSheet("treatment")}><span className="analysis-icon"><Icon name="pill" /></span><span><strong>{selected?.name || "Todos os tratamentos"}</strong></span><Icon name="chevron" /></button></div>
    <div className="filter-row">{["7","30"].map(days => <button key={days} className={"filter" + (range.mode === days ? " active" : "")} onClick={() => apply(shiftDate(today, 1-Number(days)),today,days)}>{days} dias</button>)}<button className={"filter" + (["custom","all"].includes(range.mode) ? " active" : "")} onClick={() => { setStart(from); setEnd(range.to); setSheet("period"); }}>Personalizado <Icon name="calendar" size={14} /></button></div>
    <p>{formatDate(from)} a {formatDate(range.to)}</p>
    <Report key={periodQuery(from,range.to,treatment)} from={from} to={range.to} treatment={treatment} />
    </main><BottomNav active="history" navigate={navigate} />
    {sheet && <div className="modal-overlay"><div className="bottom-sheet period-sheet" role="dialog" aria-modal="true" aria-label={sheet === "period" ? "Selecionar período" : "Escolher tratamento"}><div className="sheet-handle" /><button className="sheet-close" onClick={() => setSheet(null)} aria-label="Fechar"><Icon name="close" /></button>
      {sheet === "treatment" ? <><div className="treatment-sheet-title">Escolha o tratamento</div><div className="treatment-options">{!list.data ? <Loading retry={list.reload} error={list.error} /> : [{id:"",name:"Todos os tratamentos"}, ...list.data].map(t => <button key={t.id} className={treatment === t.id ? "treatment-option selected" : "treatment-option"} onClick={() => {setTreatment(t.id); setSheet(null);}}><Icon name="pill" /><strong>{t.name || "Sem nome"}</strong></button>)}</div></> :
      <form className="form-stack" onSubmit={e => {e.preventDefault(); apply(start,end,"custom");}}><div className="treatment-sheet-title">Selecionar período</div>{earliest && earliest <= today && <button type="button" className="btn btn-secondary" onClick={() => apply(earliest,today,"all")}>Todo o período</button>}<Field label="Data de início" required type="date" max={end} value={start} onChange={e => setStart(e.target.value)} /><Field label="Data de término" required type="date" min={start} max={today} value={end} onChange={e => setEnd(e.target.value)} /><button className="btn btn-primary">Aplicar período</button></form>}
    </div></div>}</div>;
}
function Report({from,to,treatment}: {from:string;to:string;treatment:string}) {
  const query = periodQuery(from,to,treatment);
  const summary = useResource(query, signal => api.summary(query,signal));
  const chart = useResource(query, signal => Promise.all(buckets(from,to).map(async b => ({...b, summary: await api.summary(periodQuery(b.from,b.to,treatment),signal)}))));
  const [pageEnd,setPageEnd] = useState(to);
  const [all,setAll] = useState(false);
  const pageStart = [from,shiftDate(pageEnd,-89)].sort()[1];
  const itemsQuery = periodQuery(pageStart,pageEnd,treatment);
  const history = useResource(itemsQuery, signal => api.history(itemsQuery,signal));
  const s = summary.data;
  const h = history.data;
  const records = h ? [...h.items].reverse() : [];
  return <>{!s ? <Loading error={summary.error} retry={summary.reload} /> : <div className="adherence-card"><div className="metric-label">Adesão ao tratamento</div><div className="adherence-main"><div className="ring" style={{"--adherence":s.executionPercent ?? 0} as React.CSSProperties}><span>{s.executionPercent === null ? "—" : Math.round(s.executionPercent)+"%"}</span></div><div><strong>{s.executionStatus === "NO_DATA" ? "Sem doses elegíveis" : "Doses tomadas no período"}</strong><span>{s.takenDoses} de {s.eligiblePlannedDoses} doses elegíveis.</span></div></div><div className="metric-grid">{[[s.eligiblePlannedDoses,"Previstas"],[s.takenDoses,"Tomadas"],[s.skippedDoses,"Não tomadas"],[s.unrecordedOverdueDoses,"Sem registro"],[s.lateTakenDoses,"Fora da margem"],[s.punctualityPercent === null ? "—" : Math.round(s.punctualityPercent)+"%","Pontualidade"]].map(([n,label]) => <div key={label} className="metric"><strong>{n}</strong><span>{label}</span></div>)}</div><small>Margem de pontualidade: {s.punctualityToleranceMinutes} minutos.</small></div>}
  <div className="chart-card"><div className="chart-title"><strong>Evolução da adesão</strong></div>{!chart.data ? <Loading error={chart.error} retry={chart.reload} /> : <div className="bar-chart">{chart.data.map(b => <div key={b.from} title={formatDate(b.from)+" a "+formatDate(b.to)+": "+(b.summary.executionPercent === null ? "Sem dados" : b.summary.executionPercent+"%")}><span style={{height: (b.summary.executionPercent ?? 0)*.72+"%", minHeight: 0}} /><small>{b.summary.executionPercent === null ? "— " : ""}{formatDate(b.from).slice(0,5)}</small></div>)}</div>}</div>
  <div className="section-heading"><div role="heading" aria-level={2}>Histórico de doses</div>{records.length > 3 && <button onClick={() => setAll(!all)}>{all ? "Ver recentes" : "Ver todos"}</button>}</div>
  {pageStart !== from || pageEnd !== to ? <p>{formatDate(pageStart)} a {formatDate(pageEnd)}</p> : null}
  {!h ? <Loading error={history.error} retry={history.reload} /> : <div className="record-list">{records.length ? (all ? records : records.slice(0,3)).map(item => <RecordRow key={item.plannedDoseId} item={item} reference={h.referenceTime} />) : <Notice>Nenhuma ocorrência neste intervalo.</Notice>}</div>}
  {pageStart > from && <Button variant="secondary" onClick={() => {setPageEnd(shiftDate(pageStart,-1)); setAll(true);}}>Período anterior</Button>}{pageEnd < to && <Button variant="ghost" onClick={() => {setPageEnd([to,shiftDate(pageEnd,90)].sort()[0]); setAll(true);}}>Período seguinte</Button>}</>;
}
function RecordRow({item, reference}: {item: HistoryItem;reference:string}) {
  const status = item.isCancelled ? "Cancelada" : item.action === "TAKEN" ? "Tomada" : item.action === "SKIPPED" ? "Não tomada" : Date.parse(item.scheduledAt) > Date.parse(reference) ? "Prevista" : "Sem registro";
  return <div className="record"><span className="record-icon"><Icon name={item.action === "TAKEN" ? "check" : "clock"} /></span><div className="record-copy"><strong>{item.medicationName || "Medicamento sem nome registrado"}</strong><small>{localDateTime(new Date(item.scheduledAt),item.scheduleTimezone).replace("T"," · ")} · Quantidade: {item.plannedQuantity}</small><Badge tone={item.action === "TAKEN" ? "success" : "neutral"}>{status}{item.actualTime && " às "+localDateTime(new Date(item.actualTime),item.scheduleTimezone).slice(-5)}</Badge></div></div>;
}
