# Medicamentos em uso — issue #27

**Atualizacao da etapa 6 em 21/11/2026:** as rotas de medicamentos estao
registradas na Main com DefaultTreatmentMedicationRules e TreatmentService.
A suite completa executada com MongoDB local teve 261 testes aprovados,
zero falhas, zero erros e zero testes ignorados.

O teste realTreatmentLifecycleWorksThroughHttp utiliza servidor HTTP, sessoes
persistidas e as regras reais dos tratamentos. Cobre rascunho, edicao, consulta,
titularidade, revisao desatualizada, ativacao, protecao do ultimo medicamento
ativo, encerramento e preservacao do historico por outra conexao MongoDB.
O cronograma desse cenario e um dado preparado pelo teste: o fluxo de
versionamento da #28 ainda sera integrado nas proximas etapas.
Os testes existentes tambem verificam concorrencia de edicao e desativacao.
Nenhum dado do Atlas foi utilizado.

**Registro anterior:** as secoes abaixo descrevem a entrega antes desta
integracao. Referencias a rotas desativadas, ausencia de regras reais e resultados
antigos nao representam o estado atual. A revisao completa do contrato e das
pendencias sera feita depois da integracao de versionamento com a #28.

## O que foi preparado

MedicationInUseHandler adapta as requisicoes HTTP ao MedicationInUseService.
O registro e feito pelo metodo registerRoutes(Router), nas tres rotas da #27:

| Metodo | Caminho | Sucesso |
| --- | --- | --- |
| POST | /treatments/{id}/medications | 201 com medicamento criado |
| PUT | /treatments/{id}/medications/{medicationId} | 200 com medicamento atualizado |
| DELETE | /treatments/{id}/medications/{medicationId} | 204, desativacao preservando o documento |

O Router agora reconhece segmentos {id}, mantendo prioridade das rotas exatas.
A autenticacao da #24 continua sendo executada antes dos handlers privados.
O handler usa somente o AuthContext validado e rejeita query parameters.

POST e PUT exigem Content-Type: application/json. Exemplo ficticio:

```json
{
  "name": "Medicamento X",
  "informedDose": "50 mg",
  "quantityPerAdministration": 1,
  "unit": "comprimido",
  "notes": "Exemplo de teste"
}
```

No POST, somente esses campos sao aceitos. No PUT, tambem e obrigatorio enviar
`revision`, um inteiro recebido na ultima resposta do servidor (inicialmente 0).
Revisao ausente/invalida recebe 400; revisao desatualizada recebe 409.
O POST nao aceita revision informada pelo cliente.
ID, userId, active e schedules nao podem ser
definidos pelo cliente. A quantidade atual e inteira, positiva e limitada ao
Integer do modelo existente; valores fracionarios nao sao truncados: recebem 400.
Nome e dose continuam obrigatorios conforme o modelo atual. Campos opcionais
omitidos no PUT ficam null; nao se trata de uma edicao parcial. O corpo deve ser
JSON valido, nao vazio e ter ate 16 KiB. DELETE nao aceita corpo.

A resposta de POST/PUT possui id, name, informedDose,
quantityPerAdministration, unit, notes, active e revision.

## Protecoes de concorrencia

Cada edicao confere a revisao e a incrementa no mesmo comando MongoDB.
Duas edicoes da mesma revisao nao podem vencer juntas. Documentos antigos sem
revision sao tratados como revisao zero. Essa revisao e um controle tecnico de
edicao; nao substitui o versionamento de cronogramas da #28.

A desativacao confere atomicamente a revisao e a quantidade de medicamentos
ativos. TreatmentMedicationRules deve fornecer minimumRemainingActiveMedications:
1 quando o tratamento precisa manter um medicamento ativo, ou 0 quando pode
ficar sem ativos. Duas desativacoes concorrentes respeitam esse limite.
A classificacao real dos estados continua dependendo da #26. Outros fluxos que
alterem medicamentos devem respeitar e incrementar revision; escritas que
ignorem esse contrato nao ficam protegidas. Mudancas concorrentes do estado do
tratamento tambem precisam ser coordenadas na integracao com #26.

## Erros

- 400: corpo, tipos, campos ou quantidade invalidos; query nao suportada.
- 401: sessao ausente/invalida; handler chamado sem contexto autenticado.
- 404: recurso ausente ou de outro titular, tratado pelo Router.
- 405: metodo nao registrado naquele caminho.
- 409: MedicationOperationConflictException, para negacoes de negocio.
- 500: falhas inesperadas, inclusive IllegalStateException tecnica, sem detalhes.

TreatmentMedicationRules deve usar MedicationOperationConflictException para
negacoes de negocio; falhas tecnicas nao devem ser mascaradas como conflito.

## Integracao pendente com #26

O contrato TreatmentMedicationRules ainda nao tem implementacao de producao nesta
base. Portanto, as rotas NAO foram ativadas na Main com uma politica ficticia.
Quando o responsavel integrar as regras, a montagem e:

```java
MedicationInUseRepository repository = new MongoMedicationInUseRepository(
    mongoConnection.getCollection("treatments"));
MedicationInUseService service = new MedicationInUseService(repository, treatmentRules);
new MedicationInUseHandler(service).registerRoutes(router);
```

O objeto treatmentRules deve ser a implementacao real das regras de tratamentos.
Nao criar uma implementacao vazia para liberar gravacoes. A consulta completa
GET /treatments/{id} continua pertencendo a #26; nenhuma consulta extra foi criada.
Rascunhos incompletos e estados CLOSED/DRAFT ainda dependem desse alinhamento.
Mudancas de quantidade/unidade com cronogramas continuam bloqueadas ate #28.

## Verificacao desta etapa

Os testes HTTP foram concentrados em MedicationInUseIntegrationTest, sem criar
uma quarta classe de teste. As verificacoes diretas do Router cobrem delegacao de POST/PUT/DELETE,
identidade, corpo invalido, campos proibidos, quantidade fracionaria/zero/negativa,
rotas dinamicas, prioridade de caminho exato e respostas 401/404/405/409/500.
A revisao acrescentou uma verificacao com Servidor e HttpClient reais, sessoes
persistidas e medicamentos no MongoDB local. A politica de tratamento usada
nesse teste e exclusiva do teste; ela nao representa os estados da #26.
Foram verificados POST/PUT/DELETE, titularidade, documentos intactos em tentativas
cruzadas, recuperacao por outra conexao, conflito de negocio, desativacao e
revogacao de sessao. Tambem foram cobertos Content-Type e limite em bytes UTF-8.

Resultado em 18/11/2026: os 15 testes das tres classes da #27 passaram,
sem falhas ou ignorados. Incluem disputa de edicoes e desativacoes reais no
MongoDB, revisao antiga via HTTP e protecao do ultimo ativo.
Na suite completa com MongoDB, 141 de 143 passaram.
As duas falhas existentes de MongoConnectionValidationTest exigem getCause()
preenchido, enquanto a implementacao da conexao o omite. Esse codigo nao foi
alterado na revisao. Portanto, a suite completa ainda nao esta verde.

A ativacao na Main continua pendente da politica real da #26; a validacao HTTP
acima registra o handler num Router montado pelo proprio teste.

## Arquivos e responsabilidades

| Arquivo | Responsabilidade |
| --- | --- |
| MedicamentoEmUso | ID estavel, dose, quantidade, unidade, observacoes e desativacao |
| MedicationInUseRepository | Contrato de persistencia dos medicamentos |
| MongoMedicationInUseRepository | Atualizacoes pontuais em treatments.medications, com filtro de titular |
| MedicationInUseService | Inclusao, consulta, edicao e desativacao com AuthContext |
| TreatmentMedicationRules | Contrato obrigatorio de regras a integrar com #26 |
| MedicationOperationConflictException | Negacao de negocio traduzida em 409 |
| MedicationInUseHandler | Traducao de JSON/HTTP para chamadas ao servico |
| Router | Reconhecimento de IDs no caminho, preservando autenticacao da #24 |

No MongoTreatmentRepository existente, apenas o mapeamento dos novos campos
unit/active/revision foi acrescentado, inclusive na preservacao de cronogramas durante
atualizacao. O ciclo de vida de tratamentos nao foi implementado nesta issue.

O dominio usa recursos do JDK, sem framework web. A persistencia reutiliza o
driver oficial do MongoDB e o handler usa o parser JSON desse driver ja presente.
JUnit e Mockito sao dependencias existentes de teste; nenhuma foi adicionada.

## Executar os testes

Requisitos: JDK 21 e Maven. Comandos PowerShell na raiz do repositorio:

```powershell
# Modelo e servico, sem MongoDB real.
mvn -f backend/pom.xml "-Dtest=MedicamentoEmUsoTest,MedicationInUseServiceTest" test

# Tres classes da #27, incluindo HTTP e MongoDB reais.
# Inicie antes uma instancia MongoDB local em 127.0.0.1:27017.
$env:MONGODB_URI = 'mongodb://127.0.0.1:27017'
$env:MONGODB_DATABASE = 'dosecerta_test_ping'
mvn -f backend/pom.xml "-Dtest=MedicamentoEmUsoTest,MedicationInUseServiceTest,MedicationInUseIntegrationTest" "-Dmongo.integration=true" test

# Suite completa do projeto, incluindo integracao.
mvn -f backend/pom.xml clean test "-Dmongo.integration=true"
```

As tres classes da #27 possuem 15 metodos de teste: todos passaram em 18/11/2026,
sem testes ignorados. Os bancos de escrita sao locais, exclusivos e aleatorios;
cada teste remove seu proprio banco ao final. Atlas nao foi utilizado.
Os relatorios ficam em backend/target/surefire-reports. Sem mongo.integration=true,
os testes de MongoDB/HTTP integrado aparecem como ignorados, nao aprovados.

Na suite completa permanecem duas falhas preexistentes:
MongoConnectionValidationTest.rejectsInvalidConfiguration e
MongoConnectionValidationTest.rejectsUnavailableServer. O teste exige uma causa
interna na excecao, mas MongoConnection retorna uma mensagem sem causa.
Nao foram alterados esses arquivos para esconder a divergencia.

## Revisao dos criterios de aceite da #27

| Pedido da issue | Situacao atual |
| --- | --- |
| Adicionar medicamento proprio e recuperar por consulta | Parcial: servico/repository e testes funcionam; ativacao e consulta compartilhada da #26 pendentes |
| ID proprio estavel dentro do tratamento | Implementado e testado |
| Validacoes de nome, dose, quantidade/unidade e observacoes acordadas | Parcial: validacoes do modelo atual; politica de rascunho/ativacao e contrato final dependem de alinhamento |
| Editar nos estados permitidos | Parcial: contrato obrigatorio de regras; nao ha implementacao real de DRAFT/ACTIVE/CLOSED nesta base |
| Remover/desativar preservando referencias historicas | Desativacao e limite atomico de ativos implementados; classificacao real dos estados depende da #26 |
| Alterar um medicamento sem sobrescrever outros | Atualizacao pontual e revisao implementadas; concorrencia no mesmo medicamento e entre medicamentos testada no MongoDB |
| Impedir alteracao em tratamento alheio | Implementado no filtro de titular e testado no servico, repository e HTTP |
| Alterar quantidade com versionamento das doses futuras | Pendente #28; servico bloqueia a mudanca com cronogramas em vez de inventar versao |
| Funcionar sem CMED, SerpApi ou precos | Atendido; sem dependencia desses modulos |

## Pendencias para concluir a issue

- Alinhar e fornecer a implementacao real de TreatmentMedicationRules, incluindo
  CLOSED, rascunhos incompletos, validacao de ativacao e ultimo medicamento ativo.
- Conectar a montagem na Main somente com essas regras, mantendo as verificacoes
  de estado e concorrencia na operacao atomica do repository quando necessario.
- Integrar os medicamentos na resposta GET /treatments/{id} da #26. Nao foi
  criado outro endpoint de consulta para substituir o trabalho do responsavel.
- Integrar quantidade/unidade ao contrato de versionamento da #28 e testar
  preservacao de ocorrencias passadas e alteracao das futuras.
- Alinhar os demais escritores de medicamentos ao controle revision, e expor
  essa revisao na consulta compartilhada da #26 para recarregar dados apos 409.
- Repetir o fluxo completo do MVP apos essas integracoes.

Manter a quantidade inteira foi uma escolha de compatibilidade com o codigo
existente. O Decimal128 fracionario da modelagem e proposto e ainda requer
alinhamento; esta entrega nao afirma suporta-lo.
Nenhum teste com politica ficticia comprova as regras reais pendentes da #26.
A issue nao deve ser encerrada como totalmente concluida enquanto esses pontos
permanecerem pendentes.
