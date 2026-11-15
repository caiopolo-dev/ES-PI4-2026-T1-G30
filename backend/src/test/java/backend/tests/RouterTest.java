package backend.tests;

import backend.handlers.HealthHandler;
import backend.handlers.MedicationHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.repositories.InMemoryMedicationRepository;
import backend.router.Router;
import backend.services.MedicationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suíte de testes unitários em JUnit 5 para validar o Router e os endpoints iniciais
 * (HealthHandler e MedicationHandler).
 */
public class RouterTest {

    private Router router;

    @BeforeEach
    void setUp() {
        router = new Router();
        router.register("GET", "/health", new HealthHandler());
        router.register(
                "GET",
                "/medications",
                new MedicationHandler(new MedicationService(new InMemoryMedicationRepository())));
    }

    @Test
    @DisplayName("Cenário 1: Deve retornar 200 OK e status ok ao acessar GET /health")
    void shouldReturn200ForGetHealth() {
        HttpRequest request = new HttpRequest("GET", "/health");

        HttpResponse response = router.route(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReasonPhrase());
        assertEquals("{\"status\":\"ok\"}", response.getBody());
        assertEquals("application/json; charset=utf-8", response.getHeaders().get("Content-Type"));
    }

    @Test
    @DisplayName("Cenário 2: Deve retornar 200 OK e a lista de medicamentos ao acessar GET /medications")
    void shouldReturn200ForGetMedications() {
        HttpRequest request = new HttpRequest("GET", "/medications");

        HttpResponse response = router.route(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReasonPhrase());
        assertTrue(response.getBody().contains("Losartana 50 mg"));
        assertTrue(response.getBody().contains("Sinvastatina 20 mg"));
    }

    @Test
    @DisplayName("Cenário 3: Deve retornar 404 Not Found ao acessar uma rota inexistente (GET /unknown)")
    void shouldReturn404ForUnknownRoute() {
        HttpRequest request = new HttpRequest("GET", "/unknown");

        HttpResponse response = router.route(request);

        assertNotNull(response);
        assertEquals(404, response.getStatusCode());
        assertEquals("Not Found", response.getReasonPhrase());
        assertEquals("{\"error\":\"Route not found: /unknown\"}", response.getBody());
    }

    @Test
    @DisplayName("Cenário 4: Deve retornar 405 Method Not Allowed quando o método HTTP não for suportado (POST /health)")
    void shouldReturn405ForUnsupportedMethodOnExistingRoute() {
        HttpRequest request = new HttpRequest("POST", "/health");

        HttpResponse response = router.route(request);

        assertNotNull(response);
        assertEquals(405, response.getStatusCode());
        assertEquals("Method Not Allowed", response.getReasonPhrase());
        assertEquals("{\"error\":\"Method POST not allowed for route /health\"}", response.getBody());
    }

    @Test
    @DisplayName("Cenário 5: Deve tratar o método HTTP de forma case-insensitive (ex: 'get' -> 'GET')")
    void shouldHandleCaseInsensitiveHttpMethod() {
        HttpRequest request = new HttpRequest("get", "/health");

        HttpResponse response = router.route(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("{\"status\":\"ok\"}", response.getBody());
    }

    public static void main(String[] args) throws Exception {
        int passed = 0;
        int failed = 0;
        for (Method method : RouterTest.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                RouterTest instance = new RouterTest();
                instance.setUp();
                try {
                    method.invoke(instance);
                    System.out.println("[PASS] " + method.getName());
                    passed++;
                } catch (Throwable t) {
                    System.out.println("[FAIL] " + method.getName() + ": " + t.getCause());
                    failed++;
                }
            }
        }
        System.out.println("Total: " + passed + " passaram, " + failed + " falharam.");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
