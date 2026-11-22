import { useEffect, useRef, useState } from "react";
import { messageOf } from "./api/client";
export type Screen = "welcome" | "login" | "register" | "home" | "treatments" | "new-treatment" | "treatment-detail" | "edit-schedule" | "history" | "prices" | "profile" | "notifications" | "visual-accessibility";
export type Navigate = (screen: Screen) => void;
export type VisualSize = "standard" | "medium" | "large";

export const visualSizes: Record<VisualSize, { label: string; description: string; fontScale: number; iconScale: number }> = {
  standard: { label: "Padrão", description: "Tamanho original do aplicativo", fontScale: 1, iconScale: 1 },
  medium: { label: "Médio", description: "Leitura mais confortável", fontScale: 1.12, iconScale: 1.12 },
  large: { label: "Grande", description: "Textos e ícones mais destacados", fontScale: 1.25, iconScale: 1.25 },
};

type IconName =
  | "home"
  | "pill"
  | "history"
  | "tag"
  | "user"
  | "bell"
  | "check"
  | "clock"
  | "calendar"
  | "chevron"
  | "arrow"
  | "plus"
  | "search"
  | "eye"
  | "settings"
  | "info"
  | "edit"
  | "logout"
  | "shield"
  | "close"
  | "chart";

const paths: Record<IconName, React.ReactNode> = {
  home: <><path d="m3 11 9-8 9 8" /><path d="M5 10v10h14V10" /><path d="M9 20v-6h6v6" /></>,
  pill: <><path d="M10.5 6.5 17.5 13.5a4.95 4.95 0 0 1-7 7l-7-7a4.95 4.95 0 0 1 7-7Z" /><path d="m7 17 7-7" /></>,
  history: <><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5" /><path d="M12 7v5l3 2" /></>,
  tag: <><path d="M20 12 12 20 4 12V4h8Z" /><circle cx="8.5" cy="8.5" r="1.5" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></>,
  bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" /><path d="M10 21h4" /></>,
  check: <path d="m5 12 4 4L19 6" />,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 10h18" /></>,
  chevron: <path d="m9 18 6-6-6-6" />,
  arrow: <><path d="m15 18-6-6 6-6" /><path d="M9 12h11" /></>,
  plus: <path d="M12 5v14M5 12h14" />,
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></>,
  eye: <><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12" /><circle cx="12" cy="12" r="2.5" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z" /></>,
  info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7h.01" /></>,
  edit: <><path d="m14 4 6 6L9 21H3v-6Z" /><path d="m12 6 6 6" /></>,
  logout: <><path d="M10 17l5-5-5-5M15 12H3" /><path d="M14 3h7v18h-7" /></>,
  shield: <><path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z" /><path d="m9 12 2 2 4-4" /></>,
  close: <path d="m6 6 12 12M18 6 6 18" />,
  chart: <><path d="M4 19V9M10 19V5M16 19v-7M22 19H2" /></>,
};

export function Icon({ name, size = 22 }: { name: IconName; size?: number }) {
  const scaledSize = `calc(${size}px * var(--icon-scale, 1))`;
  return <svg className="icon" width={size} height={size} style={{ width: scaledSize, height: scaledSize }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className="logo-lockup">
      <div className="logo-mark"><Icon name="pill" size={compact ? 19 : 26} /><span className="logo-check"><Icon name="check" size={compact ? 8 : 11} /></span></div>
      <div className={compact ? "logo-name compact" : "logo-name"}>Dose<span>Certa</span></div>
    </div>
  );
}

export function Button({ children, variant = "primary", icon, onClick, disabled = false }: { children: React.ReactNode; variant?: "primary" | "secondary" | "ghost" | "danger"; icon?: IconName; onClick?: () => void; disabled?: boolean }) {
  return <button className={`btn btn-${variant}`} onClick={onClick} disabled={disabled}>{icon && <Icon name={icon} size={19} />}<span>{children}</span></button>;
}

export function Header({ title, subtitle, back, onProfile, brand = false }: { title?: string; subtitle?: string; back?: () => void; onProfile?: () => void; brand?: boolean }) {
  return (
    <header className={brand ? "app-header brand-header" : "app-header"}>
      <div className="header-leading">
        {back ? <button className="icon-button" onClick={back} aria-label="Voltar"><Icon name="arrow" /></button> : brand ? <Logo compact /> : <span className="header-brand-mark" aria-hidden="true"><Icon name="pill" size={19} /></span>}
      </div>
      {title && <div className="header-copy"><div className="header-title" role="heading" aria-level={1}>{title}</div>{subtitle && <div className="header-subtitle">{subtitle}</div>}</div>}
      <div className="header-trailing">
        {onProfile && <button className="avatar-button" onClick={onProfile} aria-label="Abrir perfil"><Icon name="user" size={21} /></button>}
      </div>
    </header>
  );
}

export function Badge({ children, tone = "neutral" }: { children: React.ReactNode; tone?: "success" | "warning" | "danger" | "neutral" | "brand" }) {
  return <span className={`badge badge-${tone}`}>{tone === "success" && <Icon name="check" size={13} />}{tone === "warning" && <Icon name="clock" size={13} />}{children}</span>;
}

export function BottomNav({ active, navigate }: { active: string; navigate: (screen: Screen) => void }) {
  const items: { label: string; icon: IconName; screen: Screen; id: string }[] = [
    { label: "Início", icon: "home", screen: "home", id: "home" },
    { label: "Tratamentos", icon: "pill", screen: "treatments", id: "treatments" },
    { label: "Histórico", icon: "history", screen: "history", id: "history" },
    { label: "Preços", icon: "tag", screen: "prices", id: "prices" },
  ];
  return <nav className="bottom-nav">{items.map((item) => <button key={item.id} className={active === item.id ? "nav-item active" : "nav-item"} onClick={() => navigate(item.screen)}><Icon name={item.icon} /><span>{item.label}</span></button>)}</nav>;
}

export function Field({ label, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return <label className="field"><span>{label}</span><div className="field-box"><input {...props} /></div></label>;
}
export function Notice({ children, error = false }: { children: React.ReactNode; error?: boolean }) {
  return <div className="support-note" role={error ? "alert" : "status"}><Icon name="info" /><span>{children}</span></div>;
}
type Result<T> = { key: string; data?: T; error?: string };
export function useResource<T>(key: string, read: (signal: AbortSignal) => Promise<T>) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Result<T>>();
  const reader = useRef(read);
  const version = JSON.stringify([key, attempt]);
  useEffect(() => { reader.current = read; });
  useEffect(() => {
    const controller = new AbortController();
    reader.current(controller.signal).then(data => {
      if (!controller.signal.aborted) setResult({ key: version, data });
    }).catch(reason => {
      if (!controller.signal.aborted) setResult({ key: version, error: messageOf(reason) });
    });
    return () => controller.abort();
  }, [version]);
  return { ...(result?.key === version ? result : {}), reload: () => setAttempt(a => a + 1) };
}
export function Loading({ error, retry }: { error?: string; retry: () => void }) {
  return error ? <><Notice error>{error}</Notice><Button variant="secondary" onClick={retry}>Tentar novamente</Button></> : <Notice>Carregando…</Notice>;
}
export function useAction() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const lock = useRef(false);
  async function run(action: () => Promise<void>) {
    if (lock.current) return;
    lock.current = true;
    setPending(true);
    setError("");
    try { await action(); }
    catch (e) { setError(messageOf(e)); }
    finally { lock.current = false; setPending(false); }
  }
  return { run, pending, error };
}
export const zone = "America/Sao_Paulo";
export function civilDate(value = new Date(), timeZone = zone) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(value);
  return ["year","month","day"].map(key => parts.find(part => part.type === key)?.value).join("-");
}
export function shiftDate(date: string, offset: number) {
  const result = new Date(date + "T12:00:00Z");
  result.setUTCDate(result.getUTCDate() + offset);
  return result.toISOString().slice(0, 10);
}
export function formatDate(date: string) { return date.split("-").reverse().join("/"); }
export function localDateTime(instant: Date, timeZone = zone) {
  const clock = new Intl.DateTimeFormat("pt-BR", { timeZone, hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
  return civilDate(instant, timeZone) + "T" + clock.format(instant);
}
export function toInstant(local: string, timeZone: string) {
  const target = Date.parse(local + ":00Z");
  if (!Number.isFinite(target)) throw new Error("Informe a data e a hora de vigência.");
  let time = target;
  for (let i = 0; i < 3; i++) {
    const formatted = localDateTime(new Date(time), timeZone);
    if (formatted === local) return new Date(time).toISOString();
    time += target - Date.parse(formatted + ":00Z");
  }
  throw new Error("O horário informado não existe nesse fuso.");
}
