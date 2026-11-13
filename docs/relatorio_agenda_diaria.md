# Relatório de Implementação: API de Consulta da Agenda Diária (`GET /doses`)

**Projeto:** DoseCerta (`ES-PI4-2026-T1-G30`)  
**Branch:** `31-backend-api-de-consulta-da-agenda-diária`  
**Escopo:** Issue `#31 - [DOSE] API de consulta da agenda diária`

---

## 1. Objetivo

Implementar o endpoint `GET /doses?date=AAAA-MM-DD` no servidor HTTP em **Java puro**, permitindo consultar as doses previstas para uma data civil específica de forma:
- Ordenada cronologicamente (`scheduledAt` crescente, com desempate estável por `id`);
- Restrita exclusivamente ao usuário titular proveniente da sessão autenticada;
- Combinada em lote com os registros explícitos de realização (`TAKEN` / `SKIPPED`), sem gerar efeitos colaterais no banco ou na agenda;
- Capaz de derivar o estado de apresentação (`DisplayStatus`) sem confundir ausência de registro com `SKIPPED` e omitindo ocorrências canceladas (`CANCELLED`).

---

## 2. Contrato HTTP e JSON (`GET /doses?date=AAAA-MM-DD`)

### Requisição
- **Método e Caminho:** `GET /doses?date=AAAA-MM-DD`
- **Autenticação:** Header `Authorization: Bearer <token>`
- **Parâmetro obrigatório:** `date` no formato ISO `AAAA-MM-DD` (ex.: `2026-11-05`). Parâmetros extras ou não suportados (como tentativa de enviar `userId` arbitrário) são rejeitados com `400 Bad Request`.

### Exemplos de Resposta

#### `200 OK` (Agenda com doses previstas e registros)
```json
{
  "date": "2026-11-05",
  "timezone": "America/Sao_Paulo",
  "doses": [
    {
      "id": "dose-001",
      "treatmentId": "treat-001",
      "medicationInUseId": "med-use-001",
      "medicationName": "Losartana 50 mg",
      "plannedQuantity": 1,
      "scheduledAt": "2026-11-05T11:00:00Z",
      "scheduledLocalTime": "08:00",
      "scheduleStatus": "SCHEDULED",
      "record": {
        "id": "rec-001",
        "action": "TAKEN",
        "actualTime": "2026-11-05T11:05:00Z",
        "recordedAt": "2026-11-05T11:05:10Z"
      },
      "displayStatus": "TAKEN"
    },
    {
      "id": "dose-002",
      "treatmentId": "treat-001",
      "medicationInUseId": "med-use-002",
      "medicationName": "Sinvastatina 20 mg",
      "plannedQuantity": 1,
      "scheduledAt": "2026-11-05T23:00:00Z",
      "scheduledLocalTime": "20:00",
      "scheduleStatus": "SCHEDULED",
      "record": null,
      "displayStatus": "UPCOMING"
    }
  ]
}
```

#### `200 OK` (Agenda vazia)
```json
{
  "date": "2026-11-06",
  "timezone": "America/Sao_Paulo",
  "doses": []
}
```

#### `400 Bad Request` (Data ausente, formato inválido ou parâmetro não suportado)
```json
{
  "error": "Invalid or missing 'date' query parameter (expected format: YYYY-MM-DD)"
}
```

#### `401 Unauthorized` (Sessão ausente, inválida ou expirada)
```json
{
  "error": "Invalid, expired, or missing session"
}
```

#### `500 Internal Server Error` (Falha inesperada sem expor detalhes internos)
```json
{
  "error": "Internal server error"
}
```

---

## 3. Arquitetura e Separação de Responsabilidades

O fluxo completo segue o desacoplamento entre protocolo HTTP, regras de domínio e persistência:

```text
Cliente HTTP
    │
    ▼
HttpRequestParser (preserva path e rawQuery)
    │
    ▼
Router ("GET" + "/doses")
    │
    ▼
AgendaHandler ──► SessionAuthenticator (extrai userId da sessão)
    │
    ▼
AgendaService (regras de fuso/UTC, ordenação, filtragem e DisplayStatus)
    ├──► PlannedDoseRepository (MongoPlannedDoseRepository)
    └──► DoseRecordRepository  (MongoDoseRecordRepository)
```

### Componentes Implementados

1. **Camada HTTP e Autenticação**
   - `HttpRequest` (`backend/src/main/java/backend/http/HttpRequest.java`): Armazena `rawQuery` separada de `path` e oferece `getHeader(String name)` case-insensitive.
   - `HttpRequestParser` (`backend/src/main/java/backend/http/HttpRequestParser.java`): Repassa a request target completa (`parts[1]`) para `HttpRequest`, preservando a query string sem afetar o roteamento por `path`.
   - `HttpResponse` (`backend/src/main/java/backend/http/HttpResponse.java`): Adicionado método auxiliar `unauthorized(String message)` (`401 Unauthorized`).
   - `SessionAuthenticator` e `InMemorySessionAuthenticator` (`backend/src/main/java/backend/auth/`): Validam o header `Authorization: Bearer <token>`, verificam expiração e devolvem o `userId` do titular autenticado.
   - `AgendaHandler` (`backend/src/main/java/backend/handlers/AgendaHandler.java`): Valida sessão (`401`) e query string `?date=AAAA-MM-DD` (`400`), delega ao `AgendaService` e serializa o JSON da resposta (`200` ou `500`), sem conter consultas de banco ou regras de estado.
   - `Main` (`backend/src/main/java/backend/Main.java`): Registra `GET /doses` no `Router`.

2. **Camada de Domínio (`AgendaService` e Modelos)**
   - `DosePrevista`, `RegistroDeDose`, `ScheduleStatus`, `DoseAction`, `DisplayStatus`, `DailyAgendaItem`, `DailyAgendaResponse` (`backend/src/main/java/backend/models/`).
   - `AgendaService` (`backend/src/main/java/backend/services/AgendaService.java`): Concentra toda a lógica de consulta da agenda diária sem depender de `HttpRequest`, `HttpResponse` ou classes específicas do driver MongoDB.

3. **Camada de Persistência**
   - `PlannedDoseRepository` e `MongoPlannedDoseRepository` (`backend/src/main/java/backend/repositories/`): Persistência idempotente de `DosePrevista` (chave lógica `medicationInUseId + scheduleVersion + scheduledAt`) e consulta por `[startInclusive, endExclusive)`.
   - `DoseRecordRepository` e `MongoDoseRecordRepository` (`backend/src/main/java/backend/repositories/`): Persistência com unicidade por `plannedDoseId` e método de carregamento em lote `findByPlannedDoseIds(Collection<String>)`.

---

## 4. Regras de Domínio Implementadas no `AgendaService`

1. **Cálculo de Fronteira Civil e Fuso Horário (`UTC`)**
   - Dada uma data civil `date` e a zona de referência (padrão `America/Sao_Paulo`), o intervalo consultado é calculado via `date.atStartOfDay(effectiveZone).toInstant()` até `date.plusDays(1).atStartOfDay(effectiveZone).toInstant()`, formando o intervalo semiaberto `[início, início do dia seguinte)` em instantes UTC.
   - Não presume dias fixos de 24 horas (respeita transições de fuso/horário de verão).

2. **Carregamento em Lote e Ausência de Efeitos Colaterais**
   - Após filtrar por `userId` autenticado e descartar ocorrências com `ScheduleStatus.CANCELLED`, o serviço ordena os itens por `scheduledAt` crescente (desempate por `id`).
   - Os IDs filtrados são consultados em uma única chamada em lote (`doseRecordRepository.findByPlannedDoseIds(...)`), evitando consultas N+1.
   - Consultas `GET` repetidas são estritamente somente leitura: nenhuma `DosePrevista` nem `RegistroDeDose` é criado ou alterado.

3. **Derivação de `DisplayStatus`**
   - Se houver `RegistroDeDose` com `DoseAction.TAKEN` $\rightarrow$ `TAKEN`.
   - Se houver `RegistroDeDose` com `DoseAction.SKIPPED` $\rightarrow$ `SKIPPED`.
   - Se **não** houver registro:
     - `now < scheduledAt` $\rightarrow$ `UPCOMING`
     - `scheduledAt <= now <= scheduledAt + 30min` (`DEFAULT_DUE_WINDOW`) $\rightarrow$ `DUE`
     - `now > scheduledAt + 30min` $\rightarrow$ `OVERDUE` (dose vencida/pendente, **nunca** classificada ou persistida automaticamente como `SKIPPED`).

---

## 5. Validação e Resultados dos Testes (JUnit 5)

Foram executadas as suítes [`AgendaDiariaTest`](../backend/src/test/java/backend/tests/AgendaDiariaTest.java) (10 cenários) e [`RouterTest`](../backend/src/test/java/backend/tests/RouterTest.java) (5 cenários), todas aprovadas sem falhas.

### Cenários Cobertos em `AgendaDiariaTest`

| # | Método (`@Test`) | Cenário Validado | Status |
| :---: | :--- | :--- | :---: |
| **1** | `userAConsultsDayWithOrderedDoses` | Usuário A consulta dia com doses às 08:00 e 20:00 $\rightarrow$ `200 OK` com duas doses ordenadas | **PASS** |
| **2** | `userAConsultsEmptyDay` | Usuário A consulta dia sem doses $\rightarrow$ `200 OK` com `doses: []` | **PASS** |
| **3** | `repeatedQueryIsIdempotentAndSideEffectFree` | Consulta repetida do mesmo dia retorna a mesma resposta e cria zero documentos | **PASS** |
| **4** | `takenDoseDisplaysRecordAndPreservesScheduledTime` | Dose registrada como `TAKEN` exibe o registro preservando `scheduledAt` e `08:00` | **PASS** |
| **5** | `pastDoseWithoutRecordIsOverdueAndNeverSkipped` | Dose passada sem registro recebe `displayStatus: OVERDUE` e nunca `SKIPPED` | **PASS** |
| **6** | `cancelledFutureDoseIsOmittedFromDueAgenda` | Dose futura `CANCELLED` após alteração de cronograma é omitida das doses devidas | **PASS** |
| **7** | `userBConsultsSameDateWithoutDataLeakageFromUserA` | Usuário B consulta a mesma data $\rightarrow$ somente dados de B, sem vazamento de A | **PASS** |
| **8** | `invalidDateOrMissingAuthenticationReturns400Or401` | Data ausente/inválida/parâmetro extra $\rightarrow$ `400`; sessão ausente/inválida/expirada $\rightarrow$ `401` | **PASS** |
| **9** | `correctlyFiltersCivilDayBoundariesAcrossUtcMidnight` | Dose às 22:00 SP (`2026-11-06T01:00:00Z`) pertence ao dia civil `2026-11-05` | **PASS** |
| **10** | `realHttpServerIntegrationTest` | Integração real TCP (`Servidor` + `HttpRequestParser` + `Router` + `AgendaHandler`) | **PASS** |

### Saída Real da Execução

```text
=== RUNNING AgendaDiariaTest ===
=================================================================
 EXECUTANDO SUITE DE TESTES JUNIT 5: AgendaDiariaTest
=================================================================
[PASS] correctlyFiltersCivilDayBoundariesAcrossUtcMidnight -> Cenário 9: Dose às 22:00 de 2026-11-05 em São Paulo (2026-11-06T01:00:00Z em UTC) pertence ao dia 2026-11-05
[PASS] takenDoseDisplaysRecordAndPreservesScheduledTime -> Cenário 4: Dose registrada como tomada (TAKEN) -> Exibe registro e preserva horário previsto
[PASS] userBConsultsSameDateWithoutDataLeakageFromUserA -> Cenário 7: Usuário B consulta a mesma data -> Retorna somente dados de B sem vazar dados de A
[PASS] pastDoseWithoutRecordIsOverdueAndNeverSkipped -> Cenário 5: Dose passada sem registro -> Classificada como OVERDUE e nunca como SKIPPED
[PASS] invalidDateOrMissingAuthenticationReturns400Or401 -> Cenário 8: Data inválida/ausente/parâmetro extra retorna 400 e sessão ausente/inválida/expirada retorna 401
[PASS] userAConsultsEmptyDay -> Cenário 2: Usuário A consulta dia sem doses -> 200 OK com doses: []
[PASS] userAConsultsDayWithOrderedDoses -> Cenário 1: Usuário A consulta dia com doses às 08:00 e 20:00 -> 200 OK com duas doses ordenadas
[PASS] realHttpServerIntegrationTest -> Cenário 10: Integração com servidor HTTP real via TCP Socket respondendo GET /doses?date=2026-11-05
[PASS] cancelledFutureDoseIsOmittedFromDueAgenda -> Cenário 6: Dose futura cancelada (CANCELLED) -> Omitida das doses devidas do dia
[PASS] repeatedQueryIsIdempotentAndSideEffectFree -> Cenário 3: Consulta repetida do mesmo dia -> Mesma resposta e zero documentos adicionais criados
-----------------------------------------------------------------
Resultado final: 10 passaram, 0 falharam (Total: 10)
=================================================================
=== RUNNING RouterTest ===
[PASS] shouldReturn405ForUnsupportedMethodOnExistingRoute
[PASS] shouldReturn200ForGetMedications
[PASS] shouldReturn404ForUnknownRoute
[PASS] shouldReturn200ForGetHealth
[PASS] shouldHandleCaseInsensitiveHttpMethod
Total: 5 passaram, 0 falharam.
```
