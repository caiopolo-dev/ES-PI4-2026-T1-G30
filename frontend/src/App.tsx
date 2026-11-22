import { useEffect, useRef, useState } from "react";

type Screen =
  | "welcome"
  | "login"
  | "home"
  | "treatments"
  | "new-treatment"
  | "treatment-detail"
  | "edit-schedule"
  | "history"
  | "prices"
  | "price-search"
  | "comparison"
  | "price-history"
  | "profile"
  | "notifications"
  | "notification-settings"
  | "visual-accessibility";

type VisualSize = "standard" | "medium" | "large";

const visualSizes: Record<VisualSize, { label: string; description: string; fontScale: number; iconScale: number }> = {
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

function Icon({ name, size = 22 }: { name: IconName; size?: number }) {
  const scaledSize = `calc(${size}px * var(--icon-scale, 1))`;
  return <svg className="icon" width={size} height={size} style={{ width: scaledSize, height: scaledSize }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className="logo-lockup">
      <div className="logo-mark"><Icon name="pill" size={compact ? 19 : 26} /><span className="logo-check"><Icon name="check" size={compact ? 8 : 11} /></span></div>
      <div className={compact ? "logo-name compact" : "logo-name"}>Dose<span>Certa</span></div>
    </div>
  );
}

function Button({ children, variant = "primary", icon, onClick, disabled = false }: { children: React.ReactNode; variant?: "primary" | "secondary" | "ghost" | "danger"; icon?: IconName; onClick?: () => void; disabled?: boolean }) {
  return <button className={`btn btn-${variant}`} onClick={onClick} disabled={disabled}>{icon && <Icon name={icon} size={19} />}<span>{children}</span></button>;
}

function Header({ title, subtitle, back, onProfile, brand = false }: { title?: string; subtitle?: string; back?: () => void; onProfile?: () => void; brand?: boolean }) {
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

function Badge({ children, tone = "neutral" }: { children: React.ReactNode; tone?: "success" | "warning" | "danger" | "neutral" | "brand" }) {
  return <span className={`badge badge-${tone}`}>{tone === "success" && <Icon name="check" size={13} />}{tone === "warning" && <Icon name="clock" size={13} />}{children}</span>;
}

function BottomNav({ active, navigate }: { active: string; navigate: (screen: Screen) => void }) {
  const items: { label: string; icon: IconName; screen: Screen; id: string }[] = [
    { label: "Início", icon: "home", screen: "home", id: "home" },
    { label: "Tratamentos", icon: "pill", screen: "treatments", id: "treatments" },
    { label: "Histórico", icon: "history", screen: "history", id: "history" },
    { label: "Preços", icon: "tag", screen: "prices", id: "prices" },
  ];
  return <nav className="bottom-nav">{items.map((item) => <button key={item.id} className={active === item.id ? "nav-item active" : "nav-item"} onClick={() => navigate(item.screen)}><Icon name={item.icon} /><span>{item.label}</span></button>)}</nav>;
}

function Field({ label, placeholder, value, type = "text", rightIcon }: { label: string; placeholder?: string; value?: string; type?: string; rightIcon?: IconName }) {
  return <label className="field"><span>{label}</span><div className="field-box"><input type={type} placeholder={placeholder} defaultValue={value} />{rightIcon && <Icon name={rightIcon} size={20} />}</div></label>;
}

function SectionTitle({ title, action }: { title: string; action?: string }) {
  return <div className="section-heading"><div role="heading" aria-level={2}>{title}</div>{action && <button>{action}</button>}</div>;
}

function Welcome({ navigate }: { navigate: (screen: Screen) => void }) {
  return <div className="welcome-screen">
    <div className="welcome-orbit orbit-one" />
    <div className="welcome-orbit orbit-two" />
    <div className="welcome-content">
      <div className="welcome-logo"><Logo /><div className="welcome-kicker">Cuidado no tempo certo</div></div>
      <div className="welcome-copy"><div role="heading" aria-level={1}>Seu tratamento organizado.<br />Sua rotina mais tranquila.</div><p>Lembretes simples e acompanhamento claro para você cuidar da saúde todos os dias.</p></div>
      <div className="welcome-actions"><Button onClick={() => navigate("login")}>Entrar</Button><Button variant="secondary" onClick={() => navigate("login")}>Criar conta</Button><div className="welcome-safe"><Icon name="shield" size={17} /> Seus dados de saúde protegidos</div></div>
    </div>
  </div>;
}

function Login({ navigate }: { navigate: (screen: Screen) => void }) {
  return <div className="auth-screen">
    <Header back={() => navigate("welcome")} />
    <div className="auth-content">
      <Logo />
      <div className="auth-title" role="heading" aria-level={1}>Que bom ter você de volta</div>
      <p>Entre para acompanhar seus medicamentos e horários.</p>
      <div className="form-stack"><Field label="E-mail" placeholder="seuemail@exemplo.com" type="email" /><Field label="Senha" placeholder="Digite sua senha" type="password" rightIcon="eye" /><button className="text-link">Esqueci minha senha</button><Button onClick={() => navigate("home")}>Entrar</Button></div>
      <div className="auth-foot">Ainda não tem conta? <button>Criar conta</button></div>
    </div>
  </div>;
}

function Home({ navigate, doseTaken, openDose, unreadCount }: { navigate: (s: Screen) => void; doseTaken: boolean; openDose: () => void; unreadCount: number }) {
  const pending = doseTaken ? 1 : 2;
  const [collapsed, setCollapsed] = useState(false);
  return <div className="screen with-nav">
    <div className={collapsed ? "home-hero collapsed" : "home-hero"}>
      <Header brand onProfile={() => navigate("profile")} />
      <div className="hero-greeting" aria-hidden={collapsed}>
        <div className="hero-greeting-inner"><div><span>Quarta-feira, 18 de junho</span><div role="heading" aria-level={1}>Olá, Jeferson!</div></div><button className="bell-button" tabIndex={collapsed ? -1 : 0} onClick={() => navigate("notifications")} aria-label={unreadCount ? `${unreadCount} notificações não lidas` : "Abrir notificações"}><Icon name="bell" />{unreadCount > 0 && <span />}</button></div>
      </div>
    </div>
    <main className="content home-content" onScroll={(event) => {
      const top = event.currentTarget.scrollTop;
      setCollapsed((current) => current ? top > 4 : top > 48);
    }}>
      <div className="summary-card">
        <div className="summary-top"><div><span>Resumo de hoje</span><strong>{doseTaken ? "2 de 3" : "1 de 3"} doses registradas</strong></div><div className="progress-number">{doseTaken ? "67" : "33"}<small>%</small></div></div>
        <div className="progress-track"><div style={{ width: doseTaken ? "67%" : "33%" }} /></div>
        <div className="summary-stats"><span><i className="dot success" />{doseTaken ? 2 : 1} tomada{doseTaken ? "s" : ""}</span><span><i className="dot warning" />{pending} pendente{pending > 1 ? "s" : ""}</span></div>
      </div>
      {doseTaken && <div className="success-banner"><div className="success-icon"><Icon name="check" /></div><div><strong>Dose registrada!</strong><span>Losartana tomada às 08:12</span></div></div>}
      <PendingDoseCarousel doseTaken={doseTaken} openDose={openDose} />
      <section>
        <div className="section-heading"><div role="heading" aria-level={2}>Agenda do dia</div><button>Ver completa</button></div>
        <div className="date-strip">
          {["S|15", "T|16", "Q|17", "Q|18", "S|19", "S|20", "D|21"].map((d, i) => { const [day, num] = d.split("|"); return <button key={num} className={i === 3 ? "date-item active" : "date-item"}><span>{day}</span><strong>{num}</strong>{i === 3 && <i />}</button> })}
        </div>
        <div className="timeline">
          <DoseRow time="08:00" name="Losartana" detail="50 mg • 1 comprimido" status={doseTaken ? "Tomada às 08:12" : "Pendente"} tone={doseTaken ? "success" : "warning"} />
          <DoseRow time="12:30" name="Metformina" detail="850 mg • 1 comprimido" status="Pendente" tone="warning" />
          <DoseRow time="20:00" name="Sinvastatina" detail="20 mg • 1 comprimido" status="Pendente" tone="warning" />
        </div>
      </section>
    </main>
    <BottomNav active="home" navigate={navigate} />
  </div>;
}

const pendingDoses = [
  { id: "losartana", name: "Losartana", dosage: "50 mg", time: "08:00", quantity: "1 comprimido", treatment: "Controle da pressão" },
  { id: "metformina", name: "Metformina", dosage: "850 mg", time: "12:30", quantity: "1 comprimido", treatment: "Controle glicêmico" },
  { id: "sinvastatina", name: "Sinvastatina", dosage: "20 mg", time: "20:00", quantity: "1 comprimido", treatment: "Controle do colesterol" },
];

function PendingDoseCarousel({ doseTaken, openDose }: { doseTaken: boolean; openDose: () => void }) {
  const [current, setCurrent] = useState(0);
  const trackRef = useRef<HTMLDivElement>(null);
  const doses = doseTaken ? pendingDoses.slice(1) : pendingDoses;
  const activeIndex = Math.min(current, doses.length - 1);

  const goToDose = (index: number) => {
    const track = trackRef.current;
    if (!track) return;
    track.scrollTo({ left: index * (track.clientWidth + 12), behavior: "smooth" });
    setCurrent(index);
  };

  return <section className="next-section">
    <SectionTitle title="Próxima dose" />
    <div
      className="dose-carousel"
      ref={trackRef}
      onScroll={(event) => {
        const track = event.currentTarget;
        setCurrent(Math.min(doses.length - 1, Math.round(track.scrollLeft / (track.clientWidth + 12))));
      }}
      aria-label="Doses pendentes do dia"
    >
      {doses.map((dose) => <div className="next-dose-card carousel-card" key={dose.id}>
        <div className="next-accent" />
        <div className="dose-card-top"><div className="medicine-icon"><Icon name="pill" /></div><Badge tone="warning">Pendente</Badge></div>
        <div className="medicine-name">{dose.name} <span>{dose.dosage}</span></div>
        <div className="dose-meta-grid"><div><Icon name="clock" /><span><small>Horário</small><strong>{dose.time}</strong></span></div><div><Icon name="pill" /><span><small>Quantidade</small><strong>{dose.quantity}</strong></span></div></div>
        <div className="treatment-line">{dose.treatment}</div>
        <Button onClick={openDose}>Registrar dose</Button>
      </div>)}
    </div>
    <div className="carousel-pagination" aria-label={`Dose ${activeIndex + 1} de ${doses.length}`}>
      {doses.map((dose, index) => <button key={dose.id} className={index === activeIndex ? "active" : ""} onClick={() => goToDose(index)} aria-label={`Visualizar dose das ${dose.time}`} />)}
    </div>
  </section>;
}

function DoseRow({ time, name, detail, status, tone }: { time: string; name: string; detail: string; status: string; tone: "success" | "warning" }) {
  return <div className="dose-row"><div className="time-col"><strong>{time}</strong><span /></div><div className="dose-row-card"><div className={`mini-pill ${tone}`}><Icon name={tone === "success" ? "check" : "pill"} size={19} /></div><div className="dose-row-copy"><strong>{name}</strong><span>{detail}</span><Badge tone={tone}>{status}</Badge></div><Icon name="chevron" size={19} /></div></div>;
}

function DoseModal({ close, confirm }: { close: () => void; confirm: () => void }) {
  const [confirmSkip, setConfirmSkip] = useState(false);
  return <div className="modal-overlay" onClick={close}><div className="bottom-sheet" onClick={(e) => e.stopPropagation()}><div className="sheet-handle" /><button className="sheet-close" onClick={close}><Icon name="close" /></button>
    {!confirmSkip ? <>
      <div className="sheet-pill"><Icon name="pill" /></div><div className="sheet-title">Você tomou esta dose?</div><p>Confirme apenas a ocorrência das <strong>08:00</strong>.</p>
      <div className="sheet-med"><strong>Losartana 50 mg</strong><span>1 comprimido • Controle da pressão</span></div>
      <div className="sheet-actions"><Button onClick={confirm} icon="check">Tomei agora</Button><Button variant="secondary" onClick={() => setConfirmSkip(true)}>Não tomei</Button><Button variant="ghost" onClick={close} icon="clock">Lembrar mais tarde</Button></div>
    </> : <>
      <div className="sheet-pill warning"><Icon name="info" /></div><div className="sheet-title">Confirmar dose não tomada?</div><p>Este registro ficará no seu histórico. Você não poderá registrar esta dose novamente.</p><div className="sheet-actions"><Button variant="danger" onClick={close}>Confirmar que não tomei</Button><Button variant="secondary" onClick={() => setConfirmSkip(false)}>Voltar</Button></div>
    </>}
  </div></div>;
}

function Treatments({ navigate }: { navigate: (s: Screen) => void }) {
  return <div className="screen with-nav"><Header title="Meus tratamentos" onProfile={() => navigate("profile")} /><main className="content">
    <Button icon="plus" onClick={() => navigate("new-treatment")}>Novo tratamento</Button>
    <div className="segmented"><button className="active">Ativos <b>2</b></button><button>Rascunhos <b>1</b></button><button>Encerrados</button></div>
    <div className="card-stack">
      <TreatmentCard title="Controle da pressão" meds="Losartana 50 mg" schedule="1 dose por dia • Contínuo" onClick={() => navigate("treatment-detail")} />
      <TreatmentCard title="Controle glicêmico" meds="Metformina 850 mg" schedule="2 doses por dia • Contínuo" onClick={() => navigate("treatment-detail")} />
    </div>
    <div className="support-note"><Icon name="info" /><span>Organize aqui seus medicamentos e horários. O DoseCerta não substitui orientação profissional.</span></div>
  </main><BottomNav active="treatments" navigate={navigate} /></div>;
}

function TreatmentCard({ title, meds, schedule, onClick }: { title: string; meds: string; schedule: string; onClick: () => void }) {
  return <button className="treatment-card" onClick={onClick}><div className="treatment-card-top"><div className="medicine-icon soft"><Icon name="pill" /></div><Badge tone="success">Ativo</Badge></div><strong>{title}</strong><span>{meds}</span><div className="treatment-card-foot"><span><Icon name="calendar" size={17} />{schedule}</span><Icon name="chevron" /></div></button>;
}

const steps = ["Informações", "Medicamentos", "Cronograma", "Revisão"];
function NewTreatment({ navigate }: { navigate: (s: Screen) => void }) {
  const [step, setStep] = useState(0);
  const [success, setSuccess] = useState(false);
  if (success) return <div className="screen center-success"><div className="big-success"><Icon name="check" size={36} /></div><div role="heading" aria-level={1}>Tratamento ativado!</div><p>Os lembretes de Losartana foram adicionados à sua agenda.</p><Button onClick={() => navigate("treatments")}>Ver meus tratamentos</Button></div>;
  return <div className="screen form-screen"><Header title="Novo tratamento" subtitle={`Etapa ${step + 1} de 4`} back={() => step ? setStep(step - 1) : navigate("treatments")} />
    <div className="stepper">{steps.map((s, i) => <div className={i <= step ? "step active" : "step"} key={s}><i>{i < step ? <Icon name="check" size={12} /> : i + 1}</i><span>{s}</span></div>)}</div>
    <main className="content form-content">{step === 0 && <InfoStep />}{step === 1 && <MedicationStep />}{step === 2 && <ScheduleStep />}{step === 3 && <ReviewStep />}</main>
    <div className="form-footer">{step === 3 && <Button variant="ghost">Salvar rascunho</Button>}<Button onClick={() => step < 3 ? setStep(step + 1) : setSuccess(true)}>{step === 3 ? "Ativar tratamento" : "Continuar"}</Button></div>
  </div>;
}

function StepIntro({ title, copy }: { title: string; copy: string }) { return <div className="step-intro"><div role="heading" aria-level={1}>{title}</div><p>{copy}</p><span>* Campos obrigatórios</span></div> }
function InfoStep() { return <><StepIntro title="Sobre o tratamento" copy="Comece dando um nome fácil de reconhecer." /><div className="form-stack"><Field label="Nome do tratamento *" value="Controle da pressão" /><div className="field-grid"><Field label="Data de início *" value="18/06/2025" rightIcon="calendar" /><Field label="Data de término" placeholder="Opcional" rightIcon="calendar" /></div><label className="check-row"><input type="checkbox" defaultChecked /><span><b>Tratamento contínuo</b><small>Sem data prevista para terminar</small></span></label><label className="field"><span>Observações</span><textarea placeholder="Adicione uma observação (opcional)" /></label></div></> }
function MedicationStep() { return <><StepIntro title="Adicione o medicamento" copy="Digite as informações como aparecem na embalagem." /><div className="med-form-card"><Field label="Nome do medicamento *" value="Losartana" /><Field label="Dosagem / concentração *" value="50 mg" /><div className="field-grid"><Field label="Quantidade *" value="1" /><label className="field"><span>Unidade *</span><div className="field-box"><select defaultValue="comprimido"><option>comprimido</option><option>cápsula</option><option>mL</option></select><Icon name="chevron" size={18} /></div></label></div></div><Button variant="secondary" icon="plus">Adicionar outro medicamento</Button></> }
function ScheduleStep() { return <><StepIntro title="Defina o cronograma" copy="Escolha quando você precisa tomar este medicamento." /><div className="form-label">Frequência *</div><div className="option-cards"><button className="selected"><span><Icon name="calendar" /></span><b>Todos os dias</b><Icon name="check" size={18} /></button><button><span><Icon name="calendar" /></span><b>Dias da semana</b></button></div><div className="form-label">Horários *</div><div className="time-field"><Icon name="clock" /><strong>08:00</strong><button><Icon name="edit" size={18} /></button></div><Button variant="secondary" icon="plus">Adicionar horário</Button><label className="check-row reminder"><input type="checkbox" defaultChecked /><span><b>Lembrete ativado</b><small>Você receberá uma notificação no horário</small></span></label></> }
function ReviewStep() { return <><StepIntro title="Revise as informações" copy="Confira os dados antes de ativar o tratamento." /><div className="review-card"><ReviewSection icon="pill" title="Tratamento" lines={["Controle da pressão", "Contínuo • Início em 18/06/2025"]} /><ReviewSection icon="pill" title="Medicamento" lines={["Losartana 50 mg", "1 comprimido por administração"]} /><ReviewSection icon="clock" title="Cronograma" lines={["Todos os dias às 08:00", "Lembrete ativado"]} /></div><div className="support-note"><Icon name="info" /><span>Alterações futuras no cronograma não modificam registros anteriores.</span></div></> }
function ReviewSection({ icon, title, lines }: { icon: IconName; title: string; lines: string[] }) { return <div className="review-section"><div className="review-icon"><Icon name={icon} /></div><div><small>{title}</small>{lines.map((x, i) => i === 0 ? <strong key={x}>{x}</strong> : <span key={x}>{x}</span>)}</div><Icon name="edit" size={18} /></div> }

function TreatmentDetail({ navigate }: { navigate: (s: Screen) => void }) {
  return <div className="screen"><Header title="Detalhes do tratamento" back={() => navigate("treatments")} /><main className="content detail-content">
    <div className="detail-hero"><div><Badge tone="success">Ativo</Badge><div role="heading" aria-level={1}>Controle da pressão</div><p>Iniciado em 18 de junho de 2025 • Contínuo</p></div><button className="icon-button raised"><Icon name="edit" /></button></div>
    <SectionTitle title="Medicamentos do tratamento" />
    <div className="detail-med-card"><div className="detail-med-top"><div className="medicine-icon"><Icon name="pill" /></div><div><strong>Losartana 50 mg</strong><span>1 comprimido por administração</span></div></div><div className="divider" /><div className="detail-lines"><span><Icon name="clock" />Todos os dias às <b>08:00</b></span><span><Icon name="bell" />Lembrete ativado</span></div><Button variant="secondary" onClick={() => navigate("edit-schedule")} icon="edit">Editar cronograma</Button></div>
    <div className="info-card"><span>Observações</span><p>Tomar pela manhã, conforme orientação recebida.</p></div>
    <Button variant="danger">Encerrar tratamento</Button>
  </main></div>;
}

function EditSchedule({ navigate }: { navigate: (s: Screen) => void }) {
  const [saved, setSaved] = useState(false);
  return <div className="screen form-screen"><Header title="Editar cronograma" subtitle="Losartana 50 mg" back={() => navigate("treatment-detail")} /><main className="content form-content"><div className="support-note strong"><Icon name="shield" /><span>As mudanças valem apenas para doses futuras. Seu histórico será preservado.</span></div><ScheduleStep />{saved && <div className="inline-success"><Icon name="check" />Novo horário salvo para doses futuras.</div>}</main><div className="form-footer"><Button onClick={() => { setSaved(true); setTimeout(() => navigate("treatment-detail"), 700) }}>Salvar alterações</Button></div></div>;
}

// Reference "today" for the prototype; dose times after NOW_MINUTES are still pending.
const TODAY = new Date(2026, 5, 18);
const NOW_MINUTES = 14 * 60 + 10;
const monthShort = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];
const monthLong = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"];
const weekInitials = ["D", "S", "T", "Q", "Q", "S", "S"];

const startOfDay = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate());
const addDays = (date: Date, days: number) => new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
const daysBetween = (from: Date, to: Date) => Math.round((startOfDay(to).getTime() - startOfDay(from).getTime()) / 86400000);
const sameDay = (a: Date, b: Date) => daysBetween(a, b) === 0;
const laterDate = (a: Date, b: Date) => (a > b ? a : b);
const pad = (n: number) => String(n).padStart(2, "0");
const formatDate = (date: Date) => `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
const toMinutes = (time: string) => { const [h, m] = time.split(":").map(Number); return h * 60 + m; };
const fromMinutes = (minutes: number) => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;

type DoseStatus = "ontime" | "late" | "missed";
type DoseLog = { date: Date; time: string; status: DoseStatus; takenAt?: string };
type TreatmentConfig = {
  name: string;
  helper: string;
  medication: string;
  start: Date;
  times: string[];
  missRate: number;
  lateRate: number;
  /** Fixed outcomes for recent doses, keyed by `${daysAgo}-${timeIndex}`. */
  overrides: Record<string, { status: DoseStatus; delay?: number }>;
};

const treatmentHistory: Record<"pressure" | "glycemic", TreatmentConfig> = {
  pressure: {
    name: "Controle da pressão",
    helper: "1 medicamento • Ativo",
    medication: "Losartana 50 mg",
    start: new Date(2025, 5, 18),
    times: ["08:00"],
    missRate: .05,
    lateRate: .09,
    overrides: { "0-0": { status: "ontime", delay: 12 }, "1-0": { status: "late", delay: 38 }, "2-0": { status: "missed" } },
  },
  glycemic: {
    name: "Controle glicêmico",
    helper: "1 medicamento • Ativo",
    medication: "Metformina 850 mg",
    start: new Date(2026, 0, 12),
    times: ["12:30", "19:30"],
    missRate: .07,
    lateRate: .13,
    overrides: { "0-0": { status: "ontime", delay: 12 }, "1-1": { status: "late", delay: 45 }, "2-0": { status: "missed" } },
  },
};

type TreatmentHistoryKey = keyof typeof treatmentHistory;
type QuickPeriod = "7" | "30" | "custom";
type CustomPeriod = { mode: "all" } | { mode: "range"; start: Date; end: Date | null };

// Deterministic pseudo-random value in [0, 1) so the mock history is stable between renders.
function seeded(seed: string) {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) { h ^= seed.charCodeAt(i); h = Math.imul(h, 16777619); }
  h ^= h >>> 15; h = Math.imul(h, 2246822507); h ^= h >>> 13;
  return (h >>> 0) / 4294967296;
}

function buildDoseLogs(key: TreatmentHistoryKey, from: Date, to: Date): DoseLog[] {
  const treatment = treatmentHistory[key];
  const logs: DoseLog[] = [];
  const last = to > TODAY ? TODAY : startOfDay(to);
  for (let day = laterDate(startOfDay(from), treatment.start); day <= last; day = addDays(day, 1)) {
    const daysAgo = daysBetween(day, TODAY);
    treatment.times.forEach((time, index) => {
      if (daysAgo === 0 && toMinutes(time) > NOW_MINUTES) return;
      const seed = `${key}-${formatDate(day)}-${index}`;
      const override = treatment.overrides[`${daysAgo}-${index}`];
      const roll = seeded(seed);
      const status: DoseStatus = override?.status ?? (roll < treatment.missRate ? "missed" : roll < treatment.missRate + treatment.lateRate ? "late" : "ontime");
      const delay = override?.delay ?? (status === "late" ? 30 + Math.floor(seeded(`${seed}-d`) * 46) : Math.floor(seeded(`${seed}-d`) * 15));
      logs.push({ date: day, time, status, takenAt: status === "missed" ? undefined : fromMinutes(toMinutes(time) + delay) });
    });
  }
  return logs;
}

function summarizeLogs(logs: DoseLog[]) {
  const expected = logs.length;
  const missed = logs.filter((log) => log.status === "missed").length;
  const late = logs.filter((log) => log.status === "late").length;
  const taken = expected - missed;
  return { expected, taken, missed, late, percent: expected ? Math.round((taken / expected) * 100) : 0 };
}

function buildBars(logs: DoseLog[], from: Date, to: Date) {
  const span = daysBetween(from, to) + 1;
  const bucket = (start: Date, end: Date, label: string, title: string) => {
    const items = logs.filter((log) => log.date >= start && log.date <= end);
    const value = items.length ? Math.round((items.filter((log) => log.status !== "missed").length / items.length) * 100) : 0;
    return { label, title, value };
  };
  if (span <= 7) {
    return { subtitle: "Adesão por dia", bars: Array.from({ length: span }, (_, i) => { const day = addDays(from, i); return bucket(day, day, weekInitials[day.getDay()], formatDate(day)); }) };
  }
  if (span <= 56) {
    const count = Math.ceil(span / 7);
    return { subtitle: "Adesão por semana", bars: Array.from({ length: count }, (_, i) => {
      const end = addDays(to, -7 * (count - 1 - i));
      const start = laterDate(addDays(end, -6), from);
      return bucket(start, end, `${pad(start.getDate())}/${pad(start.getMonth() + 1)}`, `${formatDate(start)} a ${formatDate(end)}`);
    }) };
  }
  const months: Date[] = [];
  for (let m = new Date(from.getFullYear(), from.getMonth(), 1); m <= to; m = new Date(m.getFullYear(), m.getMonth() + 1, 1)) months.push(m);
  const group = months.length > 8 ? Math.ceil(months.length / 7) : 1;
  const crossYear = from.getFullYear() !== to.getFullYear();
  const bars = [];
  for (let i = 0; i < months.length; i += group) {
    const first = months[i];
    const lastMonth = months[Math.min(i + group, months.length) - 1];
    const start = laterDate(first, from);
    const monthEnd = new Date(lastMonth.getFullYear(), lastMonth.getMonth() + 1, 0);
    const end = monthEnd > to ? to : monthEnd;
    bars.push(bucket(start, end, `${monthShort[first.getMonth()]}${crossYear ? `/${String(first.getFullYear()).slice(2)}` : ""}`, `${formatDate(start)} a ${formatDate(end)}`));
  }
  return { subtitle: group === 1 ? "Adesão por mês" : `Adesão a cada ${group} meses`, bars };
}

function recordDate(log: DoseLog) {
  const daysAgo = daysBetween(log.date, TODAY);
  const day = daysAgo === 0 ? "Hoje" : daysAgo === 1 ? "Ontem" : `${log.date.getDate()} ${monthShort[log.date.getMonth()]}${log.date.getFullYear() !== TODAY.getFullYear() ? ` ${log.date.getFullYear()}` : ""}`;
  return `${day} • Prevista ${log.time}`;
}

function History({ navigate }: { navigate: (s: Screen) => void }) {
  const [selectedTreatment, setSelectedTreatment] = useState<TreatmentHistoryKey>("pressure");
  const [treatmentSheetOpen, setTreatmentSheetOpen] = useState(false);
  const [quickPeriod, setQuickPeriod] = useState<QuickPeriod>("7");
  const [customPeriod, setCustomPeriod] = useState<CustomPeriod>({ mode: "all" });
  const [periodSheetOpen, setPeriodSheetOpen] = useState(false);
  const data = treatmentHistory[selectedTreatment];

  const range = quickPeriod === "7" ? { from: addDays(TODAY, -6), to: TODAY }
    : quickPeriod === "30" ? { from: addDays(TODAY, -29), to: TODAY }
    : customPeriod.mode === "all" ? { from: data.start, to: TODAY }
    : { from: customPeriod.start, to: customPeriod.end ?? TODAY };
  const effectiveFrom = laterDate(range.from, data.start);
  const logs = buildDoseLogs(selectedTreatment, range.from, range.to);
  const stats = summarizeLogs(logs);
  const chart = effectiveFrom <= range.to ? buildBars(logs, effectiveFrom, range.to) : { subtitle: "Adesão por dia", bars: [] };
  const records = logs.slice(-3).reverse().map((log) => ({
    name: data.medication,
    date: recordDate(log),
    value: log.status === "missed" ? "Não tomada" : `Tomada às ${log.takenAt}`,
    tone: log.status === "missed" ? "danger" as const : log.status === "late" ? "warning" as const : "success" as const,
    key: `${formatDate(log.date)}-${log.time}`,
  }));
  const message = stats.expected === 0 ? "Sem doses neste período" : stats.percent >= 90 ? "Muito bem, Jeferson!" : stats.percent >= 75 ? "Você está no caminho certo" : "Vamos retomar o ritmo";
  const summary = stats.expected === 0 ? `Este tratamento começou em ${formatDate(data.start)}.` : `Você tomou ${stats.taken} de ${stats.expected} doses elegíveis.`;
  const periodLabel = customPeriod.mode === "all" ? "Todo o período" : `${formatDate(customPeriod.start)} a ${formatDate(customPeriod.end ?? TODAY)}`;
  const badge = quickPeriod === "7" ? "7 dias" : quickPeriod === "30" ? "30 dias" : customPeriod.mode === "all" ? "Todo o período" : `${daysBetween(range.from, range.to) + 1} dias`;

  return <div className="screen with-nav"><Header title="Histórico de adesão" onProfile={() => navigate("profile")} /><main className="content history-content">
    <div className="treatment-analysis">
      <span>Tratamento analisado</span>
      <button onClick={() => setTreatmentSheetOpen(true)} aria-label={`Alterar tratamento. Selecionado: ${data.name}`}>
        <span className="analysis-icon"><Icon name="pill" size={20} /></span>
        <span><strong>{data.name}</strong><small>{data.helper}</small></span>
        <Icon name="chevron" size={20} />
      </button>
    </div>
    <div className="filter-row" role="group" aria-label="Período do histórico">
      <button className={quickPeriod === "7" ? "filter active" : "filter"} aria-pressed={quickPeriod === "7"} onClick={() => setQuickPeriod("7")}>Últimos 7 dias</button>
      <button className={quickPeriod === "30" ? "filter active" : "filter"} aria-pressed={quickPeriod === "30"} onClick={() => setQuickPeriod("30")}>30 dias</button>
      <button className={quickPeriod === "custom" ? "filter active" : "filter"} aria-pressed={quickPeriod === "custom"} aria-haspopup="dialog" onClick={() => setPeriodSheetOpen(true)}>Personalizado <Icon name="calendar" size={14} /></button>
    </div>
    {quickPeriod === "custom" && <button className="period-applied" onClick={() => setPeriodSheetOpen(true)} aria-label={`Período aplicado: ${periodLabel}. Toque para alterar.`}>
      <span className="period-applied-icon"><Icon name="calendar" size={18} /></span>
      <span><small>Período aplicado</small><strong>{periodLabel}</strong>{customPeriod.mode === "all" && <span className="period-applied-note">Desde {formatDate(data.start)}, início do tratamento</span>}</span>
      <span className="period-applied-action">Alterar</span>
    </button>}
    <div className="adherence-card"><div className="metric-label">Adesão ao tratamento</div><div className="adherence-main"><div className="ring" style={{ "--adherence": stats.percent } as React.CSSProperties}><span>{stats.percent}<small>%</small></span></div><div><strong>{message}</strong><span>{summary}</span></div></div><div className="metric-grid"><Metric n={String(stats.expected)} label="Previstas" /><Metric n={String(stats.taken)} label="Tomadas" tone="success" /><Metric n={String(stats.missed)} label="Não tomada" tone="danger" /><Metric n={String(stats.late)} label="Com atraso" tone="warning" /></div></div>
    <div className="chart-card"><div className="chart-title"><div><strong>Evolução diária</strong><span>{chart.subtitle}</span></div><Badge tone="brand">{badge}</Badge></div><div className="bar-chart">{chart.bars.map((bar, i) => <div key={i} title={`${bar.title}: ${bar.value}%`}><span style={{ height: `${bar.value * .72}%` }} className={bar.value < 80 ? "low" : ""} /><small>{bar.label}</small></div>)}</div></div>
    <SectionTitle title="Registros recentes" action="Ver todos" />
    <div className="record-list">{records.length ? records.map(({ key, ...record }) => <Record key={key} {...record} />) : <div className="record-empty">Nenhum registro neste período.</div>}</div>
  </main><BottomNav active="history" navigate={navigate} />
    {treatmentSheetOpen && <TreatmentSelectorSheet selected={selectedTreatment} onClose={() => setTreatmentSheetOpen(false)} onSelect={(treatment) => { setSelectedTreatment(treatment); setTreatmentSheetOpen(false); }} />}
    {periodSheetOpen && <PeriodSheet initial={customPeriod} treatment={selectedTreatment} onClose={() => setPeriodSheetOpen(false)} onApply={(period) => { setCustomPeriod(period); setQuickPeriod("custom"); setPeriodSheetOpen(false); }} />}
  </div>;
}

function PeriodSheet({ initial, treatment, onClose, onApply }: { initial: CustomPeriod; treatment: TreatmentHistoryKey; onClose: () => void; onApply: (period: CustomPeriod) => void }) {
  const config = treatmentHistory[treatment];
  const [mode, setMode] = useState<CustomPeriod["mode"]>(initial.mode);
  const [start, setStart] = useState<Date | null>(initial.mode === "range" ? laterDate(initial.start, config.start) : null);
  const [end, setEnd] = useState<Date | null>(initial.mode === "range" ? initial.end : null);
  const [endMode, setEndMode] = useState<"today" | "date">(initial.mode === "range" && initial.end ? "date" : "today");
  const [activeField, setActiveField] = useState<"start" | "end" | null>(null);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const effectiveEnd = endMode === "today" ? TODAY : end;
  const missing = mode === "range" ? (!start ? "Selecione a data de início." : endMode === "date" && !end ? "Selecione a data de término ou escolha “Até hoje”." : null) : null;
  const preview = missing ?? (mode === "all" ? `Todo o período • desde ${formatDate(config.start)}` : start && effectiveEnd ? `${formatDate(start)} a ${formatDate(effectiveEnd)} • ${daysBetween(start, effectiveEnd) + 1} ${daysBetween(start, effectiveEnd) === 0 ? "dia" : "dias"}` : "");

  const chooseRange = () => { setMode("range"); if (!start) setActiveField("start"); };
  const pickStart = (date: Date) => { setStart(date); setActiveField(endMode === "date" && !end ? "end" : null); };
  const pickEnd = (date: Date) => { setEnd(date); setActiveField(null); };
  const chooseEndMode = (next: "today" | "date") => {
    setEndMode(next);
    if (next === "today") setActiveField((field) => (field === "end" ? null : field));
    else if (start && !end) setActiveField("end");
  };
  const apply = () => {
    if (mode === "all") onApply({ mode: "all" });
    else if (start && !missing) onApply({ mode: "range", start, end: endMode === "date" ? end : null });
  };

  return <div className="modal-overlay" onClick={onClose}><div className="bottom-sheet period-sheet" role="dialog" aria-modal="true" aria-labelledby="period-sheet-title" onClick={(event) => event.stopPropagation()}>
    <div className="sheet-handle" />
    <button className="sheet-close" onClick={onClose} aria-label="Fechar seleção de período"><Icon name="close" /></button>
    <div className="period-sheet-body">
      <div className="treatment-sheet-title" id="period-sheet-title">Selecionar período</div>
      <p className="period-sheet-lead">Escolha quais registros de <strong>{config.name}</strong> você deseja ver.</p>
      <div className="period-options" role="radiogroup" aria-label="Tipo de período">
        <button role="radio" aria-checked={mode === "all"} className={mode === "all" ? "period-option selected" : "period-option"} onClick={() => { setMode("all"); setActiveField(null); }}>
          <span className="analysis-icon"><Icon name="history" size={20} /></span>
          <span><strong>Todo o período</strong><small>Desde {formatDate(config.start)} até hoje</small></span>
          <span className="radio-dot" aria-hidden="true" />
        </button>
        <button role="radio" aria-checked={mode === "range"} className={mode === "range" ? "period-option selected" : "period-option"} onClick={chooseRange}>
          <span className="analysis-icon"><Icon name="calendar" size={20} /></span>
          <span><strong>Intervalo personalizado</strong><small>Escolha as datas de início e término</small></span>
          <span className="radio-dot" aria-hidden="true" />
        </button>
      </div>
      {mode === "range" && <div className="range-panel">
        <div className="range-field">
          <span className="range-label">Data de início</span>
          <DateTrigger label="Data de início" value={start} open={activeField === "start"} onClick={() => setActiveField((field) => (field === "start" ? null : "start"))} />
          {activeField === "start" && <CalendarPicker selected={start} rangeStart={start} rangeEnd={effectiveEnd} min={config.start} max={endMode === "date" && end ? end : TODAY} onSelect={pickStart} />}
        </div>
        <div className="range-field">
          <span className="range-label">Data de término <em>(opcional)</em></span>
          <div className="end-mode" role="radiogroup" aria-label="Data de término">
            <button role="radio" aria-checked={endMode === "today"} onClick={() => chooseEndMode("today")}>{endMode === "today" && <Icon name="check" size={16} />}Até hoje</button>
            <button role="radio" aria-checked={endMode === "date"} onClick={() => chooseEndMode("date")}>{endMode === "date" && <Icon name="check" size={16} />}Escolher data</button>
          </div>
          {endMode === "date" && <DateTrigger label="Data de término" value={end} open={activeField === "end"} onClick={() => setActiveField((field) => (field === "end" ? null : "end"))} />}
          {endMode === "date" && activeField === "end" && <CalendarPicker selected={end} rangeStart={start} rangeEnd={end} min={start ?? config.start} max={TODAY} onSelect={pickEnd} />}
        </div>
      </div>}
    </div>
    <div className="period-sheet-footer">
      <p className={missing ? "period-preview pending" : "period-preview"} aria-live="polite"><Icon name={missing ? "info" : "calendar"} size={16} />{preview}</p>
      <Button icon="check" onClick={apply} disabled={Boolean(missing)}>Aplicar período</Button>
    </div>
  </div></div>;
}

function DateTrigger({ label, value, open, onClick }: { label: string; value: Date | null; open: boolean; onClick: () => void }) {
  return <button type="button" className={open ? "date-trigger open" : "date-trigger"} onClick={onClick} aria-expanded={open} aria-label={`${label}: ${value ? formatDate(value) : "não selecionada"}. ${open ? "Fechar" : "Abrir"} calendário`}>
    <Icon name="calendar" size={20} />
    <span className={value ? "" : "placeholder"}>{value ? formatDate(value) : "Selecionar data"}</span>
    <Icon name="chevron" size={18} />
  </button>;
}

function CalendarPicker({ selected, rangeStart, rangeEnd, min, max, onSelect }: { selected: Date | null; rangeStart: Date | null; rangeEnd: Date | null; min: Date; max: Date; onSelect: (date: Date) => void }) {
  const [view, setView] = useState(() => { const base = selected ?? max; return new Date(base.getFullYear(), base.getMonth(), 1); });
  const year = view.getFullYear();
  const month = view.getMonth();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const canPrev = view > new Date(min.getFullYear(), min.getMonth(), 1);
  const canNext = view < new Date(max.getFullYear(), max.getMonth(), 1);
  const monthName = monthLong[month][0].toUpperCase() + monthLong[month].slice(1);
  const cells: (Date | null)[] = [...Array<null>(view.getDay()).fill(null), ...Array.from({ length: daysInMonth }, (_, i) => new Date(year, month, i + 1))];

  return <div className="calendar">
    <div className="calendar-head">
      <button type="button" className="calendar-nav prev" disabled={!canPrev} onClick={() => setView(new Date(year, month - 1, 1))} aria-label="Mês anterior"><Icon name="chevron" size={20} /></button>
      <strong aria-live="polite">{monthName} de {year}</strong>
      <button type="button" className="calendar-nav" disabled={!canNext} onClick={() => setView(new Date(year, month + 1, 1))} aria-label="Próximo mês"><Icon name="chevron" size={20} /></button>
    </div>
    <div className="calendar-grid calendar-weekdays" aria-hidden="true">{weekInitials.map((day, i) => <span key={i}>{day}</span>)}</div>
    <div className="calendar-grid">
      {cells.map((date, i) => {
        if (!date) return <span key={`empty-${i}`} />;
        const disabled = date < startOfDay(min) || date > startOfDay(max);
        const isSelected = Boolean(selected && sameDay(date, selected));
        const isEdge = !isSelected && Boolean((rangeStart && sameDay(date, rangeStart)) || (rangeEnd && sameDay(date, rangeEnd)));
        const inRange = Boolean(rangeStart && rangeEnd && date > rangeStart && date < rangeEnd);
        const isToday = sameDay(date, TODAY);
        const className = ["calendar-day", isSelected && "selected", isEdge && "edge", inRange && "in-range", isToday && "today"].filter(Boolean).join(" ");
        return <button key={i} type="button" className={className} disabled={disabled} aria-pressed={isSelected} aria-label={`${date.getDate()} de ${monthLong[month]} de ${year}${isToday ? ", hoje" : ""}`} onClick={() => onSelect(date)}>{date.getDate()}</button>;
      })}
    </div>
  </div>;
}

function TreatmentSelectorSheet({ selected, onClose, onSelect }: { selected: TreatmentHistoryKey; onClose: () => void; onSelect: (treatment: TreatmentHistoryKey) => void }) {
  return <div className="modal-overlay" onClick={onClose}><div className="bottom-sheet treatment-sheet" onClick={(event) => event.stopPropagation()}>
    <div className="sheet-handle" />
    <button className="sheet-close" onClick={onClose} aria-label="Fechar seleção"><Icon name="close" /></button>
    <div className="treatment-sheet-title">Escolha o tratamento</div>
    <p>Selecione qual histórico você deseja analisar.</p>
    <div className="treatment-options">
      {(Object.keys(treatmentHistory) as TreatmentHistoryKey[]).map((key) => {
        const treatment = treatmentHistory[key];
        const isSelected = selected === key;
        return <button className={isSelected ? "treatment-option selected" : "treatment-option"} key={key} onClick={() => onSelect(key)}>
          <span className="analysis-icon"><Icon name="pill" size={20} /></span>
          <span><strong>{treatment.name}</strong><small>{treatment.helper}</small></span>
          <span className="option-check">{isSelected && <Icon name="check" size={16} />}</span>
        </button>;
      })}
    </div>
  </div></div>;
}
function Metric({ n, label, tone = "" }: { n: string; label: string; tone?: string }) { return <div className={`metric ${tone}`}><strong>{n}</strong><span>{label}</span></div> }
function Record({ name, date, value, tone }: { name: string; date: string; value: string; tone: "success" | "warning" | "danger" }) { return <button className="record"><span className={`record-icon ${tone}`}><Icon name={tone === "success" ? "check" : tone === "warning" ? "clock" : "close"} /></span><span className="record-copy"><strong>{name}</strong><small>{date}</small><Badge tone={tone}>{value}</Badge></span><Icon name="chevron" size={18} /></button> }

function Prices({ navigate }: { navigate: (s: Screen) => void }) {
  return <div className="screen with-nav"><Header title="Preços de medicamentos" onProfile={() => navigate("profile")} /><main className="content">
    <button className="search-box" onClick={() => navigate("price-search")}><Icon name="search" /><span>Buscar medicamento</span></button>
    <div className="price-disclaimer"><Icon name="info" /><span>Consulte preços observados. O DoseCerta não vende medicamentos.</span></div>
    <SectionTitle title="Medicamentos acompanhados" />
    <div className="tracked-card"><div className="tracked-top"><div className="medicine-icon"><Icon name="pill" /></div><Badge tone="neutral">Acompanhando</Badge></div><strong>Losartana potássica 50 mg</strong><span>EMS • 30 comprimidos</span><div className="last-price"><span>Último preço observado</span><strong>R$ 18,90</strong><small>Dados fictícios de protótipo</small></div><Button variant="secondary" onClick={() => navigate("comparison")}>Consultar preços</Button><button className="history-link" onClick={() => navigate("price-history")}><Icon name="chart" size={18} />Ver histórico de preços</button></div>
    <div className="empty-mini"><div><Icon name="plus" /></div><strong>Acompanhe outro medicamento</strong><span>Escolha a apresentação exata para receber um histórico confiável.</span><Button variant="ghost" onClick={() => navigate("price-search")}>Buscar medicamento</Button></div>
  </main><BottomNav active="prices" navigate={navigate} /></div>;
}

function PriceSearch({ navigate }: { navigate: (s: Screen) => void }) {
  return <div className="screen"><Header title="Buscar medicamento" back={() => navigate("prices")} /><main className="content"><div className="search-box active"><Icon name="search" /><input autoFocus defaultValue="Losartana 50 mg" /><button><Icon name="close" size={18} /></button></div><div className="result-caption">3 apresentações encontradas</div><div className="result-list">
    <ProductResult maker="EMS" count="30 comprimidos" navigate={navigate} />
    <ProductResult maker="Medley" count="30 comprimidos" navigate={navigate} />
    <ProductResult maker="Neo Química" count="60 comprimidos" navigate={navigate} />
  </div><div className="support-note"><Icon name="info" /><span>Fabricantes e tamanhos de embalagem podem ter preços diferentes. Confirme a apresentação antes de continuar.</span></div></main></div>;
}
function ProductResult({ maker, count, navigate }: { maker: string; count: string; navigate: (s: Screen) => void }) { return <button className="product-result" onClick={() => navigate("comparison")}><div className="medicine-icon soft"><Icon name="pill" /></div><div><strong>Losartana potássica 50 mg</strong><span>{maker} • {count}</span><small>Comprimido revestido</small></div><Icon name="chevron" /></button> }

function Comparison({ navigate }: { navigate: (s: Screen) => void }) {
  return <div className="screen"><Header title="Comparar preços" back={() => navigate("price-search")} /><main className="content comparison-content">
    <div className="product-ident"><span>Apresentação selecionada</span><strong>Losartana potássica 50 mg</strong><small>EMS • 30 comprimidos</small></div>
    <div className="location-line"><Icon name="tag" size={17} />Busca localizada para <b>Campinas — SP</b></div>
    <div className="prototype-note">Valores fictícios para demonstração do protótipo</div>
    <div className="pharmacy-card best"><div className="best-label">Menor preço observado</div><div className="pharmacy-head"><div className="pharmacy-logo">D</div><div><strong>Drogasil</strong><span>Losartana potássica 50 mg — EMS — 30 comp.</span></div></div><div className="observed"><span>Preço observado</span><strong>R$ 18,90</strong></div><div className="source-row"><span>Observado hoje, 10:32</span><Badge tone="success">Verificado</Badge></div><Button variant="secondary">Consultar origem</Button></div>
    <div className="pharmacy-card"><div className="pharmacy-head"><div className="pharmacy-logo blue">SP</div><div><strong>Drogaria São Paulo</strong><span>Losartana potássica 50 mg — EMS — 30 comp.</span></div></div><div className="observed"><span>Preço observado</span><strong>R$ 21,49</strong></div><div className="source-row"><span>Observado hoje, 10:34</span><Badge tone="success">Verificado</Badge></div><Button variant="secondary">Consultar origem</Button></div>
    <div className="price-source"><Icon name="info" /><span>Fonte prevista: Google Search via SerpApi, condicionada à validação técnica. Preços podem mudar na origem.</span></div>
    <Button variant="ghost" onClick={() => navigate("price-history")} icon="chart">Ver histórico de preços</Button>
  </main></div>;
}

function PriceHistory({ navigate }: { navigate: (s: Screen) => void }) {
  return <div className="screen"><Header title="Histórico de preços" back={() => navigate("comparison")} /><main className="content">
    <div className="product-ident"><strong>Losartana potássica 50 mg</strong><small>EMS • 30 comprimidos</small></div><div className="filter-row"><button className="filter active">30 dias</button><button className="filter">90 dias</button><button className="filter">6 meses</button></div>
    <div className="line-chart-card"><div className="legend"><span><i className="red" />Drogasil</span><span><i className="blue" />Drogaria São Paulo</span></div><div className="line-chart"><div className="grid-lines"><i /><i /><i /><i /></div><svg viewBox="0 0 320 140" preserveAspectRatio="none"><polyline points="0,95 50,88 100,102 160,65 215,73 270,45 320,52" className="line red" /><polyline points="0,70 50,60 100,80 160,72 215,48 270,58 320,35" className="line blue" /></svg><div className="axis"><span>20 mai</span><span>3 jun</span><span>18 jun</span></div></div></div>
    <SectionTitle title="Observações anteriores" /><div className="observation-list"><Observation store="Drogasil" date="18 jun, 10:32" price="R$ 18,90" /><Observation store="Drogaria São Paulo" date="18 jun, 10:34" price="R$ 21,49" /><Observation store="Drogasil" date="10 jun, 09:18" price="R$ 19,65" /></div>
  </main></div>;
}
function Observation({ store, date, price }: { store: string; date: string; price: string }) { return <div className="observation"><div><strong>{store}</strong><span>{date} • Fonte: Google Search*</span></div><strong>{price}</strong></div> }

function Profile({ navigate, visualSize }: { navigate: (s: Screen) => void; visualSize: VisualSize }) {
  return <div className="screen"><Header title="Perfil e configurações" back={() => navigate("home")} /><main className="content profile-content"><div className="profile-card"><div className="profile-avatar">JF</div><div><strong>Jeferson Ferreira</strong><span>jeferson@email.com</span></div><button><Icon name="edit" /></button></div>
    <SettingsGroup title="Sua conta"><SettingsRow icon="user" label="Dados da conta" /><SettingsRow icon="shield" label="Privacidade e segurança" /></SettingsGroup>
    <SettingsGroup title="Preferências"><SettingsRow icon="bell" label="Notificações e lembretes" caption="Ativados" onClick={() => navigate("notification-settings")} /><SettingsRow icon="eye" label="Tamanho de fonte e ícones" caption={visualSizes[visualSize].label} onClick={() => navigate("visual-accessibility")} /><SettingsRow icon="clock" label="Margem de atraso" caption="30 minutos" /></SettingsGroup>
    <SettingsGroup title="Sobre"><SettingsRow icon="info" label="Sobre o DoseCerta" /><SettingsRow icon="shield" label="Termos e privacidade" /></SettingsGroup>
    <button className="logout-row"><Icon name="logout" /><span>Sair da conta</span></button><div className="version">DoseCerta • Versão 0.1 protótipo</div>
  </main></div>;
}
function SettingsGroup({ title, children }: { title: string; children: React.ReactNode }) { return <div className="settings-group"><div className="group-label">{title}</div><div className="settings-card">{children}</div></div> }
function SettingsRow({ icon, label, caption, onClick }: { icon: IconName; label: string; caption?: string; onClick?: () => void }) { return <button className="settings-row" onClick={onClick}><span className="settings-icon"><Icon name={icon} /></span><span>{label}</span>{caption && <small>{caption}</small>}<Icon name="chevron" size={18} /></button> }

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
      <strong>Losartana 50 mg</strong>
      <div className="preview-time"><Icon name="clock" /><span>Hoje às 08:00 • 1 comprimido</span></div>
      <div className="preview-button"><Icon name="check" size={19} />Registrar dose</div>
    </div>
    <div className="support-note"><Icon name="info" /><span>Você pode voltar ao tamanho padrão a qualquer momento nesta tela.</span></div>
  </main></div>;
}

function NotificationSettings({ navigate }: { navigate: (s: Screen) => void }) {
  const [enabled, setEnabled] = useState(true);
  return <div className="screen"><Header title="Notificações" back={() => navigate("profile")} /><main className="content"><div className={enabled ? "permission-card" : "permission-card disabled"}><span><Icon name={enabled ? "bell" : "info"} /></span><div><strong>{enabled ? "Notificações permitidas" : "Permissões desativadas no dispositivo"}</strong><p>{enabled ? "Seus lembretes podem ser enviados normalmente." : "Ative nas configurações do aparelho para receber lembretes."}</p></div></div>
    <div className="settings-group"><div className="group-label">Lembretes de medicamentos</div><div className="settings-card"><label className="toggle-row"><div><strong>Receber lembretes</strong><span>Notificações nos horários programados</span></div><input type="checkbox" checked={enabled} onChange={() => setEnabled(!enabled)} /><i /></label><label className="toggle-row"><div><strong>Lembrete persistente</strong><span>Repetir enquanto a dose estiver pendente</span></div><input type="checkbox" defaultChecked /><i /></label></div></div>
    {!enabled && <div className="support-note"><Icon name="info" /><span>Sua agenda continua disponível e seus registros de doses não serão alterados.</span></div>}
  </main></div>;
}

type AppNotification = {
  id: string;
  icon: IconName;
  tone: "brand" | "warning" | "neutral";
  title: string;
  description: string;
  time: string;
  doseRelated?: boolean;
};

const appNotifications: AppNotification[] = [
  {
    id: "losartana-reminder",
    icon: "bell",
    tone: "brand",
    title: "Hora da Losartana",
    description: "Sua dose de Losartana 50 mg está programada para agora.",
    time: "08:00",
    doseRelated: true,
  },
  {
    id: "metformina-late",
    icon: "clock",
    tone: "warning",
    title: "Dose aguardando registro",
    description: "A dose de Metformina das 07:30 ainda está sem registro.",
    time: "07:45",
  },
  {
    id: "treatment-active",
    icon: "pill",
    tone: "neutral",
    title: "Tratamento ativado",
    description: "Controle da pressão foi adicionado à sua agenda diária.",
    time: "Ontem, 18:20",
  },
  {
    id: "schedule-updated",
    icon: "calendar",
    tone: "neutral",
    title: "Cronograma atualizado",
    description: "O novo horário será aplicado somente às doses futuras.",
    time: "Ontem, 14:10",
  },
];

function NotificationCenter({ navigate, readIds, markRead, markAllRead }: { navigate: (s: Screen) => void; readIds: string[]; markRead: (id: string) => void; markAllRead: () => void }) {
  const unreadCount = appNotifications.filter((item) => !readIds.includes(item.id)).length;
  const openNotification = (notification: AppNotification) => {
    markRead(notification.id);
    if (notification.doseRelated) navigate("home");
  };

  return <div className="screen notification-screen">
    <Header title="Notificações" back={() => navigate("home")} />
    <main className="content notification-content">
      {appNotifications.length ? <>
        <div className="notification-toolbar">
          <span>{unreadCount ? `${unreadCount} não lida${unreadCount > 1 ? "s" : ""}` : "Tudo em dia"}</span>
          <button onClick={markAllRead} disabled={!unreadCount}><Icon name="check" size={16} />Marcar todas como lidas</button>
        </div>
        <div className="notification-list">
          {appNotifications.map((notification) => {
            const isRead = readIds.includes(notification.id);
            return <div className={`notification-card ${isRead ? "read" : "unread"}`} key={notification.id} role="button" tabIndex={0} onClick={() => openNotification(notification)} onKeyDown={(event) => { if (event.key === "Enter") openNotification(notification); }}>
              <span className={`notification-icon ${notification.tone}`}><Icon name={notification.icon} /></span>
              <span className="notification-copy">
                <span className="notification-title-row"><strong>{notification.title}</strong><small>{notification.time}</small></span>
                <span>{notification.description}</span>
                {notification.doseRelated && <small className="notification-link">Ver dose na agenda <Icon name="chevron" size={14} /></small>}
              </span>
              {!isRead && <span className="unread-marker" aria-label="Não lida" />}
              {!isRead && <button className="read-action" onClick={(event) => { event.stopPropagation(); markRead(notification.id); }} aria-label={`Marcar ${notification.title} como lida`}><Icon name="check" size={15} /></button>}
            </div>;
          })}
        </div>
      </> : <div className="notification-empty"><div><Icon name="bell" size={30} /></div><strong>Nenhuma notificação por aqui</strong><span>Quando houver um lembrete ou aviso importante, ele aparecerá nesta tela.</span><Button variant="secondary" onClick={() => navigate("home")}>Voltar para o início</Button></div>}
    </main>
  </div>;
}

export default function App() {
  const [screen, setScreen] = useState<Screen>("welcome");
  const [doseModal, setDoseModal] = useState(false);
  const [doseTaken, setDoseTaken] = useState(false);
  const [toast, setToast] = useState("");
  const [readNotificationIds, setReadNotificationIds] = useState<string[]>(["schedule-updated"]);
  const [visualSize, setVisualSize] = useState<VisualSize>("standard");
  const navigate = (s: Screen) => { setScreen(s); window.scrollTo(0, 0); };
  const confirmDose = () => { setDoseModal(false); setDoseTaken(true); setToast("Dose registrada com sucesso"); setTimeout(() => setToast(""), 2600); };
  const markNotificationRead = (id: string) => setReadNotificationIds((current) => current.includes(id) ? current : [...current, id]);
  const unreadNotificationCount = appNotifications.filter((item) => !readNotificationIds.includes(item.id)).length;

  const scale = visualSizes[visualSize];
  return <div className="app-shell"><div className="phone" style={{ "--font-scale": scale.fontScale, "--icon-scale": scale.iconScale } as React.CSSProperties}>
    {screen === "welcome" && <Welcome navigate={navigate} />}
    {screen === "login" && <Login navigate={navigate} />}
    {screen === "home" && <Home navigate={navigate} doseTaken={doseTaken} openDose={() => setDoseModal(true)} unreadCount={unreadNotificationCount} />}
    {screen === "treatments" && <Treatments navigate={navigate} />}
    {screen === "new-treatment" && <NewTreatment navigate={navigate} />}
    {screen === "treatment-detail" && <TreatmentDetail navigate={navigate} />}
    {screen === "edit-schedule" && <EditSchedule navigate={navigate} />}
    {screen === "history" && <History navigate={navigate} />}
    {screen === "prices" && <Prices navigate={navigate} />}
    {screen === "price-search" && <PriceSearch navigate={navigate} />}
    {screen === "comparison" && <Comparison navigate={navigate} />}
    {screen === "price-history" && <PriceHistory navigate={navigate} />}
    {screen === "profile" && <Profile navigate={navigate} visualSize={visualSize} />}
    {screen === "visual-accessibility" && <VisualAccessibility navigate={navigate} selected={visualSize} onSelect={setVisualSize} />}
    {screen === "notifications" && <NotificationCenter navigate={navigate} readIds={readNotificationIds} markRead={markNotificationRead} markAllRead={() => setReadNotificationIds(appNotifications.map((item) => item.id))} />}
    {screen === "notification-settings" && <NotificationSettings navigate={navigate} />}
    {doseModal && <DoseModal close={() => setDoseModal(false)} confirm={confirmDose} />}
    {toast && <div className="toast"><Icon name="check" /><span>{toast}</span></div>}
  </div></div>;
}
