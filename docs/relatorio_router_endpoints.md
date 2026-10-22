# Relatório de Implementação: Router e Endpoints Iniciais

**Projeto:** DoseCerta (`ES-PI4-2026-T1-G30`)  
**Branch:** `implementation/initial_endpoints`  
**Escopo:** Mecanismo de roteamento HTTP e handlers iniciais em Java puro (`#10`)

---

## 1. Objetivo

Implementar o mecanismo de roteamento HTTP (`Router`) e os endpoints iniciais (`/health` e `/medications`) do backend do **DoseCerta** utilizando apenas **Java puro** (sem frameworks web externos).

O foco desta entrega é:
- Decidir qual componente (`HttpHandler`) deve processar cada requisição com base no **método HTTP** e no **caminho (`path`)** recebidos;
- Separar a infraestrutura de rede (`ServerSocket` / `Socket`) da lógica dos endpoints e regras de negócio;
- Garantir o tratamento padronizado de rotas inexistentes (`404 Not Found`) e métodos HTTP não permitidos (`405 Method Not Allowed`).

---

## 2. Fluxo de Execução e Arquitetura

O fluxo implementado segue a arquitetura em camadas definida para o servidor:

```text
HttpRequest (method + path)
        │
        ▼
      Router
        │
        ├── Rota não cadastrada? ────────────► HttpResponse (404 Not Found)
        │
        ├── Método não permitido na rota? ───► HttpResponse (405 Method Not Allowed)
        │
        ▼
   HttpHandler (HealthHandler / MedicationHandler)
        │
        ▼
   HttpResponse (200 OK + JSON Body)
```

### Separação de Responsabilidades
- **`Router`:** Apenas consulta a tabela de rotas registradas e delega a execução para o handler correspondente. Não abre sockets, não lê `InputStream`, não escreve em `OutputStream` e não contém regras de negócio.
- **`HttpHandler`:** Funciona como uma interface de fronteira entre o protocolo HTTP e os serviços de domínio da aplicação.
- **`HttpRequest` / `HttpResponse`:** Objetos de transferência que representam os dados da requisição e da resposta de forma independente de rede.

---

## 3. Estrutura de Arquivos Criados

```text
backend/src/
├── Main.java                        # Ponto de entrada com registro de rotas e bateria dos 4 cenários mínimos
├── handlers/
│   ├── HealthHandler.java           # Endpoint GET /health -> 200 OK {"status":"ok"}
│   └── MedicationHandler.java       # Endpoint GET /medications -> 200 OK (preparado para o MedicationService)
├── http/
│   ├── HttpRequest.java             # Modelo de requisição HTTP (method, path, httpVersion, headers, body)
│   └── HttpResponse.java            # Modelo de resposta HTTP (statusCode, reasonPhrase, headers, body)
└── router/
    ├── HttpHandler.java             # Interface funcional: HttpResponse handle(HttpRequest request)
    └── Router.java                  # Registro centralizado e roteamento por método + path
```

### Detalhamento dos Componentes

1. **`HttpHandler` (`backend/src/router/HttpHandler.java`)**
   - Define o contrato único `HttpResponse handle(HttpRequest request)`.
   - Anotada com `@FunctionalInterface`, permitindo tanto classes dedicadas quanto expressões lambda.

2. **`Router` (`backend/src/router/Router.java`)**
   - Utiliza a estrutura `Map<String, Map<String, HttpHandler>>` (`path -> (method -> handler)`).
   - **Método `register(String method, String path, HttpHandler handler)`:** Permite registrar rotas de forma declarativa e centralizada, evitando blocos de `if / else if` espalhados pelo servidor.
   - **Método `route(HttpRequest request)`:**
     1. Busca o mapa de métodos associado ao `path`. Se não existir, retorna `404 Not Found`.
     2. Busca o `HttpHandler` associado ao `method` HTTP dentro daquele `path`. Se não existir, retorna `405 Method Not Allowed`.
     3. Executa `handler.handle(request)` e devolve o `HttpResponse`.

3. **`HealthHandler` (`backend/src/handlers/HealthHandler.java`)**
   - Implementa `HttpHandler`.
   - Responde a `GET /health` com status `200 OK` e corpo JSON `{"status":"ok"}`.

4. **`MedicationHandler` (`backend/src/handlers/MedicationHandler.java`)**
   - Implementa `HttpHandler`.
   - Responde a `GET /medications` com status `200 OK` e lista JSON de medicamentos, já estruturado para receber a injeção do `MedicationService`.

5. **`HttpRequest` e `HttpResponse` (`backend/src/http/`)**
   - Representam os dados estruturados da requisição (`method`, `path`, `httpVersion`, `headers`, `body`) e da resposta (`statusCode`, `reasonPhrase`, `headers` com `Content-Type` e `Content-Length`, `body`), prontos para integração com o `HttpRequestParser` e `HttpResponseWriter`.

---

## 4. Validação dos Cenários Mínimos

Todos os 4 cenários mínimos especificados na tarefa foram validados através da execução da classe `Main`:

| Cenário | Requisição | Handler Acionado | Status Esperado | Status Obtido | Corpo Retornado (`Body`) |
| :---: | :--- | :--- | :---: | :---: | :--- |
| **1** | `GET /health` | `HealthHandler` | `200` | **`200 OK`** | `{"status":"ok"}` |
| **2** | `GET /medications` | `MedicationHandler` | `200` | **`200 OK`** | `[{"id":"1","name":"Losartana 50 mg"},{"id":"2","name":"Sinvastatina 20 mg"}]` |
| **3** | `GET /unknown` | *(Nenhum)* | `404` | **`404 Not Found`** | `{"error":"Route not found: /unknown"}` |
| **4** | `POST /health` | *(Nenhum)* | `405` | **`405 Method Not Allowed`** | `{"error":"Method POST not allowed for route /health"}` |

### Saída da Execução no Terminal

```text
=== CENARIO 1: GET /health -> HealthHandler -> 200 ===
Requisicao: GET /health
Status retornado: 200 OK
Body: {"status":"ok"}

=== CENARIO 2: GET /medications -> MedicationHandler -> 200 ===
Requisicao: GET /medications
Status retornado: 200 OK
Body: [{"id":"1","name":"Losartana 50 mg"},{"id":"2","name":"Sinvastatina 20 mg"}]

=== CENARIO 3: GET /unknown -> 404 ===
Requisicao: GET /unknown
Status retornado: 404 Not Found
Body: {"error":"Route not found: /unknown"}

=== CENARIO 4: POST /health -> 405 ===
Requisicao: POST /health
Status retornado: 405 Method Not Allowed
Body: {"error":"Method POST not allowed for route /health"}
```

---

## 5. Como Compilar e Executar

Na raiz do repositório (`ES-PI4-2026-T1-G30`), execute os comandos abaixo no terminal:

```powershell
# 1. Compilar as classes
javac backend/src/http/*.java backend/src/router/*.java backend/src/handlers/*.java backend/src/Main.java

# 2. Executar a validação dos cenários
java backend.src.Main
```

---

## 6. Checklist (Definition of Done)

- [x] Existir um `Router` funcional;
- [x] Rotas puderem ser registradas centralizadamente (`router.register(...)`);
- [x] `GET /health` chegar ao `HealthHandler` e retornar `200 OK`;
- [x] `GET /medications` chegar ao `MedicationHandler` e retornar `200 OK`;
- [x] Rota inexistente (`GET /unknown`) retornar `404 Not Found`;
- [x] Método inadequado (`POST /health`) retornar `405 Method Not Allowed`;
- [x] Adicionar uma nova rota não exigir alterar o loop de conexões do servidor;
- [x] `Router` e handlers não dependerem diretamente de `ServerSocket`.

