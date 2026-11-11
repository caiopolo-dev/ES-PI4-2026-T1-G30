package backend.src.tests;

import backend.src.handlers.HealthHandler;
import backend.src.handlers.MedicationHandler;
import backend.src.http.HttpRequest;
import backend.src.http.HttpResponse;
import backend.src.router.Router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suíte de testes unitários em JUnit 5 para validar o Router, os endpoints iniciais
 * (HealthHandler e MedicationHandler) e as respostas HTTP.
 */
public class RouterTest {

    private Router router;

    @BeforeEach
    void setUp() {
        router = new Router();
        router.register("GET", "/health", new HealthHandler());
        router.register("GET", "/medications", new MedicationHandler());
    }

    // =========================================================================
    // Cenário 1: GET /health -> HealthHandler -> 200 OK
    // =========================================================================
    @Test
    @DisplayName("Cenário 1: Deve retornar 200 OK e status ok ao acessar GET /health")
    void shouldReturn200ForGetHealth() {
        HttpRequest request = new HttpRequest("GET", "/health");

        HttpResponse response = router.route(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode(), "O status code para GET /health deve ser 200");
        assertEquals("OK", response.getReasonPhrase());
        assertEquals("{\"status\":\"ok\"}", response.getBody(), "O corpo da resposta deve conter status ok");
        assertEquals("application/json; charset=utf-8", response.getHeaders().get("Content-Type"));
    }

    // =========================================================================
    // Cenário 2: GET /medications -> MedicationHandler -> 200 OK
    // =========================================================================
    @Test
    @DisplayName("Cenário 2: Deve retornar 200 OK e a lista de medicamentos ao acessar GET /medications")
    void shouldReturn200ForGetMedications() {
        HttpRequest request = new HttpRequest("GET", "/medications");

        HttpResponse response = router.route(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode(), "O status code para GET /medications deve ser 200");
        assertEquals("OK", response.getReasonPhrase());
        assertTrue(response.getBody().contains("Losartana 50 mg"), "Deve conter Losartana 50 mg no JSON");
        assertTrue(response.getBody().contains("Sinvastatina 20 mg"), "Deve conter Sinvastatina 20 mg no JSON");
    }

    // =========================================================================
    // Cenário 3: GET /unknown -> 404 Not Found
    // =========================================================================
    @Test
    @DisplayName("Cenário 3: Deve retornar 404 Not Found ao acessar uma rota inexistente (GET /unknown)")
    void shouldReturn404ForUnknownRoute() {
        HttpRequest request = new HttpRequest("GET", "/unknown");

        HttpResponse response = router.route(request);

        assertNotNull(response);
        assertEquals(404, response.getStatusCode(), "Rota inexistente deve retornar 404");
        assertEquals("Not Found", response.getReasonPhrase());
        assertEquals("{\"error\":\"Route not found: /unknown\"}", response.getBody());
    }

    // =========================================================================
    // Cenário 4: POST /health -> 405 Method Not Allowed
    // =========================================================================
    @Test
    @DisplayName("Cenário 4: Deve retornar 405 Method Not Allowed quando o método HTTP não for suportado (POST /health)")
    void shouldReturn405ForUnsupportedMethodOnExistingRoute() {
        HttpRequest request = new HttpRequest("POST", "/health");

        HttpResponse response = router.route(request);

        assertNotNull(response);
        assertEquals(405, response.getStatusCode(), "Método não registrado em rota existente deve retornar 405");
        assertEquals("Method Not Allowed", response.getReasonPhrase());
        assertEquals("{\"error\":\"Method POST not allowed for route /health\"}", response.getBody());
    }

    // =========================================================================
    // Cenário 5: Normalização case-insensitive de método HTTP
    // =========================================================================
    @Test
    @DisplayName("Cenário 5: Deve tratar o método HTTP de forma case-insensitive (ex: 'get' -> 'GET')")
    void shouldHandleCaseInsensitiveHttpMethod() {
        HttpRequest request = new HttpRequest("get", "/health");

        HttpResponse response = router.route(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("{\"status\":\"ok\"}", response.getBody());
    }

    // =========================================================================
    // Cenário 6: Múltiplos métodos HTTP na mesma rota
    // =========================================================================
    @Test
    @DisplayName("Cenário 6: Deve permitir registrar múltiplos métodos HTTP para o mesmo path sem conflito")
    void shouldSupportMultipleMethodsOnSamePath() {
        router.register("POST", "/medications", req -> new HttpResponse(201, "Created", "{\"created\":true}"));

        HttpResponse getResponse = router.route(new HttpRequest("GET", "/medications"));
        HttpResponse postResponse = router.route(new HttpRequest("POST", "/medications"));

        assertEquals(200, getResponse.getStatusCode());
        assertEquals(201, postResponse.getStatusCode());
        assertEquals("{\"created\":true}", postResponse.getBody());
    }

    // =========================================================================
    // Cenário 7: Cálculo automático do header Content-Length em UTF-8
    // =========================================================================
    @Test
    @DisplayName("Cenário 7: Deve calcular corretamente o header Content-Length na HttpResponse")
    void shouldCalculateCorrectContentLengthHeader() {
        HttpResponse response = router.route(new HttpRequest("GET", "/health"));

        // {"status":"ok"} possui 15 bytes em UTF-8
        assertEquals("15", response.getHeaders().get("Content-Length"));
    }

    /**
     * Executor auxiliar para permitir rodar os testes JUnit 5 diretamente via terminal
     * mesmo em ambientes sem o plugin do Maven/ConsoleLauncher instalado.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println(" EXECUTANDO SUITE DE TESTES JUNIT 5: RouterTest");
        System.out.println("=================================================================");

        int passed = 0;
        int failed = 0;

        for (Method method : RouterTest.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                RouterTest instance = new RouterTest();
                instance.setUp();

                DisplayName displayName = method.getAnnotation(DisplayName.class);
                String testDescription = displayName != null ? displayName.value() : method.getName();

                try {
                    method.invoke(instance);
                    System.out.println("[PASS] " + method.getName() + " -> " + testDescription);
                    passed++;
                } catch (Throwable t) {
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    System.out.println("[FAIL] " + method.getName() + " -> " + cause.getMessage());
                    failed++;
                }
            }
        }

        System.out.println("-----------------------------------------------------------------");
        System.out.println("Resultado final: " + passed + " passaram, " + failed + " falharam (Total: " + (passed + failed) + ")");
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
