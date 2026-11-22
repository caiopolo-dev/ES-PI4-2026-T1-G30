# Instrucoes de integracao — DoseCerta React + Java

## Origem e integridade

Este ZIP e uma **copia preparada** do arquivo original `DoseCerta mobile prototype.zip` (exportacao Figma Make).
`frontend/src/App.tsx` e `frontend/src/index.css` foram copiados **byte a byte, sem alteracao**. `frontend/src/main.tsx`, `public/favicon.svg`, `package.json`, `pnpm-lock.yaml` e `tsconfig.json` tambem foram mantidos.

As alteracoes de preparacao sao **apenas de scaffold**, nao de logica de negocio:
- Os arquivos de codigo estao sob `frontend/` para facilitar a incorporacao ao repositorio.
- `frontend/vite.config.ts` foi simplificado para plugins React/Tailwind e proxy local para o Java em `127.0.0.1:8080`.
- `frontend/index.html` ganhou titulo, idioma e favicon comuns (sem marcadores Figma).
- `frontend/.gitignore` foi reduzido a ignores usuais.
- O pacote **nao inclui** `.figma/`, `AGENTS.md`, `CLAUDE.md`, `.mise.toml` e `.gitattributes` exportados do ambiente Figma Make: eram metadados e instrucoes da plataforma, nao requisitos do produto.

**Importante:** nenhum build ou teste foi executado nesta copia. Valide as dependencias e o build no ambiente de desenvolvimento.

## Regras inegociaveis para o agente

1. Trabalhe em **branch nova criada da `develop` atual**, nao altere diretamente `develop`.
2. Extraia somente `frontend/` na raiz do repositorio; **nao versione o ZIP**, `node_modules`, `dist`, secrets ou tokens. Remova o ZIP da raiz depois da importacao.
3. **Java 21 continua sendo servidor HTTP principal, inclusive em producao.** Nao criar backend Node/Express, nao migrar services, repositories ou regras de negocio para React.
4. Todas as operacoes persistidas devem usar a API Java existente e o MongoDB permanece acessado apenas pelo Java.
5. A interface deve manter o design de `App.tsx` e `index.css`, salvo alteracoes visuais indispensaveis, explicadas e justificadas.
6. Vite e ferramenta de desenvolvimento e build. Durante desenvolvimento usa proxy para `http://127.0.0.1:8080`; em producao o Java deve servir `frontend/dist` com tratamento de arquivos estaticos e `Content-Type` correto.
7. Preservar URLs HTTP atuais **sem introduzir `/api`** (`/auth`, `/treatments`, `/doses`, `/adherence`, `/medications`, `/health`).
8. Antes de cada alteracao Java, justifique o minimo necessario; nao mexa em `TreatmentMutationLock`, geracao de doses, dose records nem MongoDB sem bug comprovado.
9. Todo dado de negocio deve vir da API; nao simular sucesso de login, registro ou tratamento. Precos e notificacoes sem backend real devem ficar explicitamente indisponiveis.
10. Leia o arquivo `PLANO_INTEGRACAO_DOSECERTA_REACT_JAVA.md` e `docs/api/openapi.yaml` (se disponiveis). **O codigo da `develop` e a fonte final da verdade** quando houver divergencia.
11. Execute a integracao em fases, com commits isolados e relatorio de evidencias. Comece importando o frontend e validando a compilacao; depois permita que o Java sirva o React estatico; so entao conecte login e as demais telas.

## Ordem minima das fases

- A: importar `frontend/`, instalar dependencias pelo lockfile e validar `pnpm build`.
- B: adaptar minimamente o Java para servir estaticos do React compilado (incluindo bytes binarios, MIME, content-length e regras de autenticacao). Testar GET `/`, JS, CSS, favicon, 404 e APIs protegidas.
- C: implementar `frontend/src/api/client.ts`, tipos e fluxo de login/cadastro/logout autenticado.
- D: conectar tratamentos, medicamentos e cronogramas reais.
- E: conectar agenda, registro `TAKEN`/`SKIPPED`, historico e indicadores.
- F: testar o fluxo completo; registrar lacunas de backend (precos, notificacoes, unidades historicas etc.).

**Pare e reporte conflitos ou requisitos adicionais em vez de alterar silenciosamente contratos dos handlers Java.**
