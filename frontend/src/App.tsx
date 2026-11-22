import { useEffect, useState } from "react";
import { api, session } from "./api/client";
import type { User } from "./types/api";
import { Badge, BottomNav, Button, Field, Header, Icon, Loading, Logo, Notice, useAction, useResource, visualSizes, type Navigate, type Screen, type VisualSize } from "./ui";
import { Home } from "./Agenda";
import { Detail, Treatments, Wizard } from "./Treatments";
import { History } from "./History";

function Welcome({ navigate }: { navigate: (screen: Screen) => void }) {
  return <div className="welcome-screen">
    <div className="welcome-orbit orbit-one" />
    <div className="welcome-orbit orbit-two" />
    <div className="welcome-content">
      <div className="welcome-logo"><Logo /><div className="welcome-kicker">Cuidado no tempo certo</div></div>
      <div className="welcome-copy"><div role="heading" aria-level={1}>Seu tratamento organizado.<br />Sua rotina mais tranquila.</div><p>Agenda de doses e acompanhamento para você cuidar da saúde todos os dias.</p></div>
      <div className="welcome-actions"><Button onClick={() => navigate("login")}>Entrar</Button><Button variant="secondary" onClick={() => navigate("register")}>Criar conta</Button><div className="welcome-safe"><Icon name="shield" size={17} /> Seus dados de saúde protegidos</div></div>
    </div>
  </div>;
}

function Auth({ register, navigate, authenticated, message }: { register: boolean; navigate: Navigate; authenticated: (user: User) => void; message: string }) {
  const [name,setName] = useState("");
  const [email,setEmail] = useState("");
  const [password,setPassword] = useState("");
  const [visible,setVisible] = useState(false);
  const [created,setCreated] = useState(false);
  const creating = register && !created;
  const action = useAction();
  const submit = () => action.run(async () => {
    if (creating) { await api.register(name.trim(),email.trim(),password); setPassword(""); setCreated(true); }
    else { const {token,...user} = await api.login(email.trim(),password); session.save(token); authenticated(user); }
  });
  return <div className="auth-screen"><Header back={action.pending ? undefined : () => navigate("welcome")} /><div className="auth-content"><Logo /><div className="auth-title" role="heading" aria-level={1}>{creating ? "Criar conta" : "Que bom ter você de volta"}</div><p>{creating ? "Cadastre seus dados para acompanhar seus tratamentos." : "Entre para acompanhar seus medicamentos e horários."}</p>
    {created && <Notice>Conta criada. Entre com sua senha.</Notice>}{message && <Notice>{message}</Notice>}
    <form className="form-stack" onSubmit={e => {e.preventDefault(); void submit();}}>
      {creating && <Field label="Nome" autoComplete="name" required maxLength={100} value={name} onChange={e => setName(e.target.value)} />}
      <Field label="E-mail" type="email" autoComplete="email" required maxLength={254} placeholder="seuemail@exemplo.com" value={email} onChange={e => setEmail(e.target.value)} />
      <Field label="Senha" type={visible ? "text" : "password"} required autoComplete={creating ? "new-password" : "current-password"} minLength={creating ? 8 : undefined} maxLength={creating ? 128 : undefined} value={password} onChange={e => setPassword(e.target.value)} />
      <button type="button" className="text-link" onClick={() => setVisible(!visible)}>{visible ? "Ocultar senha" : "Mostrar senha"}</button>
      {!creating && <small>Recuperação de senha indisponível.</small>}{action.error && <Notice error>{action.error}</Notice>}
      <button className="btn btn-primary" disabled={action.pending}>{creating ? "Criar conta" : "Entrar"}</button></form>
      <div className="auth-foot">{creating ? "Já tem conta?" : "Ainda não tem conta?"} <button disabled={action.pending} onClick={() => {setCreated(false); navigate(creating ? "login" : "register");}}>{creating ? "Entrar" : "Criar conta"}</button></div></div></div>;
}
function Profile({ user, navigate, logout, size }: { user: User; navigate: Navigate; logout: () => void; size: VisualSize }) {
  const action = useAction();
  const signOut = () => action.run(async () => { await api.logout(); session.clear(); logout(); });
  return <div className="screen"><Header title="Perfil e configurações" back={() => navigate("home")} /><main className="content profile-content"><div className="profile-card"><div className="profile-avatar">{user.name.trim().split(/\s+/).slice(0,2).map(n => n[0]).join("").toUpperCase()}</div><div><strong>{user.name}</strong><span>{user.email}</span></div></div>
    <div className="settings-group"><div className="group-label">Sua conta</div><div className="settings-card"><div className="settings-row"><span className="settings-icon"><Icon name="user" /></span><span>Edição da conta</span><small>Indisponível</small></div></div></div>
    <div className="settings-group"><div className="group-label">Preferências</div><div className="settings-card"><button className="settings-row" onClick={() => navigate("notifications")}><span className="settings-icon"><Icon name="bell" /></span><span>Notificações e lembretes</span><small>Indisponíveis</small></button><button className="settings-row" onClick={() => navigate("visual-accessibility")}><span className="settings-icon"><Icon name="eye" /></span><span>Tamanho de fonte e ícones</span><small>{visualSizes[size].label}</small></button><div className="settings-row"><span className="settings-icon"><Icon name="clock" /></span><span>Alterar margem de atraso</span><small>Indisponível</small></div></div></div>
    {action.error && <Notice error>{action.error}</Notice>}<button className="logout-row" disabled={action.pending} onClick={signOut}><Icon name="logout" />Sair da conta</button></main></div>;
}
function VisualAccessibility({ navigate, selected, onSelect }: { navigate: (s: Screen) => void; selected: VisualSize; onSelect: (size: VisualSize) => void }) {
  const previewScale = visualSizes[selected];
  return <div className="screen"><Header title="Acessibilidade visual" back={() => navigate("profile")} /><main className="content accessibility-content">
    <div className="accessibility-intro"><span className="settings-icon"><Icon name="eye" /></span><div><strong>Tamanho de fonte e ícones</strong><p>A alteração vale para todo o aplicativo, incluindo agenda, tratamentos, histórico e preços.</p></div></div>
    <div className="group-label">Escolha um tamanho</div>
    <div className="size-options" role="radiogroup" aria-label="Tamanho de fonte e ícones">
      {(Object.keys(visualSizes) as VisualSize[]).map((key) => {
        const option = visualSizes[key];
        return <button key={key} className={selected === key ? "size-option selected" : "size-option"} onClick={() => onSelect(key)} role="radio" aria-checked={selected === key}>
          <span className="size-sample" style={{ "--sample-scale": option.fontScale } as React.CSSProperties}>Aa</span>
          <span><strong>{option.label}</strong><small>{option.description}</small></span>
          <span className="radio-dot" />
        </button>;
      })}
    </div>
    <div className="group-label preview-label">Prévia</div>
    <div className="accessibility-preview" style={{ "--preview-scale": previewScale.fontScale, "--icon-scale": previewScale.iconScale } as React.CSSProperties}>
      <div className="preview-top"><span className="medicine-icon"><Icon name="pill" /></span><Badge tone="warning">Pendente</Badge></div>
      <strong>Nome do medicamento</strong>
      <div className="preview-time"><Icon name="clock" /><span>Prévia do horário e da quantidade</span></div>
      <div className="preview-button"><Icon name="check" size={19} />Registrar dose</div>
    </div>
    <div className="support-note"><Icon name="info" /><span>Você pode voltar ao tamanho padrão a qualquer momento nesta tela.</span></div>
  </main></div>;
}
function Unavailable({ prices, navigate }: {prices:boolean;navigate:Navigate}) {
  return <div className={prices ? "screen with-nav" : "screen notification-screen"}><Header title={prices ? "Preços de medicamentos" : "Notificações"} back={prices ? undefined : () => navigate("home")} onProfile={prices ? () => navigate("profile") : undefined} /><main className="content"><div className="notification-empty"><div><Icon name={prices ? "tag" : "bell"} size={30} /></div><strong>{prices ? "Consulta de preços indisponível" : "Notificações indisponíveis"}</strong><span>{prices ? "A busca, a comparação e o histórico de preços ainda não estão disponíveis." : "O envio de lembretes e o histórico de notificações ainda não estão disponíveis."}</span><Button variant="secondary" onClick={() => navigate("home")}>Voltar para o início</Button></div></main>{prices && <BottomNav active="prices" navigate={navigate} />}</div>;
}

function Restore({ ready, cancel }: {ready:(user:User)=>void;cancel:()=>void}) {
  const result = useResource("session", api.me);
  useEffect(() => { if (result.data) ready(result.data); }, [result.data,ready]);
  return <div className="auth-screen"><div className="auth-content"><Logo /><Loading error={result.error} retry={result.reload} />{result.error && <Button variant="ghost" onClick={cancel}>Voltar ao login</Button>}</div></div>;
}
export default function App() {
  const [restore,setRestore] = useState(() => !!session.get());
  const [screen,setScreen] = useState<Screen>("welcome");
  const [user,setUser] = useState<User | null>(null);
  const [selected,setSelected] = useState("");
  const [message,setMessage] = useState("");
  const [size,setSize] = useState<VisualSize>(() => { const stored = localStorage.getItem("dosecerta.visual-size"); return stored === "medium" || stored === "large" ? stored : "standard"; });
  const navigate: Navigate = s => {setScreen(s); window.scrollTo(0,0);};
  const signedIn = (validUser:User) => { setUser(validUser); setRestore(false); setMessage(""); navigate("home"); };
  const reset = (expired = false) => { setRestore(false); setUser(null); setSelected(""); setMessage(expired ? "Sua sessão expirou. Entre novamente." : ""); navigate("login"); };
  useEffect(() => { const expire = () => reset(true); window.addEventListener("dosecerta:expired",expire); return () => window.removeEventListener("dosecerta:expired",expire); }, []);
  const detail = (id:string) => {setSelected(id); navigate("treatment-detail");};
  const scale = visualSizes[size];
  let page: React.ReactNode;
  if (restore) page = <Restore ready={signedIn} cancel={() => {session.clear(); reset();}} />;
  else if (!user) page = screen === "welcome" ? <Welcome navigate={navigate} /> : <Auth key={screen} register={screen === "register"} authenticated={signedIn} navigate={navigate} message={message} />;
  else switch (screen) {
    case "treatments": page = <Treatments navigate={navigate} select={detail} create={() => {setSelected(""); navigate("new-treatment");}} />; break;
    case "new-treatment": page = <Wizard initialId={selected} back={() => navigate("treatments")} showDetail={detail} />; break;
    case "treatment-detail": page = <Detail id={selected} back={() => navigate("treatments")} resume={() => navigate("new-treatment")} />; break;
    case "history": page = <History navigate={navigate} />; break;
    case "profile": page = <Profile user={user} navigate={navigate} logout={() => reset()} size={size} />; break;
    case "visual-accessibility": page = <VisualAccessibility navigate={navigate} selected={size} onSelect={next => {setSize(next);localStorage.setItem("dosecerta.visual-size",next);}} />; break;
    case "prices": case "notifications": page = <Unavailable prices={screen === "prices"} navigate={navigate} />; break;
    default: page = <Home user={user} navigate={navigate} />;
  }
  return <div className="app-shell"><div className="phone" style={{"--font-scale":scale.fontScale,"--icon-scale":scale.iconScale} as React.CSSProperties}>{page}</div></div>;
}
