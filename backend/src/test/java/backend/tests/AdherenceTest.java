package backend.tests;

import backend.auth.InMemorySessionAuthenticator;
import backend.handlers.AdherenceHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.AdherenceSummary;
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
import backend.services.AdherenceService;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suíte de testes JUnit 5 para a Issue #36 - [ADHERENCE] Cálculo dos indicadores de adesão e pontualidade.
 */
public class AdherenceTest {

    private static final String TIMEZONE_SP = "America/Sao_Paulo";
    // Referência: 2026-11-04 12:00:00 America/Sao_Paulo = 2026-11-04T15:00:00Z
    private static final Instant FIXED_NOW = Instant.parse("2026-11-04T15:00:00Z");

    private PlannedDoseRepository plannedDoseRepository;
    private DoseRecordRepository doseRecordRepository;
    private InMemorySessionAuthenticator authenticator;
    private AdherenceService adherenceService;
    private AdherenceHandler adherenceHandler;
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

        adherenceService = new AdherenceService(
                plannedDoseRepository,
                doseRecordRepository,
                ZoneId.of(TIMEZONE_SP),
                fixedClock,
                Duration.ofMinutes(30),
                Duration.ofMinutes(30));

        adherenceHandler = new AdherenceHandler(adherenceService, authenticator);
        router = new Router();
        router.register("GET", "/adherence/summary", adherenceHandler);
    }

    private Map<String, String> authHeader(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer " + token);
        return headers;
    }

    private DosePrevista createDose(
            String id,
            String userId,
            String treatmentId,
            String medId,
            int scheduleVersion,
            String scheduledUtcIso,
            ScheduleStatus status) {
        return new DosePrevista(
                id,
                userId,
                treatmentId,
                medId,
                "Medicamento " + medId,
                scheduleVersion,
                Instant.parse(scheduledUtcIso),
                TIMEZONE_SP,
                new BigDecimal("1"),
                status);
    }

    private RegistroDeDose createRecord(
            String id,
            String doseId,
            String userId,
            DoseAction action,
            String actualUtcIso) {
        Instant actual = actualUtcIso != null ? Instant.parse(actualUtcIso) : null;
        Instant recorded = actual != null ? actual : FIXED_NOW;
        return new RegistroDeDose(id, doseId, userId, action, actual, recorded);
    }

    // =========================================================================
    // Caso mínimo 1: 2 doses vencidas, 2 TAKEN (08:05 e 20:47), tolerância 30 min -> 100% | 50%
    // =========================================================================
    @Test
    @DisplayName("Caso 1: 2 vencidas, 2 TAKEN (08:05 pontual e 20:47 atrasada) -> Execução 100%, Pontualidade 50%")
    void twoOverdueTwoTakenOneLate() {
        // Dia 2026-11-03: 08:00 SP = 11:00Z | 20:00 SP = 23:00Z
        plannedDoseRepository.save(createDose("d1", "user-a", "t1", "m1", 1, "2026-11-03T11:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("d2", "user-a", "t1", "m1", 1, "2026-11-03T23:00:00Z", ScheduleStatus.SCHEDULED));

        doseRecordRepository.save(createRecord("r1", "d1", "user-a", DoseAction.TAKEN, "2026-11-03T11:05:00Z"));
        doseRecordRepository.save(createRecord("r2", "d2", "user-a", DoseAction.TAKEN, "2026-11-03T23:47:00Z"));

        HttpResponse response = router.route(new HttpRequest(
                "GET", "/adherence/summary?from=2026-11-03&to=2026-11-03", authHeader("token-user-a")));

        assertEquals(200, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("\"eligiblePlannedDoses\":2"));
        assertTrue(body.contains("\"takenDoses\":2"));
        assertTrue(body.contains("\"onTimeTakenDoses\":1"));
        assertTrue(body.contains("\"lateTakenDoses\":1"));
        assertTrue(body.contains("\"executionPercent\":100.0"));
        assertTrue(body.contains("\"punctualityPercent\":50.0"));
    }

    // =========================================================================
    // Caso mínimo 2: 2 vencidas: 1 TAKEN pontual, 1 sem registro -> 50% | 100%
    // =========================================================================
    @Test
    @DisplayName("Caso 2: 2 vencidas (1 TAKEN pontual, 1 sem registro) -> Execução 50%, Pontualidade 100%")
    void twoOverdueOneTakenOneUnrecorded() {
        plannedDoseRepository.save(createDose("d1", "user-a", "t1", "m1", 1, "2026-11-03T11:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("d2", "user-a", "t1", "m1", 1, "2026-11-03T23:00:00Z", ScheduleStatus.SCHEDULED));
        doseRecordRepository.save(createRecord("r1", "d1", "user-a", DoseAction.TAKEN, "2026-11-03T11:10:00Z"));

        AdherenceSummary summary = adherenceService.calculateSummary(
                "user-a", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3));

        assertEquals(2, summary.getEligiblePlannedDoses());
        assertEquals(1, summary.getTakenDoses());
        assertEquals(0, summary.getSkippedDoses());
        assertEquals(1, summary.getUnrecordedOverdueDoses());
        assertEquals(50.0, summary.getExecutionPercent());
        assertEquals(100.0, summary.getPunctualityPercent());
    }

    // =========================================================================
    // Caso mínimo 3: 2 vencidas: 1 TAKEN pontual, 1 SKIPPED -> 50% | 100%
    // =========================================================================
    @Test
    @DisplayName("Caso 3: 2 vencidas (1 TAKEN pontual, 1 SKIPPED) -> Execução 50%, Pontualidade 100%")
    void twoOverdueOneTakenOneSkipped() {
        plannedDoseRepository.save(createDose("d1", "user-a", "t1", "m1", 1, "2026-11-03T11:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("d2", "user-a", "t1", "m1", 1, "2026-11-03T23:00:00Z", ScheduleStatus.SCHEDULED));
        doseRecordRepository.save(createRecord("r1", "d1", "user-a", DoseAction.TAKEN, "2026-11-03T11:00:00Z"));
        doseRecordRepository.save(createRecord("r2", "d2", "user-a", DoseAction.SKIPPED, null));

        AdherenceSummary summary = adherenceService.calculateSummary(
                "user-a", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3));

        assertEquals(2, summary.getEligiblePlannedDoses());
        assertEquals(1, summary.getTakenDoses());
        assertEquals(1, summary.getSkippedDoses());
        assertEquals(0, summary.getUnrecordedOverdueDoses());
        assertEquals(50.0, summary.getExecutionPercent());
        assertEquals(100.0, summary.getPunctualityPercent());
    }

    // =========================================================================
    // Caso mínimo 4: 2 doses futuras, nenhuma vencida -> NO_DATA | NO_DATA (nunca 100%)
    // =========================================================================
    @Test
    @DisplayName("Caso 4: 2 doses futuras, nenhuma vencida -> Execução NO_DATA (null) e Pontualidade NO_DATA (null)")
    void twoFutureDosesReturnNoDataNever100() {
        // FIXED_NOW = 2026-11-04T15:00:00Z; doses às 18:00Z e 23:00Z são futuras
        plannedDoseRepository.save(createDose("f1", "user-a", "t1", "m1", 1, "2026-11-04T18:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("f2", "user-a", "t1", "m1", 1, "2026-11-04T23:00:00Z", ScheduleStatus.SCHEDULED));

        HttpResponse response = router.route(new HttpRequest(
                "GET", "/adherence/summary?from=2026-11-04&to=2026-11-04", authHeader("token-user-a")));

        assertEquals(200, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("\"eligiblePlannedDoses\":0"));
        assertTrue(body.contains("\"executionPercent\":null"));
        assertTrue(body.contains("\"punctualityPercent\":null"));
        assertTrue(body.contains("\"executionStatus\":\"NO_DATA\""));
        assertTrue(body.contains("\"punctualityStatus\":\"NO_DATA\""));
    }

    // =========================================================================
    // Caso mínimo 5: 2 vencidas, ambas SKIPPED -> Execução 0% | Pontualidade NO_DATA
    // =========================================================================
    @Test
    @DisplayName("Caso 5: 2 vencidas, ambas SKIPPED -> Execução 0% e Pontualidade NO_DATA (null)")
    void twoOverdueBothSkipped() {
        plannedDoseRepository.save(createDose("d1", "user-a", "t1", "m1", 1, "2026-11-03T11:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("d2", "user-a", "t1", "m1", 1, "2026-11-03T23:00:00Z", ScheduleStatus.SCHEDULED));
        doseRecordRepository.save(createRecord("r1", "d1", "user-a", DoseAction.SKIPPED, null));
        doseRecordRepository.save(createRecord("r2", "d2", "user-a", DoseAction.SKIPPED, null));

        AdherenceSummary summary = adherenceService.calculateSummary(
                "user-a", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3));

        assertEquals(2, summary.getEligiblePlannedDoses());
        assertEquals(0, summary.getTakenDoses());
        assertEquals(2, summary.getSkippedDoses());
        assertEquals(0.0, summary.getExecutionPercent());
        assertNull(summary.getPunctualityPercent());
        assertEquals(AdherenceSummary.STATUS_OK, summary.getExecutionStatus());
        assertEquals(AdherenceSummary.STATUS_NO_DATA, summary.getPunctualityStatus());
    }

    // =========================================================================
    // Caso mínimo 6: 1 vencida TAKEN e 1 futura cancelada -> Execução 100% | Pontualidade conforme horário
    // =========================================================================
    @Test
    @DisplayName("Caso 6: 1 vencida TAKEN (atrasada) e 1 futura CANCELLED -> Execução 100% e Pontualidade 0%")
    void oneOverdueTakenLateAndOneCancelled() {
        plannedDoseRepository.save(createDose("d1", "user-a", "t1", "m1", 1, "2026-11-04T11:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("d2", "user-a", "t1", "m1", 1, "2026-11-04T23:00:00Z", ScheduleStatus.CANCELLED));
        // Tomada 45 min após o previsto (fora da tolerância de 30 min, mas conta como TAKEN na execução)
        doseRecordRepository.save(createRecord("r1", "d1", "user-a", DoseAction.TAKEN, "2026-11-04T11:45:00Z"));

        AdherenceSummary summary = adherenceService.calculateSummary(
                "user-a", LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4));

        assertEquals(1, summary.getEligiblePlannedDoses());
        assertEquals(1, summary.getTakenDoses());
        assertEquals(0, summary.getOnTimeTakenDoses());
        assertEquals(1, summary.getLateTakenDoses());
        assertEquals(100.0, summary.getExecutionPercent());
        assertEquals(0.0, summary.getPunctualityPercent());
    }

    // =========================================================================
    // Borda 1: Fronteira exata da tolerância (+30m00s, +30m01s, -30m00s, -30m01s)
    // =========================================================================
    @Test
    @DisplayName("Borda: Tomada exatamente na fronteira de 30 min é pontual; 1s além é fora da janela")
    void punctualityToleranceExactBoundaries() {
        // Doses às 10:00Z, 11:00Z, 12:00Z, 13:00Z (todas vencidas em FIXED_NOW=15:00Z)
        plannedDoseRepository.save(createDose("b1", "user-a", "t1", "m1", 1, "2026-11-04T10:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("b2", "user-a", "t1", "m1", 1, "2026-11-04T11:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("b3", "user-a", "t1", "m1", 1, "2026-11-04T12:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("b4", "user-a", "t1", "m1", 1, "2026-11-04T13:00:00Z", ScheduleStatus.SCHEDULED));

        // +30m00s -> pontual
        doseRecordRepository.save(createRecord("rb1", "b1", "user-a", DoseAction.TAKEN, "2026-11-04T10:30:00Z"));
        // +30m01s -> atrasada (fora da janela)
        doseRecordRepository.save(createRecord("rb2", "b2", "user-a", DoseAction.TAKEN, "2026-11-04T11:30:01Z"));
        // -30m00s -> pontual (janela anterior)
        doseRecordRepository.save(createRecord("rb3", "b3", "user-a", DoseAction.TAKEN, "2026-11-04T11:30:00Z"));
        // -30m01s -> fora da janela de pontualidade, mas conta como TAKEN na execução
        doseRecordRepository.save(createRecord("rb4", "b4", "user-a", DoseAction.TAKEN, "2026-11-04T12:29:59Z"));

        AdherenceSummary summary = adherenceService.calculateSummary(
                "user-a", LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4));

        assertEquals(4, summary.getEligiblePlannedDoses());
        assertEquals(4, summary.getTakenDoses());
        assertEquals(2, summary.getOnTimeTakenDoses());
        assertEquals(2, summary.getLateTakenDoses());
        assertEquals(100.0, summary.getExecutionPercent());
        assertEquals(50.0, summary.getPunctualityPercent());
    }

    // =========================================================================
    // Borda 2: Fusos diferentes e registro no dia seguinte para dose do dia anterior
    // =========================================================================
    @Test
    @DisplayName("Borda: Dose às 23:50 SP tomada às 00:05 SP do dia seguinte pertence ao período anterior e é pontual")
    void crossMidnightRecordAndTimezoneBoundaries() {
        // 23:50 de 2026-11-03 em America/Sao_Paulo = 2026-11-04T02:50:00Z
        plannedDoseRepository.save(createDose("night-1", "user-a", "t1", "m1", 1, "2026-11-04T02:50:00Z", ScheduleStatus.SCHEDULED));
        // Registro feito às 00:05 de 2026-11-04 SP (15 minutos depois) = 2026-11-04T03:05:00Z
        doseRecordRepository.save(createRecord("rn-1", "night-1", "user-a", DoseAction.TAKEN, "2026-11-04T03:05:00Z"));

        // Consultando apenas o dia civil 2026-11-03 em America/Sao_Paulo
        AdherenceSummary nov03 = adherenceService.calculateSummary(
                "user-a", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), null, null, ZoneId.of(TIMEZONE_SP));
        assertEquals(1, nov03.getEligiblePlannedDoses());
        assertEquals(100.0, nov03.getExecutionPercent());
        assertEquals(100.0, nov03.getPunctualityPercent());

        // Em UTC, 2026-11-04T02:50:00Z pertence ao dia 2026-11-04
        AdherenceSummary nov03Utc = adherenceService.calculateSummary(
                "user-a", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), null, null, ZoneId.of("UTC"));
        assertEquals(0, nov03Utc.getEligiblePlannedDoses());
        assertNull(nov03Utc.getExecutionPercent());
    }

    // =========================================================================
    // Borda 3: Alteração de cronograma preserva versão/horário históricos das doses anteriores
    // =========================================================================
    @Test
    @DisplayName("Borda: Alteração de cronograma preserva scheduledAt da versão histórica e ignora versão cancelada")
    void scheduleVersionEditPreservesHistoricalScheduledTime() {
        // Versão 1 histórica às 08:00 SP (11:00Z) tomada às 08:10 SP (11:10Z) -> pontual na v1
        plannedDoseRepository.save(createDose("v1-08h", "user-a", "t1", "m1", 1, "2026-11-03T11:00:00Z", ScheduleStatus.SCHEDULED));
        doseRecordRepository.save(createRecord("rv1", "v1-08h", "user-a", DoseAction.TAKEN, "2026-11-03T11:10:00Z"));

        // Versão 1 das 20:00 cancelada após alteração de cronograma para v2 às 21:00
        plannedDoseRepository.save(createDose("v1-20h-cancel", "user-a", "t1", "m1", 1, "2026-11-03T23:00:00Z", ScheduleStatus.CANCELLED));
        plannedDoseRepository.save(createDose("v2-21h", "user-a", "t1", "m1", 2, "2026-11-04T00:00:00Z", ScheduleStatus.SCHEDULED));
        doseRecordRepository.save(createRecord("rv2", "v2-21h", "user-a", DoseAction.TAKEN, "2026-11-04T00:40:00Z"));

        AdherenceSummary summary = adherenceService.calculateSummary(
                "user-a", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3));

        assertEquals(2, summary.getEligiblePlannedDoses());
        assertEquals(2, summary.getTakenDoses());
        assertEquals(1, summary.getOnTimeTakenDoses());
        assertEquals(1, summary.getLateTakenDoses());
        assertEquals(100.0, summary.getExecutionPercent());
        assertEquals(50.0, summary.getPunctualityPercent());
    }

    // =========================================================================
    // Borda 4: Idempotência, isolamento Usuário A vs B, filtros e Clock consistente
    // =========================================================================
    @Test
    @DisplayName("Borda: Isolamento entre usuários, filtros opcionais, idempotência e Clock fixo por avaliação")
    void userIsolationFiltersIdempotencyAndConsistentClock() {
        plannedDoseRepository.save(createDose("ua-t1", "user-a", "treat-1", "med-1", 1, "2026-11-03T11:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("ua-t2", "user-a", "treat-2", "med-2", 1, "2026-11-03T14:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("ub-t1", "user-b", "treat-b1", "med-b1", 1, "2026-11-03T11:00:00Z", ScheduleStatus.SCHEDULED));

        doseRecordRepository.save(createRecord("rua-1", "ua-t1", "user-a", DoseAction.TAKEN, "2026-11-03T11:05:00Z"));
        doseRecordRepository.save(createRecord("rub-1", "ub-t1", "user-b", DoseAction.SKIPPED, null));

        // Filtro por treatmentId=treat-1 para Usuário A
        HttpResponse filtered = router.route(new HttpRequest(
                "GET", "/adherence/summary?from=2026-11-03&to=2026-11-03&treatmentId=treat-1", authHeader("token-user-a")));
        assertEquals(200, filtered.getStatusCode());
        assertTrue(filtered.getBody().contains("\"eligiblePlannedDoses\":1"));
        assertTrue(filtered.getBody().contains("\"executionPercent\":100.0"));

        // Usuário B consulta o mesmo período -> vê apenas seus dados (0% execução)
        HttpResponse userB = router.route(new HttpRequest(
                "GET", "/adherence/summary?from=2026-11-03&to=2026-11-03", authHeader("token-user-b")));
        assertEquals(200, userB.getStatusCode());
        assertTrue(userB.getBody().contains("\"eligiblePlannedDoses\":1"));
        assertTrue(userB.getBody().contains("\"skippedDoses\":1"));
        assertTrue(userB.getBody().contains("\"executionPercent\":0.0"));

        // Requisições repetidas não alteram contagens
        int beforeDoses = plannedDoseRepository.countAll();
        int beforeRecords = doseRecordRepository.countAll();
        HttpResponse repeat = router.route(new HttpRequest(
                "GET", "/adherence/summary?from=2026-11-03&to=2026-11-03&treatmentId=treat-1", authHeader("token-user-a")));
        assertEquals(filtered.getBody(), repeat.getBody());
        assertEquals(beforeDoses, plannedDoseRepository.countAll());
        assertEquals(beforeRecords, doseRecordRepository.countAll());

        // Clock consultado uma única vez no início do cálculo
        AtomicInteger clockCalls = new AtomicInteger(0);
        Clock countingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                // Avança a cada chamada se fosse chamado mais de uma vez
                return FIXED_NOW.plusSeconds(clockCalls.getAndIncrement() * 3600L);
            }
        };
        AdherenceService singleRefService = new AdherenceService(
                plannedDoseRepository,
                doseRecordRepository,
                ZoneId.of(TIMEZONE_SP),
                countingClock,
                Duration.ofMinutes(30),
                Duration.ofMinutes(30));
        singleRefService.calculateSummary("user-a", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4));
        assertEquals(1, clockCalls.get(), "O instante de referência deve ser capturado uma única vez por avaliação");
    }

    // =========================================================================
    // Borda 5: Validação de erros 400 Bad Request e 401 Unauthorized
    // =========================================================================
    @Test
    @DisplayName("Erros HTTP: Parâmetros inválidos/ausentes retornam 400 e sessão ausente/inválida/expirada retorna 401")
    void invalidParametersOrMissingAuthReturn400Or401() {
        assertEquals(400, router.route(new HttpRequest("GET", "/adherence/summary", authHeader("token-user-a"))).getStatusCode());
        assertEquals(400, router.route(new HttpRequest("GET", "/adherence/summary?from=2026-11-05&to=2026-11-03", authHeader("token-user-a"))).getStatusCode());
        assertEquals(400, router.route(new HttpRequest("GET", "/adherence/summary?from=2026-11-03&to=2026-11-05&userId=user-b", authHeader("token-user-a"))).getStatusCode());

        assertEquals(401, router.route(new HttpRequest("GET", "/adherence/summary?from=2026-11-03&to=2026-11-05")).getStatusCode());
        assertEquals(401, router.route(new HttpRequest("GET", "/adherence/summary?from=2026-11-03&to=2026-11-05", authHeader("token-invalido"))).getStatusCode());
        assertEquals(401, router.route(new HttpRequest("GET", "/adherence/summary?from=2026-11-03&to=2026-11-05", authHeader("token-expired"))).getStatusCode());
    }

    // =========================================================================
    // Integração HTTP real via TCP Socket
    // =========================================================================
    @Test
    @DisplayName("Integração HTTP real: Servidor responde GET /adherence/summary com contagens e percentuais")
    void realHttpServerIntegrationTest() throws Exception {
        plannedDoseRepository.save(createDose("http-1", "user-a", "t1", "m1", 1, "2026-11-03T11:00:00Z", ScheduleStatus.SCHEDULED));
        plannedDoseRepository.save(createDose("http-2", "user-a", "t1", "m1", 1, "2026-11-03T23:00:00Z", ScheduleStatus.SCHEDULED));
        doseRecordRepository.save(createRecord("rh-1", "http-1", "user-a", DoseAction.TAKEN, "2026-11-03T11:05:00Z"));
        doseRecordRepository.save(createRecord("rh-2", "http-2", "user-a", DoseAction.TAKEN, "2026-11-03T23:47:00Z"));

        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        Servidor servidor = new Servidor(port, router);
        Thread serverThread = new Thread(servidor::aceitadorDeConexao);
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(150);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/adherence/summary?from=2026-11-03&to=2026-11-03"))
                    .header("Authorization", "Bearer token-user-a")
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> httpResponse =
                    client.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

            assertNotNull(httpResponse);
            assertEquals(200, httpResponse.statusCode());
            assertTrue(httpResponse.body().contains("\"executionPercent\":100.0"));
            assertTrue(httpResponse.body().contains("\"punctualityPercent\":50.0"));
            assertTrue(httpResponse.body().contains("\"punctualityToleranceMinutes\":30"));
        } finally {
            servidor.parar();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println(" EXECUTANDO SUITE DE TESTES JUNIT 5: AdherenceTest");
        System.out.println("=================================================================");

        int passed = 0;
        int failed = 0;

        for (Method method : AdherenceTest.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                AdherenceTest instance = new AdherenceTest();
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
