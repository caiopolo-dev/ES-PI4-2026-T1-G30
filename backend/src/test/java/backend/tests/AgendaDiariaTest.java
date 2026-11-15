package backend.tests;

import backend.auth.InMemorySessionAuthenticator;
import backend.handlers.AgendaHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.repositories.DoseRecordRepository;
import backend.repositories.MongoDoseRecordRepository;
import backend.repositories.MongoPlannedDoseRepository;
import backend.repositories.PlannedDoseRepository;
import backend.router.Router;
import backend.server.Servidor;
import backend.services.AgendaService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suíte de testes JUnit 5 para a Issue #31 - [DOSE] API de consulta da agenda diária.
 * Valida todos os cenários mínimos, regras de fuso/UTC, titularidade, estados derivados
 * e integração HTTP real.
 */
public class AgendaDiariaTest {

    private static final String TIMEZONE_SP = "America/Sao_Paulo";
    // Instante fixo de referência: 2026-11-05 às 12:00:00 no horário de São Paulo (15:00:00Z)
    private static final Instant FIXED_NOW = Instant.parse("2026-11-05T15:00:00Z");

    private PlannedDoseRepository plannedDoseRepository;
    private DoseRecordRepository doseRecordRepository;
    private InMemorySessionAuthenticator authenticator;
    private AgendaService agendaService;
    private AgendaHandler agendaHandler;
    private Router router;

    @BeforeEach
    void setUp() {
        plannedDoseRepository = new MongoPlannedDoseRepository();
        doseRecordRepository = new MongoDoseRecordRepository();
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

        authenticator = new InMemorySessionAuthenticator(fixedClock);
        authenticator.registerSession("token-user-a", "user-a", FIXED_NOW.plus(Duration.ofHours(2)));
        authenticator.registerSession("token-user-b", "user-b", FIXED_NOW.plus(Duration.ofHours(2)));
        authenticator.registerSession("token-expired", "user-a", FIXED_NOW.minus(Duration.ofMinutes(1)));

        agendaService = new AgendaService(
                plannedDoseRepository,
                doseRecordRepository,
                null,
                ZoneId.of(TIMEZONE_SP),
                fixedClock,
                Duration.ofMinutes(30));

        agendaHandler = new AgendaHandler(agendaService, authenticator);
        router = new Router();
        router.register("GET", "/doses", agendaHandler);
    }

    private Map<String, String> authHeader(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer " + token);
        return headers;
    }

    // =========================================================================
    // Cenário 1: Usuário A consulta dia com doses às 08:00 e 20:00 -> 200, duas doses ordenadas
    // =========================================================================
    @Test
    @DisplayName("Cenário 1: Usuário A consulta dia com doses às 08:00 e 20:00 -> 200 OK com duas doses ordenadas")
    void userAConsultsDayWithOrderedDoses() {
        // Insere fora de ordem (20:00 primeiro, 08:00 depois) para comprovar a ordenação cronológica
        // 20:00 em America/Sao_Paulo (UTC-3) = 23:00:00Z
        plannedDoseRepository.save(new DosePrevista(
                "dose-20h",
                "user-a",
                "treat-001",
                "med-002",
                "Sinvastatina 20 mg",
                1,
                Instant.parse("2026-11-05T23:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.SCHEDULED));

        // 08:00 em America/Sao_Paulo (UTC-3) = 11:00:00Z
        plannedDoseRepository.save(new DosePrevista(
                "dose-08h",
                "user-a",
                "treat-001",
                "med-001",
                "Losartana 50 mg",
                1,
                Instant.parse("2026-11-05T11:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.SCHEDULED));

        HttpRequest request = new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-user-a"));
        HttpResponse response = router.route(request);

        assertEquals(200, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("\"date\":\"2026-11-05\""));
        assertTrue(body.contains("\"timezone\":\"America/Sao_Paulo\""));

        int index08h = body.indexOf("\"id\":\"dose-08h\"");
        int index20h = body.indexOf("\"id\":\"dose-20h\"");
        assertTrue(index08h > 0 && index20h > index08h, "A dose das 08:00 deve vir antes da dose das 20:00");
        assertTrue(body.contains("\"scheduledLocalTime\":\"08:00\""));
        assertTrue(body.contains("\"scheduledLocalTime\":\"20:00\""));
    }

    // =========================================================================
    // Cenário 2: Usuário A consulta dia sem doses -> 200, doses: []
    // =========================================================================
    @Test
    @DisplayName("Cenário 2: Usuário A consulta dia sem doses -> 200 OK com doses: []")
    void userAConsultsEmptyDay() {
        HttpRequest request = new HttpRequest("GET", "/doses?date=2026-11-06", authHeader("token-user-a"));
        HttpResponse response = router.route(request);

        assertEquals(200, response.getStatusCode());
        assertEquals(
                "{\"date\":\"2026-11-06\",\"timezone\":\"America/Sao_Paulo\",\"doses\":[]}",
                response.getBody());
    }

    // =========================================================================
    // Cenário 3: Consulta repetida do mesmo dia -> Mesma informação; nenhum documento adicional criado
    // =========================================================================
    @Test
    @DisplayName("Cenário 3: Consulta repetida do mesmo dia -> Mesma resposta e zero documentos adicionais criados")
    void repeatedQueryIsIdempotentAndSideEffectFree() {
        plannedDoseRepository.save(new DosePrevista(
                "dose-001",
                "user-a",
                "treat-001",
                "med-001",
                "Losartana 50 mg",
                1,
                Instant.parse("2026-11-05T11:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.SCHEDULED));

        int initialDoseCount = plannedDoseRepository.countAll();
        int initialRecordCount = doseRecordRepository.countAll();

        HttpRequest request1 = new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-user-a"));
        HttpRequest request2 = new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-user-a"));

        HttpResponse response1 = router.route(request1);
        HttpResponse response2 = router.route(request2);

        assertEquals(200, response1.getStatusCode());
        assertEquals(200, response2.getStatusCode());
        assertEquals(response1.getBody(), response2.getBody(), "Consultas repetidas devem produzir o mesmo JSON");
        assertEquals(initialDoseCount, plannedDoseRepository.countAll(), "GET não pode criar novas doses");
        assertEquals(initialRecordCount, doseRecordRepository.countAll(), "GET não pode criar registros de dose");
    }

    // =========================================================================
    // Cenário 4: Dose já registrada como tomada -> Registro exibido sem perder o horário originalmente previsto
    // =========================================================================
    @Test
    @DisplayName("Cenário 4: Dose registrada como tomada (TAKEN) -> Exibe registro e preserva horário previsto")
    void takenDoseDisplaysRecordAndPreservesScheduledTime() {
        plannedDoseRepository.save(new DosePrevista(
                "dose-taken",
                "user-a",
                "treat-001",
                "med-001",
                "Losartana 50 mg",
                1,
                Instant.parse("2026-11-05T11:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.SCHEDULED));

        doseRecordRepository.save(new RegistroDeDose(
                "rec-001",
                "dose-taken",
                "user-a",
                DoseAction.TAKEN,
                Instant.parse("2026-11-05T11:05:00Z"),
                Instant.parse("2026-11-05T11:05:10Z")));

        HttpRequest request = new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-user-a"));
        HttpResponse response = router.route(request);

        assertEquals(200, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("\"scheduledAt\":\"2026-11-05T11:00:00Z\""), "Deve preservar o scheduledAt original");
        assertTrue(body.contains("\"scheduledLocalTime\":\"08:00\""), "Deve preservar o horário local 08:00");
        assertTrue(body.contains("\"action\":\"TAKEN\""));
        assertTrue(body.contains("\"actualTime\":\"2026-11-05T11:05:00Z\""));
        assertTrue(body.contains("\"displayStatus\":\"TAKEN\""));
    }

    // =========================================================================
    // Cenário 5: Dose passada sem registro -> Estado derivado pendente/vencida (OVERDUE), não "não tomei" (SKIPPED)
    // =========================================================================
    @Test
    @DisplayName("Cenário 5: Dose passada sem registro -> Classificada como OVERDUE e nunca como SKIPPED")
    void pastDoseWithoutRecordIsOverdueAndNeverSkipped() {
        // Horário previsto 08:00 (11:00Z), enquanto FIXED_NOW é 12:00 (15:00Z)
        plannedDoseRepository.save(new DosePrevista(
                "dose-overdue",
                "user-a",
                "treat-001",
                "med-001",
                "Losartana 50 mg",
                1,
                Instant.parse("2026-11-05T11:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.SCHEDULED));

        HttpRequest request = new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-user-a"));
        HttpResponse response = router.route(request);

        assertEquals(200, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("\"record\":null"), "Não deve existir registro inventado");
        assertTrue(body.contains("\"displayStatus\":\"OVERDUE\""), "Estado derivado deve ser OVERDUE");
        assertFalse(body.contains("SKIPPED"), "Ausência de registro jamais deve ser confundida com SKIPPED");
        assertEquals(0, doseRecordRepository.countAll(), "Nenhum DoseRecord automático pode ser persistido");
    }

    // =========================================================================
    // Cenário 6: Dose futura cancelada após alteração de cronograma -> Não aparece como dose devida
    // =========================================================================
    @Test
    @DisplayName("Cenário 6: Dose futura cancelada (CANCELLED) -> Omitida das doses devidas do dia")
    void cancelledFutureDoseIsOmittedFromDueAgenda() {
        plannedDoseRepository.save(new DosePrevista(
                "dose-active-21h",
                "user-a",
                "treat-001",
                "med-001",
                "Losartana 50 mg",
                2,
                Instant.parse("2026-11-05T23:30:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.SCHEDULED));

        plannedDoseRepository.save(new DosePrevista(
                "dose-cancelled-20h",
                "user-a",
                "treat-001",
                "med-001",
                "Losartana 50 mg",
                1,
                Instant.parse("2026-11-05T23:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.CANCELLED));

        HttpRequest request = new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-user-a"));
        HttpResponse response = router.route(request);

        assertEquals(200, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("\"id\":\"dose-active-21h\""));
        assertTrue(body.contains("\"displayStatus\":\"UPCOMING\""));
        assertFalse(body.contains("dose-cancelled-20h"), "Dose CANCELLED não deve aparecer como dose devida");
    }

    // =========================================================================
    // Cenário 7: Usuário B consulta a mesma data -> Somente dados de B, sem vazamento de A
    // =========================================================================
    @Test
    @DisplayName("Cenário 7: Usuário B consulta a mesma data -> Retorna somente dados de B sem vazar dados de A")
    void userBConsultsSameDateWithoutDataLeakageFromUserA() {
        plannedDoseRepository.save(new DosePrevista(
                "dose-user-a",
                "user-a",
                "treat-a",
                "med-a",
                "Medicamento Secreto A",
                1,
                Instant.parse("2026-11-05T11:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.SCHEDULED));

        plannedDoseRepository.save(new DosePrevista(
                "dose-user-b",
                "user-b",
                "treat-b",
                "med-b",
                "Vitamina D",
                1,
                Instant.parse("2026-11-05T12:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("2"),
                ScheduleStatus.SCHEDULED));

        HttpRequest requestB = new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-user-b"));
        HttpResponse responseB = router.route(requestB);

        assertEquals(200, responseB.getStatusCode());
        String bodyB = responseB.getBody();
        assertTrue(bodyB.contains("\"id\":\"dose-user-b\""));
        assertTrue(bodyB.contains("\"medicationName\":\"Vitamina D\""));
        assertFalse(bodyB.contains("dose-user-a"), "Não pode vazar ID de dose do Usuário A");
        assertFalse(bodyB.contains("Medicamento Secreto A"), "Não pode vazar nome de medicamento do Usuário A");
    }

    // =========================================================================
    // Cenário 8: Data inválida ou autenticação ausente -> 400 ou 401, respectivamente
    // =========================================================================
    @Test
    @DisplayName("Cenário 8: Data inválida/ausente/parâmetro extra retorna 400 e sessão ausente/inválida/expirada retorna 401")
    void invalidDateOrMissingAuthenticationReturns400Or401() {
        // 400 Bad Request: sem query parameter date
        HttpResponse missingDate = router.route(new HttpRequest("GET", "/doses", authHeader("token-user-a")));
        assertEquals(400, missingDate.getStatusCode());

        // 400 Bad Request: formato de data inválido
        HttpResponse invalidDate = router.route(new HttpRequest("GET", "/doses?date=05-11-2026", authHeader("token-user-a")));
        assertEquals(400, invalidDate.getStatusCode());

        // 400 Bad Request: tentativa de enviar userId arbitrário via query parameter
        HttpResponse extraParam = router.route(
                new HttpRequest("GET", "/doses?date=2026-11-05&userId=user-b", authHeader("token-user-a")));
        assertEquals(400, extraParam.getStatusCode());

        // 401 Unauthorized: sem header Authorization
        HttpResponse noAuth = router.route(new HttpRequest("GET", "/doses?date=2026-11-05"));
        assertEquals(401, noAuth.getStatusCode());

        // 401 Unauthorized: token inexistente
        HttpResponse invalidToken = router.route(
                new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-invalido")));
        assertEquals(401, invalidToken.getStatusCode());

        // 401 Unauthorized: sessão expirada
        HttpResponse expiredToken = router.route(
                new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-expired")));
        assertEquals(401, expiredToken.getStatusCode());
    }

    // =========================================================================
    // Cenário 9: Filtro de fronteira de fuso horário (22:00 SP = 01:00Z do dia seguinte em UTC)
    // =========================================================================
    @Test
    @DisplayName("Cenário 9: Dose às 22:00 de 2026-11-05 em São Paulo (2026-11-06T01:00:00Z em UTC) pertence ao dia 2026-11-05")
    void correctlyFiltersCivilDayBoundariesAcrossUtcMidnight() {
        // 22:00 de 2026-11-05 em America/Sao_Paulo = 2026-11-06T01:00:00Z em UTC
        plannedDoseRepository.save(new DosePrevista(
                "dose-22h-sp",
                "user-a",
                "treat-001",
                "med-001",
                "Melatonina",
                1,
                Instant.parse("2026-11-06T01:00:00Z"),
                TIMEZONE_SP,
                new BigDecimal("1"),
                ScheduleStatus.SCHEDULED));

        HttpResponse day05 = router.route(new HttpRequest("GET", "/doses?date=2026-11-05", authHeader("token-user-a")));
        HttpResponse day06 = router.route(new HttpRequest("GET", "/doses?date=2026-11-06", authHeader("token-user-a")));

        assertEquals(200, day05.getStatusCode());
        assertTrue(day05.getBody().contains("\"id\":\"dose-22h-sp\""));
        assertTrue(day05.getBody().contains("\"scheduledLocalTime\":\"22:00\""));

        assertEquals(200, day06.getStatusCode());
        assertFalse(day06.getBody().contains("dose-22h-sp"), "Não deve aparecer na agenda do dia 2026-11-06");
    }

    // =========================================================================
    // Cenário 10: Teste de integração com servidor HTTP real (Socket + HttpRequestParser + Router)
    // =========================================================================
    @Test
    @DisplayName("Cenário 10: Integração com servidor HTTP real via TCP Socket respondendo GET /doses?date=2026-11-05")
    void realHttpServerIntegrationTest() throws Exception {
        plannedDoseRepository.save(new DosePrevista(
                "dose-http-01",
                "user-a",
                "treat-001",
                "med-001",
                "Losartana 50 mg",
                1,
                Instant.parse("2026-11-05T15:10:00Z"),
                TIMEZONE_SP,
                new BigDecimal("0.5"),
                ScheduleStatus.SCHEDULED));

        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        Servidor servidor = new Servidor(port, router);
        Thread serverThread = new Thread(servidor::aceitadorDeConexao);
        serverThread.setDaemon(true);
        serverThread.start();

        // Aguarda brevemente o servidor iniciar a escuta
        Thread.sleep(150);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/doses?date=2026-11-05"))
                    .header("Authorization", "Bearer token-user-a")
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> httpResponse =
                    client.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

            assertNotNull(httpResponse);
            assertEquals(200, httpResponse.statusCode());
            assertTrue(httpResponse.body().contains("\"id\":\"dose-http-01\""));
            assertTrue(httpResponse.body().contains("\"plannedQuantity\":0.5"));
            assertTrue(httpResponse.body().contains("\"displayStatus\":\"UPCOMING\""));
        } finally {
            servidor.parar();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println(" EXECUTANDO SUITE DE TESTES JUNIT 5: AgendaDiariaTest");
        System.out.println("=================================================================");

        int passed = 0;
        int failed = 0;

        for (Method method : AgendaDiariaTest.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                AgendaDiariaTest instance = new AgendaDiariaTest();
                instance.setUp();

                DisplayName displayName = method.getAnnotation(DisplayName.class);
                String description = displayName != null ? displayName.value() : method.getName();

                try {
                    method.invoke(instance);
                    System.out.println("[PASS] " + method.getName() + " -> " + description);
                    passed++;
                } catch (Throwable t) {
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    System.out.println("[FAIL] " + method.getName() + " -> " + cause);
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
