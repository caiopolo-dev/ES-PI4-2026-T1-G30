package backend.src;

import backend.src.handlers.HealthHandler;
import backend.src.handlers.MedicationHandler;
import backend.src.http.HttpRequest;
import backend.src.http.HttpResponse;
import backend.src.router.HttpHandler;
import backend.src.router.Router;

/**
 * Validação dos cenários mínimos do Router e endpoints iniciais.
 */
public class Main {

    public static void main(String[] args) {
        // 1. Instanciar o Router e os Handlers
        Router router = new Router();
        HttpHandler healthHandler = new HealthHandler();
        HttpHandler medicationHandler = new MedicationHandler();

        // 2. Registro centralizado de rotas (sem if/else espalhados)
        router.register("GET", "/health", healthHandler);
        router.register("GET", "/medications", medicationHandler);

        // 3. Executar os 4 cenários mínimos exigidos
        System.out.println("=== CENARIO 1: GET /health -> HealthHandler -> 200 ===");
        testarCenario(router, new HttpRequest("GET", "/health"));

        System.out.println("\n=== CENARIO 2: GET /medications -> MedicationHandler -> 200 ===");
        testarCenario(router, new HttpRequest("GET", "/medications"));

        System.out.println("\n=== CENARIO 3: GET /unknown -> 404 ===");
        testarCenario(router, new HttpRequest("GET", "/unknown"));

        System.out.println("\n=== CENARIO 4: POST /health -> 405 ===");
        testarCenario(router, new HttpRequest("POST", "/health"));
    }

    private static void testarCenario(Router router, HttpRequest request) {
        System.out.println("Requisicao: " + request.getMethod() + " " + request.getPath());
        HttpResponse response = router.route(request);
        System.out.println("Status retornado: " + response.getStatusCode() + " " + response.getReasonPhrase());
        System.out.println("Body: " + response.getBody());
    }
}
