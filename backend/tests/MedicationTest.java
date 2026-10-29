package backend.tests;

import backend.src.handlers.MedicationHandler;
import backend.src.http.HttpRequest;
import backend.src.models.Medication;
import backend.src.repositories.InMemoryMedicationRepository;
import backend.src.repositories.MedicationRepository;
import backend.src.services.MedicationService;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Testes executáveis sem dependências externas. */
public class MedicationTest {
    public static void main(String[] args) throws Exception {
        testClasses();
        System.out.println("PASS: modelo, repository, service e handler");
        testHttp();
        System.out.println("PASS: GET /medications com servidor real");
    }

    private static void testClasses() {
        Medication model = new Medication("custom", "Medicamento de teste");
        check("custom".equals(model.getId()), "Identificador do modelo");
        check("Medicamento de teste".equals(model.getName()), "Nome do modelo");

        List<Medication> stored = new InMemoryMedicationRepository().findAll();
        check(stored.size() == 2, "Quantidade em memória");
        check("1".equals(stored.get(0).getId()), "ID da Losartana");
        check("Losartana 50 mg".equals(stored.get(0).getName()), "Losartana cadastrada");
        check("2".equals(stored.get(1).getId()), "ID da Sinvastatina");
        check("Sinvastatina 20 mg".equals(stored.get(1).getName()), "Sinvastatina cadastrada");
        try {
            stored.clear();
            throw new AssertionError("A lista deve impedir alterações externas");
        } catch (UnsupportedOperationException expected) {
            // A lista interna permanece protegida.
        }

        int[] calls = {0};
        MedicationRepository fake = () -> {
            calls[0]++;
            return Collections.singletonList(model);
        };
        MedicationService service = new MedicationService(fake);
        check(service.listMedications().get(0) == model, "Service usa o repository injetado");
        check(calls[0] == 1, "Service consulta o repository");
        backend.src.http.HttpResponse response = new MedicationHandler(service)
            .handle(new HttpRequest("GET", "/medications"));
        check(response.getStatusCode() == 200, "Status do handler");
        check(response.getBody().equals("[{\"id\":\"custom\",\"name\":\"Medicamento de teste\"}]"),
            "Handler usa dados do serviço, sem JSON fixo");

        MedicationHandler empty = new MedicationHandler(new MedicationService(Collections::emptyList));
        check("[]".equals(empty.handle(new HttpRequest("GET", "/medications")).getBody()),
            "Lista vazia");
        Medication special = new Medication("x", "Dose \"A\"\\B\n");
        MedicationHandler escaped = new MedicationHandler(new MedicationService(
            () -> Collections.singletonList(special)));
        check(escaped.handle(new HttpRequest("GET", "/medications")).getBody()
            .equals("[{\"id\":\"x\",\"name\":\"Dose \\\"A\\\"\\\\B\\u000a\"}]"),
            "Escape de caracteres no JSON");
        try {
            new MedicationService(null);
            throw new AssertionError("Service deve rejeitar repository null");
        } catch (NullPointerException expected) {
            // Falha imediata para dependência ausente.
        }
    }

    private static void testHttp() throws Exception {
        int port;
        try (ServerSocket available = new ServerSocket(0)) {
            port = available.getLocalPort();
        }
        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        Process server = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
            "backend.src.Main", String.valueOf(port)).inheritIO().start();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            URI endpoint = URI.create("http://127.0.0.1:" + port + "/medications");
            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < 30; attempt++) {
                check(server.isAlive(), "Servidor encerrou antes da requisição");
                try {
                    response = client.send(java.net.http.HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                    break;
                } catch (java.io.IOException e) {
                    if (attempt == 29) {
                        throw e;
                    }
                    Thread.sleep(100);
                }
            }
            check(response != null && response.statusCode() == 200, "HTTP 200");
            check(response.headers().firstValue("Content-Type").orElse("")
                .equals("application/json; charset=utf-8"), "Content-Type JSON UTF-8");
            check(response.body().equals("[{\"id\":\"1\",\"name\":\"Losartana 50 mg\"},"
                + "{\"id\":\"2\",\"name\":\"Sinvastatina 20 mg\"}]"), "Medicamentos na resposta HTTP");
            check(Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1"))
                == response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                "Content-Length em bytes");
            System.out.println("HTTP " + response.statusCode() + " " + response.body());
        } finally {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                server.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
