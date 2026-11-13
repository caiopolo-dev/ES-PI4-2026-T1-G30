# Relatório de Implementação: Cálculo dos Indicadores de Adesão e Pontualidade (`GET /adherence/summary`)

**Projeto:** DoseCerta (`ES-PI4-2026-T1-G30`)  
**Branch:** `36-backend-calculo-dos-indicadores-de-adesao-e-pontualidade`  
**Escopo:** Issue `#36 - [BACKEND] Cálculo dos indicadores de adesão e pontualidade`

---

## 1. Objetivo

Implementar o serviço de domínio (`AdherenceService`) e o endpoint HTTP (`GET /adherence/summary`) em **Java puro** para calcular de forma transparente, auditável e independente de HTTP/MongoDB os indicadores de:
- **Execução (Adesão):** proporção de doses previstas elegíveis efetivamente tomadas (`TAKEN`);
- **Pontualidade:** proporção de doses tomadas (`TAKEN`) que ocorreram dentro da janela de tolerância configurada.

---

## 2. Regras de Elegibilidade, Fórmulas e `NO_DATA`

### 2.1. Política de Elegibilidade e Período
1. O período `[from, to]` é interpretado como datas civis no fuso aplicável (padrão `America/Sao_Paulo`), convertidas para o intervalo UTC `[from 00:00, to+1 00:00)`.
2. Um único instante de referência (`referenceTime = clock.instant()`) é capturado no início da avaliação para evitar inconsistências caso o relógio avance durante a requisição.
3. Uma `DosePrevista` é **elegível** apenas quando atende simultaneamente a todos os critérios:
   - Pertence ao `userId` proveniente da sessão autenticada;
   - Atende aos filtros opcionais (`treatmentId`, `medicationInUseId`), quando informados;
   - Possui `status == ScheduleStatus.SCHEDULED` (doses `CANCELLED` são excluídas);
   - Possui horário previsto já vencido no instante de referência (`!scheduledAt.isAfter(referenceTime)`). **Doses futuras nunca entram no denominador.**
4. Ocorrências são deduplicadas por `id` e os registros são carregados em lote via `DoseRecordRepository.findByPlannedDoseIds(...)`, evitando contagem dupla.

### 2.2. Indicador de Execução (Adesão)
$$\text{executionPercent} = \frac{\text{takenDoses}}{\text{eligiblePlannedDoses}} \times 100$$

- `TAKEN` conta no numerador mesmo quando tomada com atraso.
- `SKIPPED` e doses vencidas sem registro (`unrecordedOverdueDoses`) **não** entram no numerador, mas permanecem no denominador (`eligiblePlannedDoses`).
- Ausência de registro em dose vencida não é convertida em `SKIPPED`.
- **Quando `eligiblePlannedDoses == 0`:** `executionPercent` é `null` e `executionStatus` é `"NO_DATA"` (**nunca `100.0` por padrão**).

### 2.3. Indicador de Pontualidade
$$\text{punctualityPercent} = \frac{\text{onTimeTakenDoses}}{\text{takenDoses}} \times 100$$

- Calculado exclusivamente entre doses elegíveis com ação `TAKEN`, utilizando `scheduledAt` histórico da `DosePrevista` e `actualTime` do `RegistroDeDose`.
- `SKIPPED` e doses sem registro não entram no denominador de pontualidade.
- **Janela de pontualidade adotada (configurável):** `[scheduledAt - 30 min, scheduledAt + 30 min]` (`DEFAULT_EARLY_TOLERANCE = 30 min`, `DEFAULT_PUNCTUALITY_TOLERANCE = 30 min`, sendo 30 minutos o valor experimental adotado na especificação inicial).
  - Tomadas dentro do intervalo fechado são contabilizadas em `onTimeTakenDoses`.
  - Tomadas fora do intervalo (após `+30 min` ou antes de `-30 min`) são contabilizadas em `lateTakenDoses`, mantendo a contagem como `TAKEN` na execução.
- **Arredondamento:** `BigDecimal` com 2 casas decimais e `RoundingMode.HALF_UP`.
- **Quando `takenDoses == 0`:** `punctualityPercent` é `null` e `punctualityStatus` é `"NO_DATA"`.

---

## 3. Contrato HTTP e JSON (`GET /adherence/summary`)

### Requisição
- **Endpoint:** `GET /adherence/summary?from=AAAA-MM-DD&to=AAAA-MM-DD`
- **Filtros opcionais:** `treatmentId`, `medicationInUseId`
- **Autenticação:** Header `Authorization: Bearer <token>` (`401 Unauthorized` se ausente, inválido ou expirado)
- **Validação:** `400 Bad Request` se `from`/`to` estiverem ausentes, malformados, com `from > to`, ou se houver parâmetros desconhecidos (ex.: `userId` arbitrário).

### Exemplos de Resposta

#### `200 OK` (Com doses elegíveis e tomadas)
```json
{
  "period": { "from": "2026-11-03", "to": "2026-11-03" },
  "filters": { "treatmentId": null, "medicationInUseId": null },
  "eligiblePlannedDoses": 2,
  "takenDoses": 2,
  "skippedDoses": 0,
  "unrecordedOverdueDoses": 0,
  "onTimeTakenDoses": 1,
  "lateTakenDoses": 1,
  "executionPercent": 100.0,
  "punctualityPercent": 50.0,
  "executionStatus": "OK",
  "punctualityStatus": "OK",
  "punctualityToleranceMinutes": 30
}
```

#### `200 OK` (Sem doses elegíveis — estado `NO_DATA`)
```json
{
  "period": { "from": "2026-11-04", "to": "2026-11-04" },
  "filters": { "treatmentId": null, "medicationInUseId": null },
  "eligiblePlannedDoses": 0,
  "takenDoses": 0,
  "skippedDoses": 0,
  "unrecordedOverdueDoses": 0,
  "onTimeTakenDoses": 0,
  "lateTakenDoses": 0,
  "executionPercent": null,
  "punctualityPercent": null,
  "executionStatus": "NO_DATA",
  "punctualityStatus": "NO_DATA",
  "punctualityToleranceMinutes": 30
}
```

---

## 4. Arquivos Criados e Atualizados

- [`backend/src/main/java/backend/models/AdherenceSummary.java`](../backend/src/main/java/backend/models/AdherenceSummary.java): DTO com contagens brutas, percentuais (`Double` nullable), tolerância e estados `OK` / `NO_DATA`.
- [`backend/src/main/java/backend/services/AdherenceService.java`](../backend/src/main/java/backend/services/AdherenceService.java): Regras de cálculo desacopladas de HTTP/MongoDB, com `Clock` e tolerâncias injetáveis.
- [`backend/src/main/java/backend/handlers/AdherenceHandler.java`](../backend/src/main/java/backend/handlers/AdherenceHandler.java): Fronteira HTTP (`401`, `400`, `200`, `500`) e serialização JSON para `GET /adherence/summary`.
- [`backend/src/main/java/backend/Main.java`](../backend/src/main/java/backend/Main.java): Correção do bloco duplicado e registro de `GET /adherence/summary` no `Router`.
- [`backend/src/test/java/backend/tests/AdherenceTest.java`](../backend/src/test/java/backend/tests/AdherenceTest.java): Suíte JUnit 5 com os 6 cenários mínimos, testes de borda e integração TCP HTTP real.

---

## 5. Resultados dos Testes Executados (JUnit 5)

| Suíte | Casos Executados | Passaram | Falharam | Cobertura |
| :--- | :---: | :---: | :---: | :--- |
| `AdherenceTest` | 12 | **12** | **0** | 6 cenários mínimos, tolerância exata (`+30m00s`/`+30m01s`/`-30m00s`/`-30m01s`), fuso/registro no dia seguinte, alteração de cronograma preservando v1, isolamento A vs B, `Clock` único, erros `400`/`401` e integração HTTP TCP real |
| `AgendaDiariaTest` | 10 | **10** | **0** | Regressão completa da agenda diária (`GET /doses`) |
| `RouterTest` | 5 | **5** | **0** | Regressão do roteamento base (`/health`, `/medications`, `404`, `405`) |

### Log da Execução (`AdherenceTest`)

```text
=== RUNNING AdherenceTest ===
=================================================================
 EXECUTANDO SUITE DE TESTES JUNIT 5: AdherenceTest
=================================================================
[PASS] invalidParametersOrMissingAuthReturn400Or401 -> Erros HTTP: Parâmetros inválidos/ausentes retornam 400 e sessão ausente/inválida/expirada retorna 401
[PASS] userIsolationFiltersIdempotencyAndConsistentClock -> Borda: Isolamento entre usuários, filtros opcionais, idempotência e Clock fixo por avaliação
[PASS] twoOverdueTwoTakenOneLate -> Caso 1: 2 vencidas, 2 TAKEN (08:05 pontual e 20:47 atrasada) -> Execução 100%, Pontualidade 50%
[PASS] twoOverdueBothSkipped -> Caso 5: 2 vencidas, ambas SKIPPED -> Execução 0% e Pontualidade NO_DATA (null)
[PASS] twoOverdueOneTakenOneSkipped -> Caso 3: 2 vencidas (1 TAKEN pontual, 1 SKIPPED) -> Execução 50%, Pontualidade 100%
[PASS] oneOverdueTakenLateAndOneCancelled -> Caso 6: 1 vencida TAKEN (atrasada) e 1 futura CANCELLED -> Execução 100% e Pontualidade 0%
[PASS] twoOverdueOneTakenOneUnrecorded -> Caso 2: 2 vencidas (1 TAKEN pontual, 1 sem registro) -> Execução 50%, Pontualidade 100%
[PASS] twoFutureDosesReturnNoDataNever100 -> Caso 4: 2 doses futuras, nenhuma vencida -> Execução NO_DATA (null) e Pontualidade NO_DATA (null)
[PASS] realHttpServerIntegrationTest -> Integração HTTP real: Servidor responde GET /adherence/summary com contagens e percentuais
[PASS] punctualityToleranceExactBoundaries -> Borda: Tomada exatamente na fronteira de 30 min é pontual; 1s além é fora da janela
[PASS] crossMidnightRecordAndTimezoneBoundaries -> Borda: Dose às 23:50 SP tomada às 00:05 SP do dia seguinte pertence ao período anterior e é pontual
[PASS] scheduleVersionEditPreservesHistoricalScheduledTime -> Borda: Alteração de cronograma preserva scheduledAt da versão histórica e ignora versão cancelada
-----------------------------------------------------------------
Resultado final: 12 passaram, 0 falharam (Total: 12)
=================================================================
```
