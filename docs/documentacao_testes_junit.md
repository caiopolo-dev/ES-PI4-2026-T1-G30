# Documentação de Testes em JUnit 5: Router e Endpoints Iniciais

**Projeto:** DoseCerta (`ES-PI4-2026-T1-G30`)  
**Branch:** `implementation/initial_endpoints`  
**Classe de Teste:** [`backend/src/tests/RouterTest.java`](../backend/src/tests/RouterTest.java)  
**Framework de Testes:** JUnit 5 (JUnit Jupiter)

---

## 1. Objetivo dos Testes

Validar de forma automatizada e isolada (testes unitários) o comportamento do mecanismo de roteamento HTTP (`Router`), dos handlers iniciais (`HealthHandler` e `MedicationHandler`) e da estrutura de resposta (`HttpResponse`), garantindo que todos os **cenários mínimos** e **casos de borda** da especificação sejam atendidos sem regressões.

---

## 2. Estrutura da Suíte de Testes (`RouterTest.java`)

A classe `RouterTest` utiliza as anotações e asserções do **JUnit Jupiter (`org.junit.jupiter.api.*`)**:

- **`@BeforeEach` (`setUp()`):** Executado antes de cada método `@Test`, criando uma nova instância limpa de `Router` e registrando as rotas padrão:
  - `GET /health` $\rightarrow$ `HealthHandler`
  - `GET /medications` $\rightarrow$ `MedicationHandler`
- **`@Test` e `@DisplayName`:** Identificam e documentam o propósito de cada caso de teste.
- **`Assertions` (`assertEquals`, `assertNotNull`, `assertTrue`):** Verificam automaticamente o código de status HTTP (`statusCode`), a frase de razão (`reasonPhrase`), os cabeçalhos (`headers`) e o corpo (`body`) de cada resposta.

---

## 3. Casos de Teste Implementados

| # | Método de Teste (`@Test`) | Entrada (`Method` + `Path`) | Resultado Esperado | Status |
| :---: | :--- | :--- | :--- | :---: |
| **1** | `shouldReturn200ForGetHealth` | `GET /health` | Status `200 OK`, body `{"status":"ok"}` e header `Content-Type: application/json; charset=utf-8` | **PASS** |
| **2** | `shouldReturn200ForGetMedications` | `GET /medications` | Status `200 OK` e JSON contendo `Losartana 50 mg` e `Sinvastatina 20 mg` | **PASS** |
| **3** | `shouldReturn404ForUnknownRoute` | `GET /unknown` | Status `404 Not Found` e body `{"error":"Route not found: /unknown"}` | **PASS** |
| **4** | `shouldReturn405ForUnsupportedMethodOnExistingRoute` | `POST /health` | Status `405 Method Not Allowed` e body `{"error":"Method POST not allowed for route /health"}` | **PASS** |
| **5** | `shouldHandleCaseInsensitiveHttpMethod` | `get /health` | Normaliza o método para maiúsculas (`GET`) e retorna `200 OK` com `{"status":"ok"}` | **PASS** |
| **6** | `shouldSupportMultipleMethodsOnSamePath` | `GET /medications` e `POST /medications` | Permite múltiplos métodos HTTP na mesma rota sem que um sobrescreva o outro (`200` e `201`) | **PASS** |
| **7** | `shouldCalculateCorrectContentLengthHeader` | `GET /health` | Calcula automaticamente o header `Content-Length` em bytes UTF-8 (`"15"` para `{"status":"ok"}`) | **PASS** |

---

## 4. Resultado da Execução dos Testes

Abaixo está o log real gerado pela execução da suíte `RouterTest`:

```text
=================================================================
 EXECUTANDO SUITE DE TESTES JUNIT 5: RouterTest
=================================================================
[PASS] shouldReturn200ForGetHealth -> Cenário 1: Deve retornar 200 OK e status ok ao acessar GET /health
[PASS] shouldReturn200ForGetMedications -> Cenário 2: Deve retornar 200 OK e a lista de medicamentos ao acessar GET /medications
[PASS] shouldReturn404ForUnknownRoute -> Cenário 3: Deve retornar 404 Not Found ao acessar uma rota inexistente (GET /unknown)
[PASS] shouldReturn405ForUnsupportedMethodOnExistingRoute -> Cenário 4: Deve retornar 405 Method Not Allowed quando o método HTTP não for suportado (POST /health)
[PASS] shouldHandleCaseInsensitiveHttpMethod -> Cenário 5: Deve tratar o método HTTP de forma case-insensitive (ex: 'get' -> 'GET')
[PASS] shouldSupportMultipleMethodsOnSamePath -> Cenário 6: Deve permitir registrar múltiplos métodos HTTP para o mesmo path sem conflito
[PASS] shouldCalculateCorrectContentLengthHeader -> Cenário 7: Deve calcular corretamente o header Content-Length na HttpResponse
-----------------------------------------------------------------
Resultado final: 7 passaram, 0 falharam (Total: 7)
=================================================================
```

---

## 5. Como Executar os Testes

### Opção 1: Pela IDE (VS Code / IntelliJ IDEA)
Abra o arquivo [`backend/src/tests/RouterTest.java`](../backend/src/tests/RouterTest.java) e clique no ícone de execução (**Run Test**) ao lado da declaração da classe `RouterTest` ou de cada método `@Test`.

### Opção 2: Via Terminal (PowerShell) utilizando os JARs do JUnit 5 no repositório local (`.m2`)

```powershell
$cp = ".;$env:USERPROFILE\.m2\repository\org\junit\jupiter\junit-jupiter-api\6.0.3\junit-jupiter-api-6.0.3.jar;$env:USERPROFILE\.m2\repository\org\junit\platform\junit-platform-commons\6.0.3\junit-platform-commons-6.0.3.jar;$env:USERPROFILE\.m2\repository\org\opentest4j\opentest4j\1.3.0\opentest4j-1.3.0.jar"

javac -cp $cp backend/src/http/*.java backend/src/router/*.java backend/src/handlers/*.java backend/src/tests/RouterTest.java
java -cp $cp backend.src.tests.RouterTest
```

### Opção 3: Via Maven (após integração com a branch `develop`)
Quando integrado à estrutura Maven da branch `develop` (`backend/pom.xml`):
```powershell
cd backend
mvn test
```
