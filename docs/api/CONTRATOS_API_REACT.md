# DoseCerta — Contratos HTTP para integração React

**Base auditada:** [`develop` / commit `dfe4117ef5239359ef1c3f9d084746ca47067785`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/tree/dfe4117ef5239359ef1c3f9d084746ca47067785)  
**Data da revisão:** 22/11/2026  
**Artefato principal:** [`openapi.yaml`](./openapi.yaml) — OpenAPI 3.1, **17 caminhos e 21 operações**, 36 schemas.  
**Estado:** fotografia **as-is** do backend nesta revisão, verificada por leitura dos handlers/serviços. **Não houve execução local do servidor, testes de contrato nem verificação por HTTP.** Não afirmar que esta versão já foi validada conjuntamente pelo frontend. Alterações posteriores à SHA exigem nova revisão.

## 1. Objetivo e metodologia

O documento OpenAPI descreve o contrato **efetivamente implementado** no Java, mantendo as inconsistências existentes para não induzir o frontend a consumir campos que ainda não são retornados. Foram conferidos `Main`, `Router`, `RouteAccessPolicy`, handlers, DTOs, enumerações, serviços de medicamentos/cronogramas, documentação de issues #27 e #35, e a modelagem física MongoDB da issue #14 fornecida no chat.

A modelagem anexada é uma **proposta de banco**, não a verdade atual da API. Exemplos: nela `treatments.active` é um booleano, mas o backend retorna `status: DRAFT | ACTIVE | CLOSED`; nela `medications[].quantity` é proposto como Decimal128, mas no Java e no HTTP atual a quantidade por administração é **inteiro positivo ou null**. Já `DosePrevista.plannedQuantity` é decimal e é serializado como número JSON. Os cronogramas modernos têm `frequency`, `validFrom`, `validTo` e snapshots além dos campos da modelagem inicial.

## 2. Inventário da API

| Grupo | Rotas existentes | Observações |
| --- | --- | --- |
| Infraestrutura | `GET /health` | Pública; `{"status":"ok"}`; não verifica prontidão do MongoDB. |
| Autenticação | `POST /auth/register`, `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` | Registro/login públicos; restantes com Bearer. Login pode retornar 429. |
| Catálogo | `GET /medications` | **Demonstração em memória**, não CMED/SerpApi/preços. Exige Bearer no Router. |
| Tratamentos | `GET/POST /treatments`, `GET/PUT /treatments/{id}`, `POST /treatments/{id}/activate`, `POST /treatments/{id}/close` | Tratamento inclui medicamentos e cronogramas em representação **resumida**. |
| Medicamentos | `POST /treatments/{id}/medications`, `PUT/DELETE /treatments/{id}/medications/{medicationId}` | POST 201, PUT 200, DELETE 204; versão otimista (`revision`) no PUT. |
| Cronogramas | `GET/PUT /treatments/{id}/medications/{medicationId}/schedule` | Resposta completa `current`/`versions`; `revision` no PUT. |
| Agenda | `GET /doses?date=YYYY-MM-DD`, `PUT /doses/{id}/record` | `GET` não gera doses; registro aceita somente TAKEN ou SKIPPED. |
| Adesão/histórico | `GET /adherence/summary`, `GET /adherence/history` | Query `from`, `to`, filtros opcionais. Histórico tem limite de **90 dias inclusivos**. |

**Total:** 17 caminhos, 21 operações. Não existem endpoints HTTP de criação manual de doses previstas; o gerador é interno.

## 3. Autenticação, datas, revisão e erros

**Autenticação:** `POST /auth/login` retorna `token` opaco. Os endpoints protegidos requerem `Authorization: Bearer <token>`. O `userId` é resolvido pela sessão, não pelo corpo da requisição. `GET /health`, `POST /auth/register` e `POST /auth/login` são públicos. Todo o restante exige autenticação, inclusive `GET /medications`.

**Datas:** `startDate`, `endDate`, `date`, `from` e `to` representam **datas civis** no formato `yyyy-MM-dd`. Para agenda, indicadores e histórico o fuso de referência atual é `America/Sao_Paulo`. `scheduledAt`, `recordedAt`, `actualTime`, `closedAt`, `validFrom` e `validTo` representam **instantes** no formato ISO 8601 (`Z` recomendado). O React deve converter instantes para exibição, mas preservar a data civil ao montar o query parameter `date`.

**Vigências:** `CronogramaDose` distingue os intervalos `[validFrom, validTo)` (início inclusivo, fim exclusivo); valores `null` aparecem em versões legadas/abertas. No `PUT` de cronograma, a API recebe `validFrom` e o backend define `version`, `active`, `validTo` e snapshots. Para tratamento ativo, a mudança deve ser prospectiva. Para atualização versionada da posologia, `validFrom` é necessário, deve ser futuro e posterior à versão existente.

**Revisão otimista:** a revisão do medicamento é um inteiro longo não negativo. O React deve ler `revision` no retorno mais recente e reenviá-lo em `PUT` de medicamento e cronograma. Falha por revisão obsoleta resulta em 409, e a UI deve recarregar o recurso antes de tentar uma nova alteração; **não incrementar a revisão manualmente sem a resposta do servidor**. No cadastro inicial, o medicamento é retornado com `revision: 0`.

**Erros:** o formato observado é `{"error":"mensagem"}`. Os códigos variam por handler: 400 (dados inválidos), 401 (sessão), 404 (não encontrado/outro titular), 405 (método), 409 (conflito), 429 (somente login) e 500 (falha interna). **Não vincular lógica de interface às mensagens em português/inglês**, que são diferentes entre handlers; usar status HTTP e, futuramente, um `code` estável no JSON.

**HTTP/CORS:** há suporte a uma requisição por conexão e `Connection: close`; o backend não implementa CORS/preflight `OPTIONS` nesta revisão. Para desenvolvimento React/Vite, configurar proxy `/api` para a porta Java (e remover `/api` no encaminhamento), ou implementar CORS controlado se as origens forem realmente distintas.

## 4. Divergências reais que impedem um contrato unificado

| Severidade | Divergência | Arquivos atuais | Proposta de decisão |
| --- | --- | --- | --- |
| **P1** | O mesmo medicamento aparece como `dose/quantity/observations` no GET do tratamento e como `informedDose/quantityPerAdministration/notes` no POST/PUT de medicamentos. | [`TreatmentHandler.java`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/handlers/TreatmentHandler.java) / [`MedicationInUseHandler.java`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/handlers/MedicationInUseHandler.java) | Definir mapeador/DTO HTTP compartilhado ou adaptação explícita em `src/api`, nunca supor que os nomes já estão iguais. |
| **P1** | `GET /treatments/{id}` retorna schedules **sem** `frequency`, `validFrom`, `validTo`, `quantitySnapshot`, `unitSnapshot`. | `TreatmentHandler.toDocument()` / [`ScheduleHandler.java`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/handlers/ScheduleHandler.java) | Ou incluir campos no GET de tratamento, ou documentar oficialmente GET `.../schedule` como única fonte completa. |
| **P1** | Preflight CORS sem suporte. | [`Router.java`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/router/Router.java), [`Main.java`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/Main.java) | Vite proxy para começar; em produção usar mesma origem ou CORS por allowlist. |
| **P1** | Quantidade de medicamento **só inteira**, mas modelagem #14 propõe valores fracionários. | [`MedicationInUseHandler.quantity()`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/handlers/MedicationInUseHandler.java) | Confirmar exigência de UI/negócio antes de disponibilizar controle que aceite 0,5. |
| **P2** | Unidade histórica ausente na ocorrência; `/adherence/history` retorna `plannedUnit: null`. | [`DoseHistoryService.java`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/services/DoseHistoryService.java) | Não mostrar unidade histórica inventada; considerar snapshot em `DosePrevista` se UI exigir. |
| **P2** | Catálogo de medicamentos é in-memory com 2 exemplos. | [`InMemoryMedicationRepository.java`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/repositories/InMemoryMedicationRepository.java) | Separar catálogo/preços como módulo futuro, não vender como pronto. |
| **P2** | Mensagens de erro distintas, sem código de erro estável. | Diversos handlers | Padronizar erros em etapa futura sem quebrar status HTTP. |
| **P2** | Concorrência no registro de dose: verificar + gravar não usa o mesmo monitor da reconciliação. | [`DoseService.java`](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/services/DoseService.java), `DoseAccessService.java` | Completar exclusão mútua em uma JVM e avaliar garantias multi-instância. |

O arquivo OpenAPI é deliberadamente **fiel ao estado atual**, inclusive nos dois formatos de medicamento. Se a equipe decidir padronizar os handlers, o contrato precisará ser atualizado *junto com a alteração*, sem “corrigir” apenas o YAML.

## 5. Exemplo de fluxo real para React

1. Login com email/password; armazenar token de sessão conforme política de segurança escolhida.
2. `POST /treatments` (DRAFT); guardar ID do tratamento retornado.
3. `POST /treatments/{id}/medications`; guardar ID e `revision: 0`.
4. `PUT /treatments/{id}/medications/{medicationId}/schedule`, com frequência, horários, `validFrom` e revisão corrente.
5. `POST /treatments/{id}/activate` (sem corpo); o serviço gera doses internamente.
6. `GET /doses?date=yyyy-MM-dd`; montar lista de ocorrências.
7. `PUT /doses/{id}/record`, `{ "action": "TAKEN" }` ou `{ "action": "SKIPPED" }`.
8. `GET /adherence/summary?from=...&to=...`; `GET /adherence/history?from=...&to=...`.

**Vite:** configurar proxy de desenvolvimento para que `/api/treatments` no navegador seja encaminhado como `/treatments` ao Java; o contrato OpenAPI usa as rotas **sem** prefixo `/api` porque é assim que o backend está registrado.

```ts
// Exemplo Vite para backend Java em http://localhost:8080
import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, ''),
      },
    },
  },
});
```

**Tipos TypeScript:** depois de revisar o OpenAPI com a equipe, por exemplo `npx openapi-typescript ./docs/api/openapi.yaml -o ./frontend/src/types/api.ts` (requer instalar/baixar a ferramenta em um ambiente com Node). Tipos compilados não substituem testes de contrato.

## 6. Checklist para congelamento definitivo

- [x] Inventário de 17 paths/21 operações a partir de `Main.java`.
- [x] Schemas das respostas conforme serializers Java atuais, inclusive formatos diferentes de medicamento.
- [x] Regras de `revision`, `validFrom`, datas locais, instantes e enums documentadas.
- [x] Códigos HTTP usados e segurança Bearer descritos.
- [x] Modelagem física proposta confrontada com o código.
- [x] OpenAPI parseado e referências locais conferidas.
- [ ] Revisão de API feita pela equipe do frontend e pelos responsáveis por cada módulo.
- [ ] Decidir padronização dos nomes e correção dos campos ausentes em `TreatmentHandler`.
- [ ] Confirmar quantitativos fracionários, unidade histórica, experiência de erros e catálogo.
- [ ] Executar validação formal OpenAPI e testes HTTP de contrato contra servidor real, com banco de teste.
- [ ] Revalidar SHA após alterações da `develop` e marcar versão contratual aprovada pela equipe.

### Fontes e rastreabilidade

- [`Main.java` nesta revisão](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/backend/src/main/java/backend/Main.java)
- [Issue #27 — contratos de medicamentos](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/docs/issue_27_endpoints.md)
- [Issue #35 — histórico](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/blob/dfe4117ef5239359ef1c3f9d084746ca47067785/docs/issue_35_contrato.md)
- Modelagem física MongoDB fornecida pelo usuário (issue #14, documento **propositivo**).
