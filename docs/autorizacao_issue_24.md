# Autorizacao por titularidade - issue #24

## Implementacao atual

O Router valida as rotas privadas com SessionAuthenticator conectado ao
SessionService da #23. A identidade da sessao persistida em auth_sessions e
entregue ao handler em AuthContext, numa copia de HttpRequest. Query, body e
headers de identidade nao substituem o titular. AgendaHandler e AdherenceHandler
exigem esse contexto e nao autenticam novamente por conta propria.

| Metodo | Rota existente | Acesso |
| --- | --- | --- |
| GET | /health | Publico |
| POST | /auth/register | Publico |
| POST | /auth/login | Publico |
| GET | /auth/me | Sessao valida |
| POST | /auth/logout | Sessao valida |
| GET | /medications | Sessao valida; catalogo fixo compartilhado |
| GET | /doses?date=AAAA-MM-DD | Sessao valida; doses do titular |
| GET | /adherence/summary?from=AAAA-MM-DD&to=AAAA-MM-DD | Sessao valida; indicadores do titular |

Novas rotas registradas sao privadas por padrao. Rotas inexistentes retornam 404
e metodos nao registrados retornam 405 antes da validacao de sessao.

TreatmentAccessService restringe listagem, consulta, consulta de medicamento e
atualizacao ao titular. DoseAccessService verifica a dose antes de consultar ou
registrar uma acao; o registro usa usuario da sessao e horario do servidor.
Nao sobrescreve registros anteriores e rejeita doses canceladas.
Agenda e indicadores consultam apenas doses do titular e usam
findByPlannedDoseIdsAndUserId para restringir os registros associados.

## Respostas e logs

| Situacao | Resposta |
| --- | --- |
| Sessao ausente, invalida, expirada ou revogada | 401 |
| Recurso inexistente ou de outro titular | 404 com a mesma mensagem |
| Permissao explicitamente negada | 403 |
| Falha interna inesperada | 500 sem detalhes internos |

ResourceNotFoundException e AccessDeniedException sao traduzidas pelo Router.
Os futuros handlers devem deixar essas excecoes chegarem a ele.
Nao foram criados papeis administrativos ou permissoes entre familiares.

AccessAudit usa java.util.logging com eventos fixos AUTHENTICATION_REQUIRED,
PERMISSION_DENIED e RESOURCE_UNAVAILABLE. O ultimo nao diferencia ausencia de
recurso e titularidade negada. Nao registra tokens, senhas, IDs, caminho, query,
corpo, dados de tratamento ou stack traces. 401/403 devolvidos pelos handlers
tambem geram eventos; rotas desconhecidas nao geram eventos de titularidade.

## Executar os testes

Requisitos: JDK 21, Maven e, para integracao, MongoDB local em 127.0.0.1:27017.
Comandos PowerShell a partir da raiz do repositorio:

```powershell
# Sem banco: testes unitarios e HTTP com repositories em memoria.
mvn -f backend/pom.xml clean test

# Todos, incluindo usuarios e sessoes no MongoDB local.
$env:MONGODB_URI = 'mongodb://127.0.0.1:27017'
$env:MONGODB_DATABASE = 'dosecerta_test_ping'
mvn -f backend/pom.xml clean test "-Dmongo.integration=true"

# Somente o novo fluxo HTTP com MongoDB.
mvn -f backend/pom.xml "-Dmongo.integration=true" "-Dtest=AuthorizationMongoIntegrationTest" test
```

O MongoDB deve estar iniciado antes do comando de integracao. As variaveis valem
somente no terminal atual. Os testes de escrita usam bancos locais aleatorios,
removidos ao terminar; o ping usa a configuracao acima e nao grava documentos.
Nenhum desses comandos configura Atlas ou utiliza credenciais reais.

Os resultados ficam em backend/target/surefire-reports. Skipped significa nao
executado. Em 15/11/2026, clean test com mongo.integration=true executou **128
testes, zero falhas, zero erros e zero ignorados**.

## Evidencias e limites

| Cenario | Evidencia |
| --- | --- |
| Rotas publicas e privadas por padrao | RouteAccessPolicyTest, RouterAuthenticationTest |
| Identidade da sessao, tentativa de forjar userId | AuthenticatedIdentityTest, RouterAuthenticationTest |
| Tratamentos/medicamentos de A inacessiveis a B | TreatmentAccessServiceTest (memoria) |
| Dose propria permitida; tentativa cruzada nao grava | DoseAccessServiceTest (memoria) |
| Agenda e indicadores isolados por titular | AgendaDiariaTest, AdherenceTest, DoseRecordOwnershipTest |
| Erros sem revelar existencia de recurso alheio | AccessErrorResponseTest |
| Logs sem parametros ou excecoes sensiveis | AccessAuditTest |
| Login, identidade, logout, expiracao e token em hash | AuthorizationMongoIntegrationTest (MongoDB e HTTP reais) |

O teste de integracao usa Servidor real, dois usuarios e sessoes no MongoDB,
confirma sessao por uma segunda conexao e verifica que logout invalida apenas a
sessao encerrada. A rota /test/owned e exclusiva do teste: comprova o caminho
Router -> contexto -> TreatmentAccessService, com tratamentos em memoria.
Nao representa endpoint de produto implementado.

## Pendencias para concluir a issue

- Integrar TreatmentAccessService aos endpoints de tratamentos e medicamentos
  quando as funcionalidades #26/#27 estiverem disponiveis.
- Integrar DoseAccessService ao endpoint real de registro de doses.
- Proteger o historico quando a #35 for integrada.
- Repetir testes de titularidade em todos os endpoints privados finais do MVP,
  inclusive consultas, alteracoes, ativacao e encerramento.
- Validar persistencia real de tratamentos e doses: os repositories atuais
  usados nestes testes mantem esses dados em memoria. Apenas usuarios e sessoes
  foram comprovados no MongoDB neste fluxo.

A infraestrutura e os endpoints existentes foram testados no limite descrito.
A issue nao deve ser encerrada como completa enquanto as integracoes acima
estiverem pendentes. Nao houve validacao do frontend nem de ambiente de producao.
