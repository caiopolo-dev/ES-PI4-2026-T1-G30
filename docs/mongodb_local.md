# MongoDB local para desenvolvimento

Na raiz do repositório, execute no PowerShell:

```powershell
.\backend\scripts\start-mongodb.ps1
```

O script baixa o MongoDB Community 8.0.34 do endereço oficial, verifica o SHA256
e inicia o servidor em segundo plano. Não exige Docker nem cria um serviço do Windows.

- Endereço: `mongodb://127.0.0.1:27017`
- Banco previsto para desenvolvimento: `dosecerta_dev`
- Dados e logs: `.local/mongodb/`, ignorados pelo Git.

Esta instância não usa autenticação e aceita conexões somente no próprio computador.
É destinada a dados fictícios de desenvolvimento, sem compartilhamento pela internet.
O banco será criado quando o backend realizar a primeira gravação.

Após reiniciar o computador, execute o script novamente. Os dados permanecem no disco.
Para encerrar normalmente, conecte um cliente MongoDB ao endereço acima e execute
`db.getSiblingDB('admin').shutdownServer()`; aguarde o processo terminar.
O shell `mongosh` não está incluído neste preparo.

## Configuração do ambiente

Na raiz do repositório, execute no terminal em que iniciará o backend:

```powershell
. .\backend\scripts\set-local-env.ps1
```

O script define `MONGODB_URI=mongodb://127.0.0.1:27017` e
`MONGODB_DATABASE=dosecerta_dev` somente no terminal atual e nos processos
iniciados por ele. Execute novamente ao abrir outro terminal. Ele seleciona
explicitamente o ambiente local, substituindo essas duas variáveis nessa sessão.

`backend/.env.example` documenta as variáveis. Arquivos `.env` privados são
ignorados pelo Git; não existe carregamento automático desses arquivos no Java.
A classe `backend.config.MongoConnection` lê essas variáveis e confirma a conexão
com um `ping`. O cliente possui limites de espera de cinco segundos para seleção
do servidor, conexão e leitura. A URI não é incluída nas mensagens de erro da classe.

Exemplo de uso pela inicialização ou por um teste:

```java
try (MongoConnection connection = new MongoConnection()) {
    var users = connection.getCollection("users");
    // O repository utilizará a collection nas próximas etapas.
}
```

O cliente deve ser compartilhado durante a execução e fechado ao encerrar a
aplicação. A integração com o `Main` será feita na etapa de conexão das dependências.

## Testes da conexão e do modelo

Os testes unitários de configuração e de `User` não precisam do banco.
Com Maven e JDK 21 configurados, execute na raiz do repositório:

```powershell
mvn -f backend/pom.xml test
```

Para executar também os testes reais da conexão, inicie o MongoDB e habilite-os:

```powershell
. .\backend\scripts\set-local-env.ps1
$env:RUN_MONGODB_TESTS = 'true'
try {
    mvn -f backend/pom.xml test
} finally {
    Remove-Item Env:RUN_MONGODB_TESTS -ErrorAction SilentlyContinue
}
```

`MongoConnectionIntegrationTest` verifica ping, acesso à collection, encerramento
do cliente e erro de serviço indisponível. Não grava nem remove documentos.
Esses testes ainda não validam persistência de usuários: o repository será
implementado nas próximas etapas.
