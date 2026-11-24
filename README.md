# DoseCerta

**Gestão de tratamentos medicamentosos, agenda de doses e acompanhamento da adesão.**  
**Projeto Integrador IV — Engenharia de Software | PUC-Campinas | 2026**  
**Grupo 30 — Repositório:** [ES-PI4-2026-T1-G30](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30)  
**Planejamento:** [GitHub Projects — Quadro do projeto](https://github.com/users/caiopolo-dev/projects/4/views/2)

> **Estado do projeto (22/11/2026):** núcleo funcional integrado na branch `develop`, com interface React web responsiva, servidor HTTP próprio em Java e persistência MongoDB. Recursos complementares e a validação final de ponta a ponta possuem limitações descritas abaixo. Este repositório é um projeto acadêmico, não um produto médico homologado.

---

## Sumário

1. [Sobre o projeto](#sobre-o-projeto)
2. [Funcionalidades e escopo](#funcionalidades-e-escopo)
3. [Arquitetura e tecnologias](#arquitetura-e-tecnologias)
4. [Organização do repositório](#organização-do-repositório)
5. [Equipe e contribuições](#equipe-e-contribuições)
6. [Como executar do zero (Windows + PowerShell + Atlas)](#como-executar-do-zero-windows--powershell--atlas)
7. [Comandos rápidos para a apresentação](#comandos-rápidos-para-a-apresentação)
8. [Como utilizar e demonstrar](#como-utilizar-e-demonstrar)
9. [API HTTP](#api-http)
10. [Modelagem e persistência](#modelagem-e-persistência)
11. [Testes e qualidade](#testes-e-qualidade)
12. [Plataforma mobile e notificações](#plataforma-mobile-e-notificações)
13. [Limitações conhecidas e próximos passos](#limitações-conhecidas-e-próximos-passos)
14. [Solução de problemas](#solução-de-problemas)
15. [Documentação complementar](#documentação-complementar)

## Sobre o projeto

O **DoseCerta** foi idealizado para ajudar usuários a **organizar tratamentos medicamentosos**, identificar as doses previstas para cada dia, registrar se foram tomadas ou não e acompanhar indicadores de execução e pontualidade. A proposta busca reunir em uma experiência simples informações que frequentemente ficam dispersas entre prescrições, anotações e lembretes informais.

No fluxo principal, a pessoa se cadastra, cria um tratamento, associa um ou mais medicamentos, configura horários e ativa o tratamento. A aplicação gera ocorrências previstas, apresenta uma agenda e permite registrar explicitamente as ações **“Tomei”** (`TAKEN`) e **“Não tomei”** (`SKIPPED`). O histórico e os indicadores são calculados a partir dessas ocorrências e registros — não de números fictícios da interface.

O projeto também investiga **catálogo e preços de medicamentos** como uma frente complementar. A experiência atual de cadastro permite buscar apresentações de um **catálogo experimental** no MongoDB ou informar um medicamento manualmente. A consulta/comparação de preços na interface web ainda não integra o núcleo entregue na `develop` descrita neste README.

**Importante:** o DoseCerta não prescreve medicamentos, não determina doses e não substitui orientação médica ou farmacêutica. Os dados de administração são informados pelo próprio usuário.

## Funcionalidades e escopo

### Disponíveis no núcleo web

| Área | O que está implementado |
| --- | --- |
| Conta e sessão | Cadastro, login, recuperação da identidade da sessão e logout; autenticação por token Bearer. |
| Tratamentos | Criar e editar rascunhos (`DRAFT`), consultar, ativar (`ACTIVE`) e encerrar (`CLOSED`). |
| Medicamentos em uso | Adicionar, editar e desativar medicamentos vinculados a um tratamento; controle de revisão para edições concorrentes. |
| Catálogo experimental | Buscar apresentações em `medication_presentations` por princípio ativo/nome; selecionar uma apresentação ou cadastrar manualmente. |
| Cronogramas | Configurar horários diários ou por dias da semana, período, fuso e versões com vigência temporal. |
| Doses previstas | Geração/reconciliação de ocorrências com identidade estável, evitando duplicações. |
| Agenda | Consultar doses de uma data, horários e estados de apresentação. |
| Registro | Marcar explicitamente uma dose como tomada (`TAKEN`) ou não tomada (`SKIPPED`). |
| Histórico | Consultar ocorrências, ações e datas de um período, preservando informações históricas disponíveis. |
| Indicadores | Consultar execução/adesão e pontualidade a partir das doses elegíveis. |
| Interface | React com layout mobile-first, distribuído como arquivos estáticos pelo próprio servidor Java. |

### Limitações e funcionalidades complementares

- **Preços:** existem pesquisas, documentos, collections e desenvolvimento em branch/PR específico; **a interface React web da `develop` não disponibiliza comparação de preços funcional** nesta revisão. Não interpretar preços experimentais como cotações garantidas ou atualizadas.
- **Notificações:** há um módulo de lembretes **React Native** na pasta `mobile/`, mas ele não corresponde a um aplicativo nativo completo empacotado e conectado ao fluxo React web. Na interface web atual, os lembretes aparecem como indisponíveis.
- **Aplicativo instalável:** o frontend principal é uma **aplicação web responsiva**, não um APK/IPA e não um PWA instalável configurado.
- **Cadastro de medicamento:** a quantidade por administração é atualmente limitada a **inteiros positivos** pelo contrato Java; não assumir suporte a frações.
- **Catálogo:** registros de demonstração/amostra experimental; não é um cadastro nacional completo nem uma base de posologias recomendadas.
- **Conta:** recuperação de senha e edição avançada do perfil não foram concluídas no fluxo web.

## Arquitetura e tecnologias

O Java continua sendo o **servidor HTTP principal**. O frontend React **não** cria um backend paralelo e **não** acessa o MongoDB diretamente.

```text
Navegador (React + TypeScript)
            |
            | HTTP / JSON; HTML / JS / CSS
            v
  Servidor HTTP Java (porta 8080)
  Servidor -> ClienteHandler -> HttpRequestParser
                            -> Router e autenticação
                            -> Handlers
                            -> Services (regras de negócio)
                            -> Repositories
                                     |
                                     v
                                  MongoDB

  GET /, /assets/*, /favicon.svg -> StaticFileHandler
  Rotas /auth, /treatments, /doses, ... -> API Java
```

### Tecnologias

| Camada | Tecnologias e decisões |
| --- | --- |
| Backend | **Java** com código-alvo **21** e **Maven**; servidor HTTP implementado com `ServerSocket`, sem Spring Boot. |
| Protocolo | Parser de requisições, roteamento, autenticação e escrita de respostas HTTP próprios. |
| Persistência | **MongoDB** e MongoDB Java Driver síncrono. |
| Frontend principal | **React 19**, **TypeScript**, **Vite 8**, CSS e Tailwind CSS 4. |
| Documentação da API | **OpenAPI 3.1** (`docs/api/openapi.yaml`), além de relatórios das issues. |
| Testes Java | **JUnit** e **Mockito**, com testes unitários, de HTTP e integração condicional com MongoDB. |
| Módulo separado de lembretes | React Native, TypeScript, `expo-notifications` e AsyncStorage em `mobile/`. |
| Gestão | Git/GitHub, GitHub Issues, Pull Requests e GitHub Projects. |

### Como as camadas interagem

1. **`Servidor`** aceita conexões HTTP; **`ClienteHandler`** lê a requisição e devolve a resposta.
2. **`Router`** identifica a rota e aplica a política de autenticação. O `AuthContext` associa a operação ao usuário autenticado; o cliente não escolhe um `userId` arbitrário.
3. **Handlers** recebem e validam dados HTTP/JSON, delegando as ações.
4. **Services** implementam as regras de tratamentos, medicamentos, cronogramas, geração de doses, histórico e indicadores.
5. **Repositories** isolam as operações de armazenamento no MongoDB.
6. **`StaticFileHandler`** serve os arquivos do React compilado (`index.html`, JavaScript, CSS, ícones) pelo mesmo processo Java.

O Maven copia `frontend/dist` para `backend/target/classes/web` durante o build. **Node.js e pnpm são ferramentas de compilação do frontend, não processos necessários enquanto a aplicação final está rodando.**

### Regras importantes do domínio

- Tratamentos possuem estados **`DRAFT` → `ACTIVE` → `CLOSED`**. A ativação exige configurações válidas; um tratamento encerrado não pode ser reativado.
- A alteração de cronograma cria versões com intervalos de vigência. Registros já existentes não devem ser reconstruídos a partir da configuração atual.
- A geração de doses previstas busca ser **determinística e idempotente**; a reconciliação ajusta ocorrências futuras quando as configurações mudam.
- Uma dose sem registro não é automaticamente `SKIPPED`. `TAKEN` e `SKIPPED` representam ações explícitas.
- O indicador de execução considera doses elegíveis já vencidas; doses futuras e canceladas não entram indevidamente no denominador.
- Consultas de histórico e indicadores utilizam datas civis e referência temporal compatíveis com `America/Sao_Paulo`.

## Organização do repositório

```text
ES-PI4-2026-T1-G30/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/backend/
│       │   ├── Main.java
│       │   ├── auth/
│       │   ├── config/
│       │   ├── handlers/
│       │   ├── http/
│       │   ├── models/
│       │   ├── repositories/
│       │   ├── router/
│       │   ├── server/
│       │   └── services/
│       └── test/                     # Testes Java e fixtures
├── frontend/
│   ├── src/
│   │   ├── App.tsx
│   │   ├── Agenda.tsx
│   │   ├── Treatments.tsx
│   │   ├── Schedule.tsx
│   │   ├── History.tsx
│   │   ├── MedicationPresentationSearch.tsx
│   │   ├── api/client.ts
│   │   └── types/api.ts
│   ├── package.json
│   ├── pnpm-lock.yaml
│   └── vite.config.ts
├── mobile/                           # Módulo React Native de lembretes (separado)
├── docs/
│   ├── api/openapi.yaml
│   └── ...                            # Modelagem, relatórios e especificações
└── README.md
```

## Equipe e contribuições

Desenvolvido pelo **Grupo 30**, no âmbito do Projeto Integrador IV da PUC-Campinas. A divisão abaixo resume **responsabilidades e contribuições verificáveis** em issues atribuídas, commits, arquivos e PRs. Houve integração e trabalho coletivo; não significa autoria exclusiva de cada módulo.

| Integrante | Contribuições principais | Evidências no GitHub |
| --- | --- | --- |
| **Samuel Campovilla** — [@SamuelCampovilla](https://github.com/SamuelCampovilla) | Organização e especificação técnica do fluxo de implementação; parser/writer HTTP; ciclo de vida e CRUD de tratamentos; persistência/integração de doses; geração determinística e reconciliação; integração do frontend React/Figma ao servidor Java; busca de apresentações no cadastro; correções e testes integrados. | [#9](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/9), [#26](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/26), [#30](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/30), [#33](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/33), [PR #71](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/pull/71). |
| **Caio Ferreira Polo** — [@caiopolo-dev](https://github.com/caiopolo-dev) | Infraestrutura e ciclo de vida do servidor; modelagem física do MongoDB; cadastro, login e sessões; configuração/versionamento de cronogramas; regras de registro de doses; pesquisa e desenvolvimento do catálogo/aquisição de preços. | [#8](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/8), [#14](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/14), [#22](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/22), [#23](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/23), [#28](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/28), [#32](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/32), [#40](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/40). |
| **Miguel Batista Gallinucci** — [@miguelgallinucci](https://github.com/miguelgallinucci) | Conexão Java–MongoDB e `UserRepository`; autorização por titularidade; estrutura inicial de `MedicationService`; cadastro/edição/versionamento de medicamentos em uso; consulta e composição do histórico de doses; integração de snapshots e relatórios técnicos. | [#11](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/11), [#15](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/15), [#24](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/24), [#27](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/27), [#35](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/35), [PR #66](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/pull/66). |
| **Cauã Bianchi Ferroni** — [@caua98](https://github.com/caua98) | Roteamento e endpoints iniciais; persistência de tratamentos; API da agenda diária; cálculo de indicadores de adesão/pontualidade; implementação separada do módulo React Native de lembretes/notificações locais. | [#10](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/10), [#16](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/16), [#31](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/31), [#36](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/36), [#37](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/37), [PR #68](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/pull/68). |

As etapas de ideação, definição de regras, revisão de contratos e integração entre as frentes foram realizadas com colaboração da equipe. O design visual foi exportado do Figma e adaptado para React web; o histórico consultado não permite atribuir com segurança o desenho original a um único integrante.

Consulte também o [quadro de planejamento](https://github.com/users/caiopolo-dev/projects/4/views/2), as [issues](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues) e os [Pull Requests](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/pulls).

## Como executar do zero (Windows + PowerShell + Atlas)

O procedimento abaixo foi utilizado pela equipe para executar o **React compilado pelo servidor Java**, com banco **MongoDB Atlas**. Não é necessário executar `pnpm dev` nem manter um servidor Node.js separado.

### 1. Pré-requisitos

Instale ou tenha disponível:

- **Git** para clonar o repositório.
- **JDK 21 ou superior** com suporte a `--release 21` (projeto configurado para Java 21; o ambiente de demonstração também utilizou Temurin 25).
- **Maven 3.x**. No ambiente utilizado, o Maven está embutido no IntelliJ IDEA; pode-se usar uma instalação independente.
- **Node.js 22** e **pnpm 11.25.0** para compilar o frontend.
- **MongoDB Shell (`mongosh`)**, recomendado para testar a conexão.
- Uma instância **MongoDB Atlas** com usuário de banco e IP público autorizado na *Network Access List* (ou um servidor MongoDB local).

Verifique as ferramentas:

```powershell
java -version
node -v
pnpm -v
mongosh --version
```

Se `pnpm` não estiver disponível, com Node.js e npm instalados:

```powershell
npm install -g pnpm@11.25.0
pnpm -v
```

### 2. Clonar ou atualizar o projeto

Em um computador novo, onde ainda não existe `C:\Projetos\PI-IV`:

```powershell
New-Item -ItemType Directory -Force C:\Projetos | Out-Null
git clone https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30.git C:\Projetos\PI-IV
cd C:\Projetos\PI-IV
git switch develop
```

Em um checkout já existente, **com alterações locais importantes preservadas/commitadas**, faça:

```powershell
cd C:\Projetos\PI-IV
git status
git switch develop
git pull --ff-only origin develop
```

> O repositório possui também a branch `main`. Este README descreve o estado integrado da **`develop`**; não pressuponha que `main` contém as mesmas funcionalidades.

### 3. Configurar o MongoDB Atlas

1. No [MongoDB Atlas](https://cloud.mongodb.com/), selecione o projeto/cluster correspondente.
2. Em **Database Access**, confirme a existência de um usuário de banco com as permissões necessárias ao banco de desenvolvimento.
3. Em **Network Access**, adicione **seu IP público atual** à lista de acesso e aguarde a regra entrar em vigor. Não use o IP privado `192.168.x.x` apresentado pelo Windows.
4. Em **Connect → Drivers**, copie a string de conexão apropriada ao cluster. A senha deve ser codificada para URI quando contiver caracteres reservados.
5. Crie, **somente na máquina local**, o arquivo `C:\Projetos\PI-IV\backend\.env`:

```dotenv
# Exemplo — NÃO coloque credenciais reais no repositório
MONGODB_URI=mongodb+srv://USUARIO:SENHA@HOST_DO_CLUSTER/?retryWrites=true&w=majority
MONGODB_DATABASE=dosecerta_dev
```

O banco `dosecerta_dev` é o nome utilizado na demonstração; você pode escolher outro, desde que seja o banco pretendido. **A variável `MONGODB_DATABASE` determina explicitamente onde o backend grava os dados**, independentemente do nome do cluster.

**Segurança:** o arquivo `.env` contém segredos e não deve ser commitado nem compartilhado. O `.gitignore` da raiz já ignora arquivos `.env` e `.env.*` (com exceção de `.env.example`). Em um computador novo, crie o arquivo localmente — o Git não o transfere.

> O Java lê as configurações por `System.getenv(...)`: **não carrega `.env` automaticamente**. O passo de importação para o PowerShell abaixo é obrigatório para essa forma de execução.

### 4. Habilitar o Maven no PowerShell

No computador utilizado na apresentação, o Maven foi encontrado dentro do IntelliJ:

```powershell
cd C:\Projetos\PI-IV

$mavenBin = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin"
$env:Path = "$mavenBin;$env:Path"

mvn -version
```

O caminho acima **depende da versão e da instalação local do IntelliJ**. Quem já tem Maven instalado globalmente pode apenas executar `mvn -version`. Se necessário, localize o Maven na sua instalação de IntelliJ. O ajuste em `$env:Path` vale apenas para aquele processo PowerShell.

### 5. Instalar dependências e gerar o React

```powershell
cd C:\Projetos\PI-IV\frontend
pnpm install --frozen-lockfile
pnpm build
```

- `pnpm install --frozen-lockfile` instala dependências respeitando o `pnpm-lock.yaml`, sem modificar suas versões resolvidas.
- `pnpm build` compila React/TypeScript/Vite e gera `frontend/dist` com HTML, JS e CSS estáticos.
- **Não execute `pnpm dev`** para a demonstração integrada. O Vite só é opcional durante desenvolvimento visual.

### 6. Carregar as variáveis de `backend/.env`

Acesse a pasta do backend e confirme a existência do arquivo:

```powershell
cd C:\Projetos\PI-IV\backend
Test-Path .env
```

O resultado deve ser `True`. Depois execute **todo o bloco no mesmo terminal em que iniciará o Java**:

```powershell
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and !$line.StartsWith("#")) {
        $parts = $line -split "=", 2
        if ($parts.Length -eq 2) {
            $value = $parts[1].Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable(
                $parts[0].Trim(), $value, "Process"
            )
        }
    }
}
```

Esse carregador atende ao formato **simples** de `.env` (`CHAVE=valor`, uma variável por linha, comentários começando em `#`). Ele não implementa todos os recursos de bibliotecas dotenv, como expansão de variáveis ou valores multilinha.

Confira as configurações **sem imprimir a URI e a senha**:

```powershell
$env:MONGODB_DATABASE
$env:MONGODB_URI -match "^mongodb\+srv://"
```

O resultado esperado da segunda expressão é `True` quando a URI for SRV do Atlas. Para um MongoDB local (`mongodb://...`), esse teste é naturalmente `False`.

### 7. Confirmar a conexão com o Atlas

```powershell
mongosh "$env:MONGODB_URI" --eval "db.adminCommand({ ping: 1 })"
```

Se a conexão estiver correta, a resposta conterá `ok: 1`. Caso apareça erro TLS/SSL ou *server selection timeout*, verifique a **Network Access List**, o IP público atual, a URI, a conectividade e as credenciais. A autorização da porta TCP `27017` **não garante**, por si só, que o Atlas aceite a conexão TLS.

### 8. Compilar e iniciar o servidor Java

Na pasta `backend`, com o mesmo PowerShell e as variáveis já carregadas:

```powershell
mvn clean compile exec:java "-Dexec.mainClass=backend.Main"
```

Significado:

- `clean`: remove a saída anterior de compilação em `backend/target`;
- `compile`: compila os fontes Java para o alvo 21 e copia o conteúdo de `frontend/dist` para `backend/target/classes/web`;
- `exec:java`: executa `backend.Main`, que abre o servidor HTTP (porta **8080**, por padrão) e inicializa a conexão/serviços do MongoDB.

**Deixe este terminal aberto.** O processo Java serve tanto o React quanto as rotas da API. Não é preciso iniciar Vite, Express ou Spring Boot.

### 9. Confirmar a aplicação

Em outro PowerShell, com o Java rodando:

```powershell
Invoke-RestMethod http://localhost:8080/health
```

Resposta esperada:

```json
{"status":"ok"}
```

Abra **[http://localhost:8080](http://localhost:8080)** no navegador. O `GET /health` comprova atendimento HTTP; não é uma verificação completa da saúde do MongoDB a cada requisição.

### 10. Encerrar a aplicação

No terminal que executa o Java, pressione **Ctrl+C**. Para iniciar novamente, recarregue o `.env` se tiver aberto um novo PowerShell. Depois de alterar o frontend, execute `pnpm build` antes da recompilação do backend.

### Alternativa: MongoDB local

Se desejar usar o MongoDB local em vez do Atlas, mantenha o serviço local ativo e, no PowerShell que iniciará o Java, configure:

```powershell
$env:MONGODB_URI = "mongodb://127.0.0.1:27017"
$env:MONGODB_DATABASE = "dosecerta_local"
```

Nesse cenário, não é necessário o acesso à internet para o banco. Os dados do MongoDB local **não são automaticamente copiados para o Atlas** quando a URI muda.

## Comandos rápidos para a apresentação

Após a instalação das ferramentas e a criação de `backend/.env`, o roteiro completo de inicialização é:

```powershell
# 1. Entrar no projeto
cd C:\Projetos\PI-IV

# 2. Habilitar o Maven instalado no IntelliJ
$mavenBin = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin"
$env:Path = "$mavenBin;$env:Path"

# 3. Compilar React
cd frontend
pnpm install --frozen-lockfile
pnpm build

# 4. Entrar no backend
cd ..\backend

# 5. Carregar backend/.env
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and !$line.StartsWith("#")) {
        $parts = $line -split "=", 2
        if ($parts.Length -eq 2) {
            $value = $parts[1].Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable(
                $parts[0].Trim(), $value, "Process"
            )
        }
    }
}

# 6. Confirmar o banco e o tipo de URI
$env:MONGODB_DATABASE
$env:MONGODB_URI -match "^mongodb\+srv://"

# 7. Testar conexão ao Atlas
mongosh "$env:MONGODB_URI" --eval "db.adminCommand({ ping: 1 })"

# 8. Compilar e iniciar o Java
mvn clean compile exec:java "-Dexec.mainClass=backend.Main"
```

**URL:** `http://localhost:8080` — **encerramento:** `Ctrl+C` no terminal do Java.

## Como utilizar e demonstrar

Fluxo principal sugerido para uma demonstração com **dados fictícios**:

1. **Entrar:** cadastrar ou autenticar um usuário de demonstração.
2. **Criar um tratamento:** informar nome, data de início e se possui término previsto; inicialmente fica em `DRAFT`.
3. **Adicionar medicamentos:** pesquisar uma apresentação experimental, escolher uma opção e informar a **quantidade por administração**; experimentar também o cadastro manual.
4. **Configurar cronograma:** escolher frequência, dias e horários válidos, respeitando início, término e vigência.
5. **Ativar:** revisar e ativar o tratamento com os dados obrigatórios completos.
6. **Consultar agenda:** verificar as doses previstas de um dia e suas situações.
7. **Registrar:** marcar explicitamente `TAKEN` ou `SKIPPED` em ocorrências apropriadas.
8. **Conferir histórico e indicadores:** verificar que o sistema apresenta informações compatíveis com as ações e com a elegibilidade temporal.
9. **Testar persistência:** atualizar o navegador e reiniciar o Java; os registros devem continuar disponíveis no banco.
10. **Testar proteção:** autenticar um segundo usuário e comprovar que não tem acesso aos tratamentos do primeiro.

> Para uma demonstração previsível de `TAKEN` e `SKIPPED`, prepare **antecipadamente** ocorrências elegíveis de teste. As regras de vigência e geração não devem ser burladas alterando o relógio ou escrevendo registros artificiais no banco compartilhado.

## API HTTP

O Java registra os principais endpoints abaixo. Exceto `GET /health`, `POST /auth/register` e `POST /auth/login`, as rotas exigem autenticação conforme a política do backend. O token é enviado em `Authorization: Bearer <token>`.

| Método | Endpoint | Finalidade |
| --- | --- | --- |
| `GET` | `/health` | Verificação básica do processo HTTP. |
| `POST` | `/auth/register` | Cadastro de usuário. |
| `POST` | `/auth/login` | Login e emissão de token. |
| `GET` | `/auth/me` | Dados do usuário autenticado. |
| `POST` | `/auth/logout` | Encerramento da sessão. |
| `GET` | `/medications` | Catálogo legado de demonstração. |
| `GET` | `/medication-presentations?q=...` | Busca do catálogo experimental de apresentações no MongoDB. |
| `POST` | `/treatments` | Criar tratamento em rascunho. |
| `GET` | `/treatments` | Listar tratamentos do usuário. |
| `GET` | `/treatments/{id}` | Consultar um tratamento. |
| `PUT` | `/treatments/{id}` | Editar metadados do tratamento. |
| `POST` | `/treatments/{id}/activate` | Ativar tratamento. |
| `POST` | `/treatments/{id}/close` | Encerrar tratamento. |
| `POST` | `/treatments/{id}/medications` | Adicionar medicamento em uso. |
| `PUT` | `/treatments/{id}/medications/{medicationId}` | Editar medicamento em uso. |
| `DELETE` | `/treatments/{id}/medications/{medicationId}` | Desativar medicamento. |
| `GET` | `/treatments/{id}/medications/{medicationId}/schedule` | Consultar cronograma e suas versões. |
| `PUT` | `/treatments/{id}/medications/{medicationId}/schedule` | Criar/atualizar versão de cronograma. |
| `GET` | `/doses?date=AAAA-MM-DD` | Agenda diária. |
| `PUT` | `/doses/{id}/record` | Registrar `TAKEN` ou `SKIPPED`. |
| `GET` | `/adherence/history?from=AAAA-MM-DD&to=AAAA-MM-DD` | Histórico de doses por período. |
| `GET` | `/adherence/summary?from=AAAA-MM-DD&to=AAAA-MM-DD` | Indicadores de adesão e pontualidade. |

A documentação detalhada de payloads, revisões, datas e respostas está em **[`docs/api/openapi.yaml`](docs/api/openapi.yaml)**. Como o OpenAPI foi originalmente levantado de uma revisão anterior do código, ele deve ser revisado quando novos endpoints ou mudanças de contratos forem incorporados; os handlers Java são a fonte de verdade durante a execução.

### Exemplo de requisição autenticada

```http
GET /doses?date=2026-11-22 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <TOKEN_DA_SESSAO>
Accept: application/json
```

Datas de calendário usam `AAAA-MM-DD`; instantes são representados em ISO-8601. O fuso usual do domínio é `America/Sao_Paulo`.

## Modelagem e persistência

O backend usa MongoDB e mantém o usuário autenticado como titular das operações. Collections centrais:

```text
Banco configurado em MONGODB_DATABASE
├── users                 # Usuários e dados de autenticação (hash/salt, não senha em claro)
├── auth_sessions         # Sessões autenticadas
├── treatments            # Tratamentos e medicamentos/cronogramas embutidos
│   └── medications[]
│       └── schedules[]
├── planned_doses         # Ocorrências previstas, vinculadas ao tratamento
├── dose_records          # Ações explícitas TAKEN/SKIPPED
└── medication_presentations  # Catálogo experimental de apresentações
```

Collections complementares como `price_observations` e `price_collection_attempts` podem existir em bancos preparados para experimentos de preços; sua presença **não demonstra**, isoladamente, que o frontend já consome esse módulo.

Por convenção, o identificador de um documento de tratamento ou usuário é atribuído pelo backend. Medicamentos em uso ficam embutidos no tratamento, enquanto ocorrências e registros são armazenados separadamente para permitir consultas históricas e evitar crescimento ilimitado do documento do tratamento.

Para inspecionar os dados **sem expor hashes de senha**, após conectar ao banco de desenvolvimento apropriado:

```javascript
use dosecerta_dev
show collections
db.users.find({}, { name: 1, email: 1, _id: 1 })
db.treatments.find({}, { name: 1, status: 1, medications: 1 })
```

Não publique credenciais, tokens, dados pessoais ou capturas contendo registros clínicos reais.

## Testes e qualidade

### Java

```powershell
cd C:\Projetos\PI-IV\backend
mvn test
```

O repositório contém testes unitários e de HTTP para modelos, autorização, tratamentos, cronogramas, geração, registro, histórico, indicadores, catálogo e arquivos estáticos. **Alguns testes de integração com MongoDB são condicionais e podem ser ignorados** quando o ambiente de teste não foi preparado. Um resultado com testes ignorados não equivale à aprovação de todo o fluxo MongoDB.

Para suites de integração que alteram dados, utilize **uma instância/banco exclusivo de testes e as instruções específicas do respectivo teste**. **Não execute testes destrutivos no Atlas compartilhado `dosecerta_dev`.**

### Frontend web

```powershell
cd C:\Projetos\PI-IV\frontend
pnpm build
pnpm exec tsc --noEmit
```

### Módulo React Native de lembretes (independente)

A pasta `mobile/` possui testes específicos e dependências próprias. O relatório de implementação descreve a execução de `npm test` nesse módulo, mas isso **não** substitui testes da aplicação web ou validação física das notificações em um app instalado.

### Cenários mínimos de aceitação

- Cadastro, login, sessão expirada e logout.
- Usuário A não consegue consultar ou alterar dados do usuário B.
- Criar/retomar/ativar/encerrar tratamento; cadastro manual e por catálogo.
- Persistir e recuperar medicamentos, cronogramas e versões após reinício.
- Gerar/consultar doses sem duplicação após consultas repetidas.
- Registrar `TAKEN` e `SKIPPED` sem registros duplicados ou ações contraditórias silenciosas.
- Preservar histórico após alteração de cronograma; cancelar corretamente ocorrências futuras afetadas.
- Calcular indicadores sem contar doses futuras ou canceladas como faltas.
- Tratar erros de validação, conectividade e estado sem exibir sucesso falso.
- Testar interface em larguras móveis (por exemplo, 360, 390 e 430 px) e, quando possível, em celular real.

## Plataforma mobile e notificações

**A aplicação principal em `frontend/` é uma web app responsiva**, não um aplicativo React Native nativo. Seu layout mobile-first foi projetado para uso no celular, mas é servido pelo navegador e depende de um endereço HTTP acessível ao dispositivo.

**A pasta `mobile/` é uma frente separada:** contém implementações, componentes e testes React Native de lembretes locais. O código dessa pasta não representa, por si só, uma distribuição Android/iOS pronta para instalar, nem está automaticamente incorporado à interface React web. Consulte [`docs/relatorio_lembretes_react_native.md`](docs/relatorio_lembretes_react_native.md).

Para testar a web app em um celular na **mesma rede privada**, descubra o IP LAN do computador (`ipconfig`) e abra `http://IP_LOCAL_DO_COMPUTADOR:8080`, mantendo o Java ligado. Pode ser necessário permitir a porta no Firewall **somente na rede privada**. Esse acesso por HTTP não fornece as garantias de HTTPS necessárias para publicação pública.

## Limitações conhecidas e próximos passos

1. **Testes de ponta a ponta:** registrar evidências de uso real `React → Java → MongoDB`, incluindo persistência, segurança e cenários de falha; acompanhar as issues [#39](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/39), [#41](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/41) e [#42](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/42).
2. **Preços:** conciliar os módulos experimentais e o [PR #67](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/pull/67), ainda não incorporado na revisão consultada, com a API e a interface final. Não prometer preço em tempo real, estoque ou cobertura nacional.
3. **Notificações:** decidir oficialmente a plataforma de entrega, conectar/validar lembretes em ambiente suportado e documentar permissões e limitações. A [issue #37](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/37) ainda está aberta na revisão consultada.
4. **Aplicativo instalável:** se exigido, planejar PWA ou implementação/empacotamento React Native/Android/iOS; não confundir layout mobile com APK pronto.
5. **Segurança de implantação:** evitar exposição pública do servidor HTTP de demonstração sem HTTPS, controle de acesso à rede e revisão operacional de autenticação, logs e dados sensíveis.
6. **Documentação e contratos:** atualizar exemplos/OpenAPI quando o comportamento da API mudar; conferir a consistência de campos de medicamentos e cronogramas antes de ampliar o frontend.

## Solução de problemas

| Sintoma | Causa possível / ação recomendada |
| --- | --- |
| `mvn` não reconhecido | Verifique instalação de Maven ou adicione temporariamente o caminho `maven3/bin` do IntelliJ ao `$env:Path`. |
| `pnpm` não reconhecido | Com Node.js instalado, execute `npm install -g pnpm@11.25.0`. |
| `backend/.env` não encontrado | O arquivo deve existir em **`backend/`**, não necessariamente na raiz do repositório. |
| URI carregada mas Java usa outro banco | Verifique `MONGODB_DATABASE` e se as variáveis foram importadas **no mesmo terminal** do processo Java. |
| `SSLException` / `TLS alert internal_error` no Atlas | Verifique o IP público na **Network Access List**, conectividade e URI; confirme usando `mongosh` antes do Java. |
| `/health` funciona, mas `/` devolve 404 | Execute `pnpm build`, depois `mvn clean compile` para copiar `frontend/dist` ao classpath. |
| Porta 8080 ocupada | Pare a instância anterior (`Ctrl+C`), identifique o processo ou informe outra porta ao `Main` conforme documentação. |
| Login funciona, mas usuário não aparece no Compass | Confira o cluster e o banco exibido: `dosecerta_local` e `dosecerta_dev` são bancos diferentes. |
| Doses não aparecem no dia esperado | Confira o estado do tratamento, a vigência do cronograma, horários, fuso e janela de geração; não insira ocorrências artificialmente. |
| Alteração de cronograma responde `409` | Verifique revisão otimista e compatibilidade do início de vigência com as regras do tratamento. |
| Testes Java exibem `skipped` | Muitos cenários exigem MongoDB/configuração de teste; consulte a documentação do módulo antes de habilitá-los. |

## Documentação complementar

- [Contrato OpenAPI (`docs/api/openapi.yaml`)](docs/api/openapi.yaml)
- [Contratos React–Java (`docs/api/CONTRATOS_API_REACT.md`)](docs/api/CONTRATOS_API_REACT.md)
- [Modelagem física MongoDB](docs/modelagem_fisica_mongoDB.md)
- [Autorização por titularidade — Issue #24](docs/autorizacao_issue_24.md)
- [Medicamentos em uso — Issue #27](docs/issue_27_endpoints.md)
- [Tratamentos — Issue #26](docs/relatorio_issue_26_tratamentos.md)
- [Histórico — Issue #35](docs/issue_35_contrato.md)
- [Agenda diária](docs/relatorio_agenda_diaria.md)
- [Indicadores de adesão/pontualidade](docs/relatorio_adesao_pontualidade.md)
- [Módulo React Native de lembretes](docs/relatorio_lembretes_react_native.md)
- [GitHub Issues](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues)
- [GitHub Projects](https://github.com/users/caiopolo-dev/projects/4/views/2)

---

**Desenvolvimento acadêmico — Grupo 30, Engenharia de Software, PUC-Campinas, 2026.**

*Este projeto organiza informações fornecidas pelo usuário e registra eventos; não é um dispositivo médico nem fornece prescrição, diagnóstico ou recomendações de dosagem.*
