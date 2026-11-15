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

