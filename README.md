# ES-PI4-2026-T1-G30

Backend do DoseCerta em Java 21, com Maven e MongoDB.

- [Issue #15: implementação, configuração, execução e testes](docs/relatorio_issue_15.md)
- [Issue #24: autorizacao, rotas protegidas, testes e pendencias](docs/autorizacao_issue_24.md)
- [Issue #27: medicamentos em uso, contratos, testes e pendencias](docs/issue_27_endpoints.md)
- [Issue #35: contrato da consulta de histórico](docs/issue_35_contrato.md)
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

## Histórico de doses (`GET /adherence/history`)

Consulta as doses previstas e seus registros do usuário autenticado, sem alterar
o banco. Use um token de sessão válido e o endereço/porta do servidor em execução:

```powershell
curl.exe -i "http://127.0.0.1:8081/adherence/history?from=2026-11-03&to=2026-11-09" -H "Authorization: Bearer <token>"
```

Para restringir por tratamento e medicamento em uso, combine os filtros opcionais:

```http
GET /adherence/history?from=2026-11-03&to=2026-11-09&treatmentId=treatment-001&medicationInUseId=medication-001
Authorization: Bearer <token>
```

Exemplo fictício de resposta `200 OK`:

```json
{
  "from": "2026-11-03",
  "to": "2026-11-09",
  "timezone": "America/Sao_Paulo",
  "referenceTime": "2026-11-09T15:00:00Z",
  "items": [
    {
      "plannedDoseId": "dose-001",
      "treatmentId": "treatment-001",
      "medicationInUseId": "medication-001",
      "medicationName": "Medicamento X",
      "scheduleVersion": 1,
      "scheduledAt": "2026-11-03T11:00:00Z",
      "scheduleTimezone": "America/Sao_Paulo",
      "plannedQuantity": 0.5,
      "plannedUnit": null,
      "action": "TAKEN",
      "actualTime": "2026-11-03T11:05:00Z",
      "recordedAt": "2026-11-03T11:06:00Z",
      "isCancelled": false
    }
  ]
}
```

`from` e `to` são obrigatórios, no formato `yyyy-MM-dd`, e abrangem até **90 dias
locais inclusivos**, no fuso `America/Sao_Paulo`. A seleção usa `scheduledAt`,
mesmo quando a ação foi registrada em outro dia. Os instantes da resposta estão
em UTC (`Z`). A ordem é crescente por `scheduledAt` e, em empate, pelo ID da dose.

Sem ocorrências, a resposta mantém o envelope e retorna `items: []`. Sem registro,
`action`, `actualTime` e `recordedAt` são `null`; isso não significa `SKIPPED`.
Doses futuras e canceladas também aparecem, com cancelamento indicado por
`isCancelled`. `referenceTime` permite comparar o instante previsto com o momento
da consulta. O endpoint não calcula percentuais nem classifica pontualidade.

Os dados vêm da ocorrência persistida, preservando o horário e a versão históricos.
`plannedUnit` permanece `null`, pois a ocorrência atual não armazena essa informação;
o nome também pode ser `null` se estiver ausente na ocorrência. Não se preenchem
esses campos com os dados atuais do medicamento.

Sessão ausente, inválida ou expirada retorna `401`. Parâmetros inválidos, repetidos,
desconhecidos, período invertido, superior a 90 dias ou corpo na consulta retornam
`400`. O cliente não envia `userId`: a identidade vem da sessão. Filtros sem
correspondência nas doses do titular retornam lista vazia. Falhas técnicas retornam
`500`, sem detalhes internos.

O [contrato completo](docs/issue_35_contrato.md) detalha os campos e limites.
A revisão conjunta com frontend e responsável pelos indicadores (#36) está
**pendente**; os pontos para alinhamento estão no contrato.

## React servido pelo Java

O React é somente a interface. O build é copiado para o classpath pelo Maven e o
servidor HTTP de `backend.Main` continua responsável tanto pelas páginas quanto
pelas APIs e pelo acesso ao MongoDB. Não há servidor Node.js em produção.

Pré-requisitos de build: Java 21, Maven, Node.js 22 e pnpm. Configure o MongoDB no
mesmo terminal em que o Java será iniciado:

```powershell
$env:MONGODB_URI = "mongodb://127.0.0.1:27017"
$env:MONGODB_DATABASE = "dosecerta"
```

Na primeira execução, ou após alterar o frontend, gere os arquivos estáticos:

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm build
```

Depois, desligue o Vite e inicie apenas o servidor Java:

```powershell
cd ..\backend
mvn clean compile exec:java -Dexec.mainClass=backend.Main
```

A aplicação completa fica disponível em `http://localhost:8080`. O Maven copia
`frontend/dist` para `target/classes/web`; por isso o build do frontend deve existir
antes de compilar o backend. Para usar outra porta, informe um único argumento ao
`Main` (por exemplo, `-Dexec.args=8081`).

O comando `pnpm dev` é opcional e exclusivo para desenvolvimento visual. Nesse
modo, o proxy do Vite encaminha as rotas `/auth`, `/treatments`, `/doses`,
`/adherence`, `/medications` e `/health` ao Java sem introduzir prefixo `/api`.
