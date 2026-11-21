# Issue #27 — medicamentos em uso

Revisão de 21/11/2026. Operações básicas integradas à #26 e edição de
quantidade/unidade conectada ao versionamento da #28, com persistência e HTTP
testados. Os pontos de alinhamento com a equipe estão discriminados ao final.

## API disponível

As rotas estão registradas na Main com a conexão MongoDB e a sessão existentes.
O usuário vem do AuthContext; o JSON não define titularidade.

| Método | Caminho | Sucesso |
| --- | --- | --- |
| POST | /treatments/{id}/medications | 201, medicamento criado |
| PUT | /treatments/{id}/medications/{medicationId} | 200, medicamento editado |
| DELETE | /treatments/{id}/medications/{medicationId} | 204, desativação |
| GET | /treatments/{id} | 200, tratamento com medicamentos e cronogramas; endpoint da #26 |

POST e PUT exigem Content-Type: application/json. Exemplo fictício de inclusão:

```json
{
  "name": "Medicamento X",
  "informedDose": "50 mg",
  "quantityPerAdministration": 1,
  "unit": "comprimido",
  "notes": "Exemplo de cadastro informado pelo usuário"
}
```

Exemplo de edição sem alterar quantidade/unidade:

```json
{
  "name": "Medicamento X",
  "informedDose": "50 mg",
  "quantityPerAdministration": 1,
  "unit": "comprimido",
  "notes": "Observação atualizada",
  "revision": 0
}
```

Usar a revisão recebida na última resposta/consulta. POST gera revisão 0;
PUT exige revisão inteira, não negativa e inferior a Long.MAX_VALUE.
Nome e dose informada são obrigatórios. Quantidade, quando informada, deve ser
inteira positiva; frações recebem 400. Quantidade/unidade podem faltar em DRAFT,
mas são exigidas pelas regras de inclusão/edição em ACTIVE.
PUT substitui os campos editáveis: opcionais omitidos ficam null.
O limite do corpo é 16 KiB. Campos desconhecidos e query parameters são rejeitados.
DELETE não aceita corpo. ID, userId, active e schedules não são editáveis pelo JSON.

POST/PUT retornam id, name, informedDose, quantityPerAdministration, unit, notes,
active e revision. A consulta da #26 mantém os nomes existentes dose, quantity
e observations, além de id, name, unit, active, revision e schedules.
O consumidor deve fazer essa correspondência ao montar o PUT.

### Edição de quantidade/unidade com cronograma

Quando o medicamento já possui cronograma, envie `validFrom` no PUT para criar
uma nova versão com a quantidade/unidade desejadas. Exemplo fictício:

```http
PUT /treatments/{id}/medications/{medicationId}
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "Medicamento X",
  "informedDose": "50 mg",
  "quantityPerAdministration": 2,
  "unit": "comprimido",
  "notes": "",
  "revision": 1,
  "validFrom": "2026-12-01T11:00:00Z"
}
```

Use uma vigência futura no momento da requisição e a revisão efetivamente
consultada. A vigência deve ser posterior à da última versão e respeitar o período
do tratamento/cronograma. O resultado é `200`, com medicamento e revisão atualizados.
Sem `validFrom`, a edição comum continua retornando `409` se tentar mudar
quantidade/unidade de um medicamento com cronogramas. POST não aceita `validFrom`.
No fluxo versionado, os dados devem estar completos e a quantidade ou unidade
deve realmente mudar. Datas inválidas/passadas recebem `400`; revisão antiga,
medicamento inativo ou tratamento encerrado recebem `409`.

| Código | Situação |
| --- | --- |
| 400 | Corpo, campos, tipos, quantidade ou revisão inválidos; query não suportada |
| 401 | Sessão ausente, expirada ou inválida |
| 404 | Recurso inexistente ou de outro titular |
| 405 | Método não registrado no caminho |
| 409 | Revisão antiga, estado incompatível ou edição que exige versionamento |
| 500 | Falha técnica inesperada, sem detalhes internos na resposta |

## Integração com a #26

DefaultTreatmentMedicationRules implementa TreatmentMedicationRules e reutiliza
TreatmentService.find() para consultar o tratamento com titularidade.
CLOSED bloqueia inclusão, edição e desativação. ACTIVE exige medicamento completo.
A desativação deve manter medicamento ativo com cronograma válido; DRAFT pode
ficar sem medicamentos ativos. A exclusão é lógica, preservando ID e referências.

O repository faz atualizações pontuais com revisão otimista e verifica o estado
na gravação. A desativação confere atomicamente a quantidade de ativos e outro
medicamento ativo com cronograma marcado ativo. Outros escritores precisam
respeitar a mesma revisão.

**Alinhamento na ativação:** Treatment.validarDadosAtivacao() verifica cronograma
válido, mas não chama MedicamentoEmUso.validateForActivation() nem filtra
explicitamente medicamentos inativos nessa busca. Os testes aprovados não
significam que essa regra pendente foi implementada. Ajustes nesse fluxo devem
ser coordenados com o responsável pela #26.

## Integração com a #28: estado real

A #28 fornece ScheduleService.prepararTransicao(), vigência e persistência
condicional de cronogramas. Reutilizamos essa implementação sem alterar
ScheduleService ou CronogramaDose.

Na #27, prepareVersionedUpdate() prepara dados anteriores, novos e transição de
cronogramas sem gravar. Exige revisão atual, medicamento ativo, dados completos
e início de vigência futuro/posterior ao início conhecido da versão atual.
saveVersionedUpdate() grava medicamento e versões juntos, com um único comando
MongoDB e um incremento de revision, usando updateWithSchedules().

O PUT chama esse fluxo quando recebe validFrom. Cada versão persiste
quantityPerAdministrationSnapshot e unitSnapshot no próprio cronograma, sem
repository ou coleção paralela. A versão encerrada mantém os valores anteriores;
a nova recebe os valores solicitados. As cópias do modelo, a leitura BSON e as
alterações posteriores de horário preservam esses campos.

O GET `/treatments/{id}/medications/{medicationId}/schedule` expõe esses campos
em current/versions. O consumidor deve selecionar a versão pela vigência e usar
seus snapshots, pois quantity/unit no medicamento representam a última edição
cadastrada, inclusive quando ela só começa a valer no futuro.

**Compatibilidade:** documentos anteriores continuam legíveis com snapshots null.
Na primeira mudança de quantidade, captura-se o valor conhecido da versão atual
que está sendo encerrada; versões históricas mais antigas não são preenchidas
com valores atuais. Quando a versão consultada tem snapshot null, a informação
histórica continua desconhecida; não há reconstrução automática do passado.

Essa é a estratégia implementada nesta branch para a integração #27/#28; a
implementação não representa confirmação de revisão conjunta pelos colegas.
O fluxo não gera, cancela ou altera doses previstas. Cronogramas legados sem
início de vigência conhecido não são substituídos inventando datas históricas.

## Testes e execução

Última execução: **275 testes, zero falhas, zero erros e zero ignorados**, com
MongoDB local. Atlas não foi utilizado. As falhas antigas de conexão não aparecem
nesta base atualizada; os resultados anteriores foram substituídos por esta rodada.

Cobertura: titularidade, validação, estados, consulta, desativação, revisão antiga,
servidor HTTP com sessões reais, leitura por outra conexão, preparação sem escrita
e gravação conjunta. Um teste existente disputa duas gravações da mesma revisão,
uma de quantidade e outra de unidade: apenas uma vence, sem misturar os dados,
preservando versões anteriores e outro medicamento.
O fluxo conjunto também foi testado pelo PUT HTTP, com sessão real e MongoDB:
mudança de quantidade/unidade, bloqueio de outro titular, datas inválidas/passadas,
revisão antiga, fronteira exata da vigência, alteração posterior de horário e
recuperação dos snapshots por outra conexão após encerramento do tratamento.
Os testes existentes foram ampliados; nenhuma classe de teste nova foi criada.

Com JDK 21, Maven e MongoDB local iniciado, executar na raiz do repositório:

```powershell
$env:MONGODB_URI = 'mongodb://127.0.0.1:27017'
$env:MONGODB_DATABASE = 'dosecerta_test_ping'
mvn -f backend/pom.xml clean test "-Dmongo.integration=true"
```

Os testes de escrita usam bancos temporários exclusivos e removem seus próprios
dados. Relatórios: backend/target/surefire-reports. Sem as condições de integração,
testes podem ser ignorados; isso não equivale a aprovação.

## Revisão dos critérios da #27

| Critério | Situação |
| --- | --- |
| Adicionar medicamento próprio e recuperar por consulta | Implementado e testado |
| ID próprio e estável | Implementado e testado |
| Validações acordadas dos campos | Parcial: comportamento atual documentado; política final de rascunho e quantidade fracionária ainda exigem alinhamento |
| Editar em estados permitidos | Implementado, com restrições documentadas para quantidade/unidade |
| Desativar preservando referências históricas | Implementado, sem exclusão física |
| Alterar um medicamento sem sobrescrever outros | Implementado e testado, inclusive sob concorrência |
| Bloquear alteração em tratamento alheio | Implementado e testado |
| Quantidade com impacto futuro segue versionamento acordado | Implementado e testado por HTTP/MongoDB com snapshots por versão; revisão conjunta da estratégia ainda não confirmada |
| Cadastro sem CMED, SerpApi ou preços | Atendido |

Para concluir formalmente: confirmar os contratos pendentes com a equipe e
coordenar a validação de ativação com o responsável pela #26. O endpoint de
edição versionada já está conectado e testado. A integração do gerador com os
snapshots e a política para doses futuras materializadas pertencem ao módulo de
doses; não foram implementadas nesta alteração.
**A #27 não deve ser encerrada como concluída apenas porque a suíte passou.**
