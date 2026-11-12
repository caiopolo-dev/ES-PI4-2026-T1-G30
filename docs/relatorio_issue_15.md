# Issue #15 - Conexão MongoDB e persistência de usuários

## Objetivo

Conectar o backend ao MongoDB pelo driver oficial e permitir salvar e consultar
usuários, mantendo a comunicação com o banco separada do serviço.

O trabalho aproveita a dependência `mongodb-driver-sync` 5.13.0 e o teste
`MongoConnectionTest` adicionados à `develop`. O teste original foi preservado.

## O que foi implementado

| Componente | Responsabilidade |
|---|---|
| `MongoConnection` | Ler as variáveis de ambiente, criar o cliente, selecionar o banco, confirmar o acesso com ping e fechar os recursos. |
| `User` | Representar os oito campos do usuário definidos na modelagem. |
| `UserRepository` | Definir `save()`, `findById()` e `findByEmail()`. |
| `UserDocumentMapper` | Converter `User` e `Document`, validando campos obrigatórios e tipos. |
| `MongoUserRepository` | Inserir e consultar documentos na collection fornecida pela conexão. |
| `UserPersistenceException` | Classificar falhas de persistência sem repassar mensagens internas do driver. |
| `UserService` | Usar o repository recebido pelo construtor, sem conhecer MongoDB ou HTTP. |
| `Main` | Montar as dependências e manter a conexão aberta enquanto o servidor executa. |

O `Main` conecta o banco, cria o repository da collection `users` e instancia o
serviço antes de abrir o servidor HTTP. O serviço está preparado para futuros
handlers; nenhuma rota de usuários foi adicionada nesta issue.

```text
Inicialização: Main -> MongoConnection -> MongoUserRepository -> UserService

Persistência: UserService -> UserRepository -> MongoUserRepository -> MongoDB
```

## Dados e regras de persistência

Os campos da collection `users` são:

| Campo | Tipo |
|---|---|
| `_id` | String, correspondente a `User.id` |
| `name` | String |
| `email` | String |
| `emailNormalized` | String |
| `passwordHash` | String |
| `passwordSalt` | String |
| `passwordAlgorithm` | String |
| `passwordIterations` | Int32 positivo |

Os identificadores são recebidos pelo modelo; o repository não gera IDs.
O método `save()` insere um documento novo, sem substituir um usuário existente.
As buscas retornam `Optional.empty()` quando não encontram o usuário.

O e-mail de consulta é normalizado com `trim()` e `toLowerCase(Locale.ROOT)`.
Ao salvar, `emailNormalized` deve corresponder ao resultado dessa mesma regra.
O campo `email` preserva o valor recebido.

O repository cria o índice único `users_email_normalized_unique` sobre
`emailNormalized`. A unicidade é garantida pelo MongoDB, inclusive em inserções
simultâneas. A inicialização não apaga nem corrige dados antigos duplicados.

O modelo recebe hash e parâmetros da senha já preparados. Não existe geração ou
verificação de senha nesta entrega. Os valores fictícios e as 600000 iterações
dos testes não representam uma configuração definitiva de autenticação.

## Tratamento de erros

| Situação | Resultado |
|---|---|
| Campo de entrada nulo, vazio ou inconsistente | `IllegalArgumentException` |
| Usuário não encontrado | `Optional.empty()` |
| ID ou e-mail duplicado | `UserPersistenceException`, motivo `DUPLICATE_USER` |
| Documento recuperado incompleto ou com tipo inválido | Motivo `INVALID_DOCUMENT` |
| Timeout, falha de socket ou cliente encerrado | Motivo `DATABASE_UNAVAILABLE` |
| Outras falhas do driver no repository | Motivo `DATABASE_ERROR` |

As mensagens do repository não incluem valores pessoais, mensagens ou causas do
driver. Na inicialização, configuração ausente e falhas de conexão são reportadas
por `IllegalStateException`, sem expor a URI.

A conexão possui limites de cinco segundos para seleção do servidor, conexão e
leitura. Esses limites não significam que toda operação terminará em cinco segundos.
O cliente é fechado ao sair do bloco de execução ou no encerramento com `Ctrl+C`.

## Requisitos para executar

- JDK 21, com `java` e `javac` no PATH.
- Maven, com `mvn` no PATH.
- MongoDB acessível e usuário com permissão de leitura, escrita e criação do índice.
- Internet no primeiro download das dependências Maven.

Confira em um novo terminal PowerShell:

```powershell
java -version
javac -version
mvn -version
```

Os exemplos seguintes devem ser executados na raiz do repositório.

## Configurar o ambiente

Para desenvolvimento local, com MongoDB já iniciado em `127.0.0.1:27017`:

```powershell
$env:MONGODB_URI = 'mongodb://127.0.0.1:27017'
$env:MONGODB_DATABASE = 'dosecerta_dev'
```

As variáveis valem para o terminal atual e os processos iniciados por ele. No VS
Code, a configuração do ambiente de execução também pode ser usada.

Para utilizar o Atlas da equipe, substitua a URI local pela URI fornecida pelo
Atlas, utilizando suas próprias credenciais, e confirme o nome do banco. Também
é necessário autorizar o IP e as permissões do usuário de banco no Atlas.

Credenciais não devem ser incluídas no código, nesta documentação ou no Git.
Arquivos `.env` e `.env.*` são ignorados pelo repositório, mas não são carregados
automaticamente pela aplicação: os valores precisam estar no ambiente do Java.
O carregador de configuração da implementação anterior não faz parte desta versão.

## Compilar e iniciar o servidor

```powershell
mvn -f backend/pom.xml package dependency:copy-dependencies "-DincludeScope=runtime"
if ($LASTEXITCODE -eq 0) {
    java -cp "backend/target/classes;backend/target/dependency/*" backend.Main
}
```

A porta padrão é `8080`. Para utilizar `8081`:

```powershell
java -cp "backend/target/classes;backend/target/dependency/*" backend.Main 8081
```

Em outro terminal:

```powershell
curl.exe -i http://127.0.0.1:8081/health
curl.exe -i http://127.0.0.1:8081/medications
```

As rotas devem responder `200 OK`. `/health` confirma que o servidor HTTP está
atendendo; o ping ao MongoDB é realizado na inicialização, não em cada chamada.
`/medications` continua consultando os medicamentos em memória, não a collection
de usuários.

Iniciar o `Main` também cria ou verifica o índice único na collection `users`.
Portanto, a inicialização não é uma operação estritamente de leitura do banco.

## Executar os testes

### Testes JUnit existentes

```powershell
mvn -f backend/pom.xml test
```

Na execução registrada da etapa 10, os 13 testes JUnit do parser e writer passaram.

### Verificações executáveis por main

Os testes abaixo possuem `main()` próprio e não são executados automaticamente
pelo comando `mvn test`. Depois de compilar, prepare o classpath:

```powershell
mvn -f backend/pom.xml test dependency:copy-dependencies "-DincludeScope=runtime"
$testClasspath = 'backend/target/classes;backend/target/test-classes;backend/target/dependency/*'
```

Sem necessidade de banco real:

```powershell
java -cp $testClasspath backend.tests.UserDocumentMapperTest
java -cp $testClasspath backend.tests.UserServiceTest
java -cp $testClasspath backend.tests.MongoConnectionValidationTest
```

O último teste também utiliza uma porta local reservada, sem MongoDB, para
verificar a falha de conexão. A opção `--integration` habilita uma verificação
adicional de ping usando as variáveis do ambiente, sem gravar documentos.

Com MongoDB local em execução em `127.0.0.1:27017`:

```powershell
java -cp $testClasspath backend.tests.MongoUserRepositoryTest
java -cp $testClasspath backend.tests.UserEmailUniquenessTest
java -cp $testClasspath backend.tests.UserPersistenceErrorTest
java -cp $testClasspath backend.tests.UserPersistenceIntegrationTest
```

Esses quatro testes usam endereço local fixo e nomes de banco aleatórios exclusivos
de cada execução. Os bancos são removidos ao final, inclusive o validador
temporário criado para testar uma escrita rejeitada. Eles não utilizam a URI do Atlas.
Use somente uma instância local de desenvolvimento, com permissão para criar e
remover os bancos temporários.

Cada programa termina com erro se uma verificação falhar. Não é necessário usar
`-ea`, pois as verificações lançam `AssertionError` explicitamente.

## Resultados registrados em 12/11/2026

Na etapa 10, o build Maven, os 13 testes JUnit e os sete programas de verificação
acima passaram. Foram verificados:

- Conversão e preservação dos oito campos do usuário.
- Rejeição de configuração ausente, URI inválida e servidor indisponível.
- Serviço utilizando o contrato recebido no construtor.
- Gravação real e buscas por ID e e-mail normalizado.
- Recuperação após fechar e recriar a conexão, o repository e o serviço.
- Usuário inexistente retornando vazio.
- E-mail único, incluindo duas inserções simultâneas.
- ID duplicado e documento armazenado inválido.
- Conexão encerrada e erro de validação do banco tratados sem expor detalhes internos.

Na etapa 9, o teste HTTP existente de medicamentos também passou com servidor real.
O teste de persistência desta entrega recria conexões no mesmo processo; ele não
reinicia o MongoDB, não simula queda de energia e não valida produção.

## Problemas comuns

| Sintoma | Verificação |
|---|---|
| Java 8 ou `javac` não encontrado | Configurar JDK 21 no PATH e reabrir o terminal. |
| `mvn` não encontrado | Configurar Maven no PATH. |
| Variável não configurada | Definir o ambiente no mesmo terminal em que o backend será iniciado. |
| Falha de conexão | Conferir URI, servidor, IP permitido e credenciais/permissões. |
| `Address already in use` | Encerrar o servidor anterior ou escolher outra porta HTTP. |
| Falha ao criar o índice único | Conferir dados duplicados e índices existentes; não remover dados da equipe automaticamente. |
| Aviso sobre SLF4J | O provedor de logging do driver não foi configurado; nas verificações registradas, o aviso não impediu a conexão. |

## Fora do escopo

Cadastro HTTP, login, hashing de senha, sessões, autorização, persistência de
tratamentos/doses, módulo de preços e deploy não fazem parte da issue #15.
A criação do serviço não adiciona esses comportamentos.

Esta documentação não representa commit, push, PR, merge ou encerramento da issue.
