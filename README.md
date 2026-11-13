# ES-PI4-2026-T1-G30

Backend do DoseCerta em Java 21, com Maven e MongoDB.

- [Issue #15: implementação, configuração, execução e testes](docs/relatorio_issue_15.md)
- [Modelagem física do MongoDB](docs/modelagem_fisica_mongoDB.md)

Para iniciar o backend, configure `MONGODB_URI` e `MONGODB_DATABASE` e mantenha o
MongoDB disponível. As instruções completas estão no relatório acima.
# ES-PI4-2026-T1-G30 — DoseCerta Backend

Servidor HTTP em **Java puro** para o backend do projeto **DoseCerta**.

---

## Indicadores de Adesão e Pontualidade (`GET /adherence/summary`)

### Política de Elegibilidade
Para um período civil `[from, to]` no fuso de referência (padrão `America/Sao_Paulo`, convertido para UTC), uma `DosePrevista` é elegível apenas se:
1. Pertencer ao usuário autenticado na sessão (`Authorization: Bearer <token>`);
2. Atender aos filtros opcionais (`treatmentId`, `medicationInUseId`), quando fornecidos;
3. Estiver ativa/agendada (`status == SCHEDULED` — doses `CANCELLED` são excluídas);
4. Já estiver vencida no instante único de referência `referenceTime` capturado no início da avaliação (`scheduledAt <= referenceTime`). **Doses futuras não entram no denominador.**

### Fórmulas e Tratamento de `NO_DATA`

- **Execução (Adesão):**
  $$\text{executionPercent} = \frac{\text{takenDoses}}{\text{eligiblePlannedDoses}} \times 100$$
  - Inclui todas as doses elegíveis com ação `TAKEN` (mesmo tomadas com atraso).
  - `SKIPPED` e doses vencidas sem registro (`unrecordedOverdueDoses`) permanecem no denominador.
  - Se `eligiblePlannedDoses == 0`: `executionPercent = null` e `executionStatus = "NO_DATA"` (nunca `100.0`).

- **Pontualidade:**
  $$\text{punctualityPercent} = \frac{\text{onTimeTakenDoses}}{\text{takenDoses}} \times 100$$
  - Avalia somente as doses elegíveis com ação `TAKEN`, comparando `actualTime` com `scheduledAt` histórico da ocorrência.
  - **Tolerância adotada (configurável):** `30 minutos` (valor experimental: `[scheduledAt - 30 min, scheduledAt + 30 min]`).
  - Se `takenDoses == 0`: `punctualityPercent = null` e `punctualityStatus = "NO_DATA"`.

### Exemplo de Requisição e Resposta (`200 OK`)

```http
GET /adherence/summary?from=2026-11-03&to=2026-11-03
Authorization: Bearer <token>
```

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

> Documentação técnica completa e resultados dos testes: [`docs/relatorio_adesao_pontualidade.md`](docs/relatorio_adesao_pontualidade.md).
