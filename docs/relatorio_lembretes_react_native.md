# Relatório de Implementação: Lembretes de Medicamentos (React Native)

**Projeto:** DoseCerta (`ES-PI4-2026-T1-G30`)  
**Branch:** `37-frontend-implementar-lembretes-de-medicamentos`  
**Escopo:** Issue `#37 — [FRONTEND] Implementar lembretes de medicamentos`  
**Plataforma / Tecnologia:** React Native (TypeScript) com notificações locais (`expo-notifications` + `@react-native-async-storage/async-storage`)

---

## 1. Objetivo

Implementar o fluxo completo de lembretes de medicamentos no frontend **React Native** do **DoseCerta**, utilizando as doses previstas fornecidas pelo backend (`GET /doses?date=AAAA-MM-DD`) como única fonte de verdade para horários, identidades (`plannedDoseId`) e fusos horários.

O fluxo permite:
- Habilitar/desabilitar lembretes com persistência da preferência por usuário;
- Solicitar permissões de forma contextual e tratar todos os estados (`granted`, `undetermined`, `denied`, `blocked`, `unavailable`);
- Agendar notificações locais idempotentes dentro da janela suportada e do limite do sistema operacional;
- Cancelar e reagendar lembretes futuros automaticamente diante de alterações de cronograma, encerramento de tratamento, desabilitação da preferência ou registro explícito de dose;
- Garantir privacidade na tela bloqueada por padrão e zero efeito colateral automático em `RegistroDeDose`.

---

## 2. Regras de Negócio Aplicadas

1. **Identidade estável (`DosePrevista`):** Todo lembrete é indexado e reconciliado pelo `plannedDoseId` retornado pelo backend (`GET /doses?date=AAAA-MM-DD`), associado localmente ao identificador de notificação do SO (`ReminderRegistry`).
2. **Backend como fonte de verdade temporal:** O agendamento utiliza exatamente o instante ISO-8601 UTC (`scheduledAt`) e o horário/fuso (`scheduledLocalTime`, `timezone`) retornados pela API. O app React Native **não define posologia** e **não cria horários fictícios**.
3. **Sem efeito colateral no estado da dose:** Receber, visualizar ou tocar em uma notificação **nunca** cria ou modifica `RegistroDeDose` (`PUT /doses/{id}/record`). O toque apenas abre a agenda destacando a dose para que o usuário acione explicitamente **"Tomei"** (`TAKEN`) ou **"Não tomei"** (`SKIPPED`).
4. **Degradação graciosa:** Permissão negada/bloqueada, módulo indisponível ou falha de agendamento nunca bloqueiam a consulta da agenda nem o registro manual de doses.
5. **Preservação de histórico:** Alterações de cronograma ou encerramento de tratamento cancelam apenas lembretes futuros afetados, sem modificar registros históricos.
6. **Privacidade na tela bloqueada:** Por padrão (`hideSensitiveDetailsOnLockScreen = true`), o texto da notificação omite nome do medicamento e dados clínicos, exibindo apenas o horário previsto e orientando o usuário a abrir o app; no Android, o canal é configurado com `lockscreenVisibility = PRIVATE`.

---

## 3. Arquitetura e Arquivos Criados

```text
mobile/
├── package.json                                  # Dependências React Native (expo-notifications, AsyncStorage) e script de teste
├── tsconfig.json                                 # Configuração TypeScript strict para React Native
├── scripts/
│   └── ts-resolve-hook.mjs                       # Resolvedor de imports TS para execução direta dos testes em Node 24
├── src/
│   ├── index.ts                                  # Exportações públicas do módulo de lembretes
│   ├── types/
│   │   ├── api.ts                                # Contratos HTTP alinhados ao backend (PlannedDose, DailyAgendaResponse, DoseAction)
│   │   └── reminders.ts                          # Tipos de permissões, preferências, registro local, reconciliação e navegação
│   ├── api/
│   │   └── doseCertaApi.ts                       # Cliente HTTP para GET /doses?date=AAAA-MM-DD e PUT /doses/{id}/record
│   ├── notifications/
│   │   ├── privacyFormatter.ts                   # Formatação de título/corpo compatível com privacidade na tela bloqueada
│   │   ├── storage.ts                            # Adaptador @react-native-async-storage/async-storage + InMemoryKeyValueStorage
│   │   ├── preferencesStore.ts                   # Persistência das preferências de lembretes por userId
│   │   ├── reminderRegistry.ts                   # Associação persistida plannedDoseId <-> platformNotificationId
│   │   ├── expoNotificationDriver.ts             # Adaptador React Native sobre expo-notifications (canal Android, permissões iOS/Android, triggers DATE)
│   │   ├── notificationService.ts                # Operações: requestPermission, getPermissionStatus, scheduleReminder, cancelReminder, reconcileReminders, cancelRemindersByTreatment, handleDoseRecorded
│   │   └── interactionHandler.ts                 # Tratamento de toque na notificação (app aberto, minimizado e fechado/cold-start) sem registro implícito
│   ├── hooks/
│   │   └── useMedicationReminders.ts             # Hook React Native integrando AppState, preferências, permissões e reconciliação
│   └── screens/
│       ├── ReminderSettingsScreen.tsx            # Tela de configurações com explicação contextual, estados de permissão, Linking.openSettings() e aviso de limitações
│       └── AgendaScreen.tsx                      # Tela de agenda integrada à reconciliação idempotente e ações explícitas "Tomei" / "Não tomei"
└── __tests__/
    └── reminders.test.ts                         # Suíte automatizada cobrindo todos os cenários da Issue #37
```

---

## 4. Fluxo de Reconciliação e Prevenção de Duplicidade

- **Serialização:** Todas as operações de agendamento, cancelamento e reconciliação passam por uma fila sequencial interna (`operationQueue`), prevenindo *race conditions* em renderizações repetidas ou navegações rápidas.
- **Idempotência:** Se `plannedDoseId` já existe no `ReminderRegistry` com o mesmo `scheduledAt`, `scheduledLocalTime`, `timezone`, `scheduleVersion` e configuração de privacidade — e segue presente no SO (`getAllScheduledNotificationIds`) — nenhuma nova notificação é criada (`unchangedCount`).
- **Janela e limite da plataforma:** O agendamento considera apenas ocorrências futuras dentro de uma janela configurável (padrão **7 dias**) e aplica ordenação cronológica com teto de **50 notificações pendentes** (margem segura abaixo do limite de 64 notificações locais pendentes do iOS).
- **Eventos de ciclo de vida:**
  - *Edição de cronograma:* `reconcileReminders` / `handleScheduleUpdated` cancela lembretes futuros obsoletos/cancelados e programa os substitutos.
  - *Encerramento de tratamento:* `cancelRemindersByTreatment(treatmentId)` remove todos os agendamentos futuros do tratamento encerrado.
  - *Desabilitação da preferência:* `disableReminders()` grava `enabled = false` e cancela todos os lembretes administrados pelo app.
  - *Reativação da preferência:* `enableReminders()` revalida permissão e reconcilia as próximas doses elegíveis.
  - *Dose registrada (`TAKEN`/`SKIPPED`):* `handleDoseRecorded(plannedDoseId)` cancela eventual lembrete pendente dessa dose sem alterar histórico.

---

## 5. Limitações Conhecidas da Plataforma (Documentadas e Sinalizadas na UI)

Conforme exigido pela Issue #37, as limitações abaixo **não** são omitidas nem apresentadas como garantidas ao usuário (estão sinalizadas na `ReminderSettingsScreen`):

1. **Economia de bateria / Doze (Android) e Modo Pouca Energia (iOS):** Restrições do sistema operacional ou otimizações agressivas de fabricantes podem atrasar ou suprimir a entrega no minuto exato de notificações locais em segundo plano.
2. **Limite de notificações pendentes no SO:** O iOS limita aplicativos a **64 notificações locais pendentes** simultaneamente. Por isso o app utiliza janela móvel de **7 dias** e teto conservador de **50 agendamentos**, exigindo abertura periódica do app para renovação da janela.
3. **Permissão bloqueada permanentemente (`blocked`):** Após recusa definitiva, o SO não exibe novo diálogo nativo; a interface informa claramente o estado e oferece atalho direto via `Linking.openSettings()`.
4. **Dependência de conectividade para refletir alterações remotas:** Caso o cronograma seja alterado em outro dispositivo enquanto o aparelho está offline, o reagendamento local ocorrerá na próxima sincronização com `GET /doses?date=AAAA-MM-DD`.
5. **Fora do escopo do MVP:** Push remoto (FCM/APNs), SMS, e-mail, cálculo de adesão por abertura de notificação e alterações no gerador de doses do backend não fazem parte desta entrega.

---

## 6. Validação e Testes Reproduzíveis

A suíte [`mobile/__tests__/reminders.test.ts`](../mobile/__tests__/reminders.test.ts) foi executada com **Node.js v24** (`npm test` na pasta `mobile/`), com **12 testes passando e 0 falhas**:

| # | Cenário Validado | Resultado |
| :---: | :--- | :---: |
| **1** | Lembrete de uma dose futura com horário/fuso do backend e privacidade na tela bloqueada | **PASS** |
| **2** | Dois medicamentos em horários diferentes com identidades distintas (`plannedDoseId`) | **PASS** |
| **3** | Preferência desligada (não agenda e cancela pendentes) e reativação posterior | **PASS** |
| **4** | Permissão `denied`, `blocked` e `unavailable` (não marca como ativo; agenda e registro manual seguem operando) | **PASS** |
| **5** | Consultas repetidas e concorrentes da agenda sem duplicatas (idempotência) | **PASS** |
| **6** | Alteração de horário e reagendamento (substitui apenas lembretes futuros sem alterar histórico) | **PASS** |
| **7** | Encerramento de tratamento cancela somente lembretes futuros daquele tratamento | **PASS** |
| **8–9** | Entrega e toque na notificação (app minimizado e fechado/cold-start) abrem a dose sem criar `RegistroDeDose`; registro explícito cancela lembrete pendente | **PASS** |
| **10** | Janela de 7 dias, limite de agendamentos pendentes da plataforma e isolamento por `userId` | **PASS** |
| **11** | Integração HTTP `GET /doses?date=AAAA-MM-DD` (`DoseCertaApi` + `syncWithAgendaWindow`) e recuperação após falha no SO | **PASS** |
| **12** | Mapeamento de estados no `ExpoNotificationDriver` (`granted`, `denied`, `blocked`, `unavailable`) | **PASS** |
| **13** | Alternância da opção de privacidade na tela bloqueada com reagendamento sem duplicatas | **PASS** |

### Como executar os testes

```powershell
cd mobile
npm.cmd test
```

---

## 7. Integração ao Frontend React Web (`frontend/`)

Para atender à integração direta com a interface web existente sem remover o módulo React Native (`mobile/`), o motor de lembretes foi integrado de ponta a ponta ao app React + Vite em `frontend/`:

- **Serviço e driver Web ([`frontend/src/notifications/webReminders.ts`](../frontend/src/notifications/webReminders.ts)):** implementação do adaptador baseado na Browser Notifications API (`window.Notification`) + timers locais + persistência por usuário em `localStorage`, mantendo as mesmas garantias de privacidade por padrão, janela de 7 dias, teto de 50 lembretes, idempotência e ausência de registro automático.
- **Hook de ciclo de vida ([`frontend/src/notifications/useWebReminders.ts`](../frontend/src/notifications/useWebReminders.ts)):** inicializado em [`App.tsx`](../frontend/src/App.tsx) para o usuário autenticado, revalida permissão ao retornar para a aba (`visibilitychange`/`focus`), limpa timers no logout e direciona cliques em notificações para a dose correspondente na agenda.
- **Tela dedicada ([`frontend/src/Notifications.tsx`](../frontend/src/Notifications.tsx)):** substituiu a tela de indisponibilidade anterior (`<Unavailable />`) oferecendo status real da permissão, modal explicativo contextual antes de `Notification.requestPermission()`, toggle de ativação e privacidade, lista de lembretes agendados e aviso explícito das limitações do navegador (entrega apenas com aba/navegador aberto, sem Web Push remoto no MVP).
- **Pontos integrados nas telas existentes:**
  - [`Profile`](../frontend/src/App.tsx): exibe o status dinâmico em *Notificações e lembretes* (`Ativos (N)`, `Desativados`, `Bloqueadas`, `Indisponíveis`);
  - [`Agenda.tsx`](../frontend/src/Agenda.tsx): reconcilia lembretes ao carregar `GET /doses?date=...`, abre o bottom-sheet da dose ao clicar na notificação (sem registrar automaticamente) e cancela o lembrete pendente ao registrar explicitamente **Tomei agora** ou **Não tomei**;
  - [`Treatments.tsx`](../frontend/src/Treatments.tsx) e [`Schedule.tsx`](../frontend/src/Schedule.tsx): reconciliam lembretes ao ativar tratamento, editar cronograma ou desativar medicamento, e cancelam todos os lembretes futuros ao encerrar o tratamento (`POST /treatments/{id}/close`).
- **Testes automatizados Web ([`frontend/__tests__/webReminders.test.ts`](../frontend/__tests__/webReminders.test.ts)):** executáveis via `npm.cmd test` em `frontend/` (4 cenários cobrindo privacidade, idempotência, preferência desligada/permissão bloqueada, edição de cronograma, encerramento de tratamento, registro explícito e clique na notificação).

---

## 8. Checklist (Definition of Done)

- [x] Mecanismo de notificações locais implementado para React Native (`expo-notifications` + `AsyncStorage`).
- [x] Preferência do usuário respeitada e persistida.
- [x] Permissões concedidas, negadas, bloqueadas e indisponibilidade tratadas corretamente.
- [x] Lembretes utilizam dados reais de `DosePrevista` (`GET /doses?date=AAAA-MM-DD`), sem criar horários fictícios.
- [x] O mesmo lembrete não é agendado em duplicidade por consultas repetidas ou concorrentes.
- [x] Alterações de cronograma reconciliam apenas os lembretes futuros afetados sem modificar histórico.
- [x] Encerramento de tratamento cancela os lembretes futuros pertinentes.
- [x] Desabilitar lembretes impede novos agendamentos e limpa os pendentes sob controle do aplicativo.
- [x] Receber/abrir notificação não registra automaticamente dose tomada ou ignorada.
- [x] Falhas e permissões negadas não impedem agenda, tratamentos nem registros manuais.
- [x] Cenários suportados testados de forma reproduzível (incluindo app minimizado e fechado/cold-start).
- [x] Limitações da plataforma documentadas e comunicadas com transparência na interface.

