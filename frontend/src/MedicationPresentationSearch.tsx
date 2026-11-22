import { useState } from "react";
import { api } from "./api/client";
import type { MedicationPresentation, MedicationPresentationSearchResponse } from "./types/api";
import { Button, Field, Icon, Notice, useAction } from "./ui";

function packageDescription(presentation: MedicationPresentation) {
  const parts = [presentation.packageType];
  if (presentation.unitCount !== null) {
    parts.push(`${presentation.unitCount} unidades na embalagem`);
  }
  return parts.filter(Boolean).join(" · ");
}

export function MedicationPresentationSearch({
  onSelect,
  onManual,
  onContinue,
  onLeave,
}: {
  onSelect: (presentation: MedicationPresentation) => void;
  onManual: () => void;
  onContinue?: () => void;
  onLeave?: () => void;
}) {
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<MedicationPresentationSearchResponse | null>(null);
  const action = useAction();
  const search = () => action.run(async () => {
    const normalized = query.trim();
    if (normalized.length < 2) {
      throw new Error("Digite pelo menos 2 caracteres para pesquisar.");
    }
    setResult(await api.presentations(normalized));
  });

  return <div className="form-stack">
    <div className="prototype-note">Catálogo experimental: a consulta não representa uma base farmacêutica completa e não recomenda posologia.</div>
    <Field
      label="Medicamento / princípio ativo"
      placeholder="Ex.: losartana"
      value={query}
      onChange={event => setQuery(event.target.value)}
      onKeyDown={event => {
        if (event.key === "Enter") {
          event.preventDefault();
          void search();
        }
      }}
    />
    <Button icon="search" disabled={action.pending} onClick={() => void search()}>Pesquisar apresentações</Button>
    {action.error && <Notice error>{action.error}</Notice>}
    {result && <>
      <div className="result-caption">{result.items.length} apresentação{result.items.length === 1 ? "" : "ões"} encontrada{result.items.length === 1 ? "" : "s"}</div>
      {result.items.length ? <div className="result-list">{result.items.map(presentation => <button
        type="button"
        className="product-result"
        key={presentation.id}
        onClick={() => onSelect(presentation)}
      >
        <div className="medicine-icon soft"><Icon name="pill" /></div>
        <div>
          <strong>{presentation.name}</strong>
          <span>{[presentation.activeIngredient !== presentation.name ? presentation.activeIngredient : null, presentation.concentration, presentation.dosageForm].filter(Boolean).join(" · ")}</span>
          <small>{[presentation.manufacturer, packageDescription(presentation)].filter(Boolean).join(" · ")}</small>
        </div>
        <Icon name="chevron" />
      </button>)}</div> : <Notice>Nenhuma apresentação encontrada. Você pode informar o medicamento manualmente.</Notice>}
    </>}
    <Button variant="secondary" onClick={onManual}>Não encontrei meu medicamento</Button>
    {onContinue && <Button variant="ghost" onClick={onContinue}>Continuar com os medicamentos adicionados</Button>}
    {onLeave && <Button variant="ghost" onClick={onLeave}>Salvar rascunho</Button>}
  </div>;
}

export function presentationPackageDescription(presentation: MedicationPresentation) {
  return packageDescription(presentation);
}
