# Issue #35 — contrato da consulta de histórico

Handler implementado e rota registrada na Main na etapa 8, usando a autenticação
e os repositories existentes. Na etapa 9, o endpoint foi validado com servidor HTTP,
sessoes persistidas e MongoDB local: 12 testes da #35 aprovados, sem falhas ou ignorados.
A revisão conjunta com frontend e responsável pelos indicadores fica pendente.

## Requisição

`GET /adherence/history?from=2026-11-03&to=2026-11-09`

Exige sessão válida pelo mecanismo de autenticação existente. A identidade vem
do AuthContext; o cliente não pode escolher um userId. GET não aceita corpo.

| Parâmetro | Obrigatório | Regra |
| --- | --- | --- |
| from | Sim | Data válida no formato exato yyyy-MM-dd |
| to | Sim | Data válida no formato exato yyyy-MM-dd, igual ou posterior a from |
| treatmentId | Não | ID não vazio; restringe as doses do próprio usuário |
| medicationInUseId | Não | ID não vazio; restringe as doses do próprio usuário |

São aceitos somente esses quatro parâmetros, sem repetições. Valores vazios,
datas impossíveis e parâmetros desconhecidos recebem 400. Ambos os filtros,
quando presentes, são aplicados juntos. IDs sem correspondência nas doses do
titular retornam lista vazia, incluindo IDs de outro usuário, sem expor sua existência.

O limite é **90 dias locais, contando as duas datas**. Um único dia é permitido.
O período pode incluir doses futuras: histórico não significa filtrar só o passado.

## Período e fuso

O fuso da consulta é **America/Sao_Paulo**, consistente com o padrão atual do
backend. Não há parâmetro timezone nesta versão do endpoint.
As datas são locais e inclusivas. A busca converte o período em:

`[from.atStartOfDay(zone), to.plusDays(1).atStartOfDay(zone))`

A conversão usa ZoneId e suas regras de fuso, sem fixar um deslocamento UTC.
O filtro é aplicado ao scheduledAt da dose, nunca ao actualTime ou recordedAt.
Uma ação registrada em outro dia continua associada à ocorrência selecionada.

## Resposta 200

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
      "plannedQuantity": 1,
      "plannedUnit": null,
      "action": "TAKEN",
      "actualTime": "2026-11-03T11:05:00Z",
      "recordedAt": "2026-11-03T11:06:00Z",
      "isCancelled": false
    }
  ]
}
```

Dados fictícios. Instantes são serializados em UTC, no formato ISO-8601 com Z;
datas from/to permanecem locais. Quantidade é número JSON, preservando BigDecimal.
referenceTime é capturado uma única vez por consulta. Com scheduledAt, permite
distinguir ocorrência futura de vencida sem criar uma ação ou calcular pontualidade.
Os itens são ordenados por scheduledAt crescente e, em empate, pelo ID crescente.
Sem ocorrências, a mesma estrutura retorna items: []. Não há paginação nesta versão.

## Significado dos dados

- Previsão, nome, quantidade, versão e fuso vêm da DosePrevista persistida.
- Ações são associadas em lote pelo ID da ocorrência e pelo titular.
- action é TAKEN, SKIPPED ou null. Sem registro, action, actualTime e recordedAt
  são null. Ausência de registro nunca é convertida em SKIPPED ou TAKEN.
- isCancelled indica ScheduleStatus.CANCELLED. Canceladas continuam na resposta;
  um registro associado válido é preservado mesmo quando a dose está cancelada.
- O modelo atual não possui motivo de cancelamento; o endpoint não inventa um.
- plannedUnit é null porque DosePrevista não armazena unidade histórica.
  medicationName também permanece null se ausente na ocorrência. Não se consulta
  o medicamento atual para preencher dados como se fossem históricos.
- Consultar tratamento encerrado ou medicamento hoje inativo não exclui suas doses.
  O horário e a versão antigos não são reconstruídos pelo cronograma atual.

## Erros, implementação e limites

| Código | Situação |
| --- | --- |
| 200 | Histórico, inclusive lista vazia |
| 400 | Parâmetros/corpo inválidos, período invertido ou superior a 90 dias |
| 401 | Sessão ausente, inválida ou expirada |
| 500 | Falha técnica inesperada, sem detalhes internos na resposta |

Reutilizar PlannedDoseRepository.findByUserAndPeriod() e a consulta em lote
DoseRecordRepository.findByPlannedDoseIdsAndUserId(). Aplicar filtros opcionais
às doses do titular já recuperadas. Não criar repository paralelo.
O contrato não depende de MedicationInUseService ou das regras novas da #27.

A operação é somente leitura: não gera doses, cancela ocorrências, registra ações,
altera tratamentos ou calcula percentuais/classificação de pontualidade.
Os testes devem cobrir os cenários da #35, fronteiras dos dias, limite de 90 dias,
ordenação estável, titularidade e ausência de efeitos de escrita.

## Revisão conjunta pendente

Os exemplos de consumo estão no [README](../README.md#histórico-de-doses-get-adherencehistory).
Os pontos abaixo estão preparados para revisão, mas ainda não representam acordo
confirmado com os responsáveis pelas outras partes:

| Responsável | Pontos para confirmar |
| --- | --- |
| Frontend | Envelope e nomes dos campos; conversão dos instantes UTC para exibição; período em America/Sao_Paulo; limite inclusivo de 90 dias; filtros e lista vazia |
| Frontend | Exibir ausência de ação como ausência de registro, sem assumir SKIPPED; distinguir futuras e canceladas; tratar nome e unidade ausentes |
| Indicadores (#36) | Identidade da ocorrência, scheduledAt histórico, action e actualTime; seleção por data prevista mesmo com registro posterior |
| Indicadores (#36) | O histórico inclui futuras e canceladas; sua presença na lista não significa elegibilidade para os indicadores. O cálculo e suas regras permanecem no serviço de indicadores |

A revisão não exige implementar telas ou modificar o cálculo dos indicadores
nesta issue. Eventuais ajustes no contrato devem ser combinados antes de alterar
os consumidores.

## Verificação final — etapa 11

Em 21/11/2026, a suíte foi recompilada e executada com JDK 21 e MongoDB local
isolado para testes, sem usar o Atlas:

```powershell
mvn -f backend/pom.xml clean test -Dmongo.integration=true
```

Para reproduzir os testes de integração, disponibilize MongoDB em
`mongodb://127.0.0.1:27017` e configure `MONGODB_URI` com esse endereço e
`MONGODB_DATABASE` com um nome exclusivo de testes. Use ambiente de testes,
pois a suíte cria e remove seus dados temporários.

Resultado: **275 testes JUnit, zero falhas, zero erros e zero ignorados**.
Desses, 8 pertencem a DoseHistoryServiceTest e 4 a DoseHistoryIntegrationTest.
MedicationTest e MongoConnectionTest também foram executados pelo método main
e passaram; esses dois executáveis não entram na contagem do JUnit.

| Critério da Definition of Done | Resultado e evidência |
| --- | --- |
| Consulta autenticada por intervalo | Implementada; HTTP com sessão persistida e validação das datas |
| Ocorrências previstas, inclusive sem registro | Validado para TAKEN, SKIPPED, vencidas e futuras sem ação |
| Associação ao registro, tratamento e medicamento | IDs e contexto da ocorrência persistida; registros recuperados em lote e vinculados ao titular |
| Diferenciar ações, ausência e cancelamento | Ações explícitas, campos null quando ausentes e isCancelled independente da ação |
| Preservar passado após mudança ou encerramento | Teste com alteração de cronograma, fechamento do tratamento e nova conexão com MongoDB |
| Datas, fuso, filtros e ordenação | Testes dos limites locais, horário de verão histórico, 90 dias inclusivos, filtros e desempate por ID |
| Bloquear acesso de outro usuário | Testes com duas sessões e filtros sem exposição de ocorrências alheias |
| Consulta sem escrita | Verificação das interações do serviço e comparação das coleções antes e depois das requisições HTTP |
| Testes de serviço, persistência e HTTP | 12 testes da #35 aprovados, inclusive integração com MongoDB e servidor HTTP reais |
| DTO e README revisados com frontend e indicadores | **Pendente de revisão conjunta**, conforme os pontos preparados acima |

Limite conhecido do contexto disponível: plannedUnit permanece null porque
DosePrevista não persiste unidade histórica; medicationName também pode ser null.
A consulta preserva os dados existentes e não reconstrói informações ausentes
a partir do cadastro atual.
