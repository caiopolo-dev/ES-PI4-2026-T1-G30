# Politica de acesso - issue #24

## Etapa 2: classificacao das rotas

RouteAccessPolicy verifica a combinacao exata de metodo HTTP e caminho.
Somente as tres rotas abaixo sao publicas. As demais exigem sessao por padrao,
inclusive rotas futuras.

| Metodo | Caminho | Acesso definido |
| --- | --- | --- |
| GET | /health | Publico |
| POST | /auth/register | Publico |
| POST | /auth/login | Publico |
| GET | /auth/me | Autenticado |
| POST | /auth/logout | Autenticado |
| GET | /medications | Autenticado |
| GET | /doses | Autenticado |
| GET | /adherence/summary | Autenticado |
| Qualquer | Demais rotas | Autenticado por padrao |

Esta etapa define a politica. A aplicacao centralizada no Router e o contexto
autenticado serao implementados na etapa 3. Os handlers que ja validam sessoes
continuam com seu comportamento atual. A titularidade de recursos sera tratada
nas etapas seguintes, usando a identidade da sessao da issue #23.

RouteAccessPolicyTest verifica rotas publicas, privadas e futuras, metodos
incorretos, caminhos semelhantes e valores ausentes.

## Etapa 3: protecao centralizada

O Router recebe o SessionAuthenticator conectado ao SessionService da #23. Rotas privadas sem sessao valida recebem 401 antes do handler. Falhas internas retornam 500 generico. Rotas inexistentes e metodos nao registrados preservam 404/405.

A identidade validada e entregue em AuthContext, numa copia da HttpRequest. Headers, query e body nao definem essa identidade. Agenda e indicadores usam o contexto; quando chamados diretamente, mantem a validacao de sessao existente. A Main compartilha o autenticador real entre Router e handlers.

RouterAuthenticationTest cobre rotas publicas, negacao por padrao, contexto derivado da sessao e sessoes expiradas/revogadas/invalidas. Titularidade de recursos sera tratada nas proximas etapas.

## Etapa 4: identidade efetiva

AgendaHandler e AdherenceHandler recebem somente o servico de negocio e obtem o titular exclusivamente de HttpRequest.getAuthContext(), criado pelo Router apos validar a sessao. Nao consultam mais o autenticador diretamente. Chamadas sem contexto retornam 401 antes do servico. userId na query permanece rejeitado; body e headers de identidade nao autenticam. MeHandler e LogoutHandler continuam vinculados ao token validado pelo SessionService da #23.

AuthenticatedIdentityTest comprova que campos do cliente e um token nao validado nao liberam chamadas diretas aos handlers. Os testes HTTP de agenda e indicadores verificam rejeicao de userId na query e isolamento entre titulares. A protecao de operacoes de tratamentos e doses sera completada nas proximas etapas.


## Etapa 5: acesso a tratamentos e medicamentos

TreatmentAccessService recebe AuthContext e TreatmentRepository. Lista pelo titular e consulta por ID junto ao titular; a consulta de medicamento exige tratamento proprio e vinculo com esse tratamento. A atualizacao verifica o tratamento existente e impede alterar seu titular. Recursos inexistentes e alheios lancam a mesma NoSuchElementException generica.

Nao foram criados endpoints nem regras de ciclo de vida da issue #26/#27. Os futuros handlers devem chamar esta camada com o contexto do Router e traduzir a ausencia para 404. A integracao HTTP de edicao/desativacao de medicamentos permanece pendente desses endpoints. Os testes usam o armazenamento em memoria existente de MongoTreatmentRepository; nao comprovam persistencia real.


## Etapa 6: doses e registros

DoseAccessService exige AuthContext e verifica o titular da DosePrevista antes de consultar seu registro ou salvar uma acao. O novo registro recebe userId da sessao, ID gerado pelo modelo e recordedAt do relogio do servidor. Doses canceladas e registros anteriores nao sao alterados por este servico. Ausencia e dose alheia produzem a mesma excecao generica.

DoseAccessServiceTest verifica dois usuarios, tentativa cruzada sem gravacao, registro proprio, dose cancelada e preservacao do registro anterior. Sao testes em memoria dos repositories existentes; a prova no MongoDB real e a integracao com o futuro endpoint de registro continuam pendentes. A politica completa de registro/correcao pertence a respectiva funcionalidade de doses.

