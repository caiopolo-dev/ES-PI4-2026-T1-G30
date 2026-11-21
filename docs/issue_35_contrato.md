# Issue #35 — contrato da consulta de histórico

Contrato definido para orientar a implementação. Endpoint ainda não implementado.
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
