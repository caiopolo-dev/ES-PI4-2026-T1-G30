package backend.tests;

import backend.auth.AuthContext;
import backend.handlers.HistoryHandler;
import backend.http.HttpRequest;
import backend.models.DoseAction;
import backend.models.DoseHistoryItem;
import backend.models.DoseHistoryPeriod;
import backend.models.DoseHistoryResponse;
import backend.router.Router;
import backend.services.DoseHistoryService;
import backend.config.MongoConnection;
import backend.models.CronogramaDose;
import backend.models.DosePrevista;
import backend.models.MedicamentoEmUso;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.MongoDoseRecordRepository;
import backend.repositories.MongoPlannedDoseRepository;
import backend.repositories.MongoSessionRepository;
import backend.repositories.MongoTreatmentRepository;
import backend.server.Servidor;
import backend.services.BearerTokenExtractor;
import backend.services.SessionService;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DoseHistoryIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
    void realHttpUsesMongoSessionsAndPreservesTheStoredHistory() throws Exception {

        String database = "dosecerta_test_history_" + UUID.randomUUID().toString().replace("-", "");

        try (var connection = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
            try {
                var planned = new MongoPlannedDoseRepository(connection.getDatabase());
                var recorded = new MongoDoseRecordRepository(connection.getDatabase());
                var treatments = new MongoTreatmentRepository(connection.getDatabase());
                var reference = Instant.parse("2026-11-03T15:00:00Z");

                var medication = new MedicamentoEmUso("m1", "Nome atual", "50 mg", 1,
                    "comprimido", null, true, List.of(new CronogramaDose(List.of("20:00"), "2026-11-03", "America/Sao_Paulo")));
                medication.addScheduleVersion(new CronogramaDose(List.of("21:00"), "2026-11-03", "America/Sao_Paulo"));

                var treatment = new Treatment("t-owner", "owner", "Tratamento", null,
                    LocalDate.parse("2026-11-03"), null, TreatmentStatus.DRAFT, null, List.of(medication,
                        new MedicamentoEmUso("m2", "Outro", "10 mg", 1, "comprimido", null, true, List.of())));
                treatment.activate();
                treatments.save(treatment);
                treatments.save(new Treatment("t-boundary", "owner", "Limites", null, LocalDate.parse("2026-11-03"),
                    null, TreatmentStatus.DRAFT, null, List.of(new MedicamentoEmUso("m3", "Demo", "10 mg", null, null))));
                treatments.save(new Treatment("t-foreign", "other", "Outro titular", null, LocalDate.parse("2026-11-03"),
                    null, TreatmentStatus.DRAFT, null, List.of(new MedicamentoEmUso("other-med", "Demo", "10 mg", null, null))));

                var taken = storedDose("a-taken", "owner", "t-owner", "m1", 1, "2026-11-03T11:00:00Z");
                var skipped = storedDose("b-skipped", "owner", "t-owner", "m2", 1, "2026-11-03T11:00:00Z");

                // Preparei ocorrencias na fronteira e fora do dia para testar o filtro real do MongoDB.
                planned.saveAll(List.of(taken, skipped,
                    storedDose("missing", "owner", "t-owner", "m1", 1, "2026-11-03T12:00:00Z"),
                    storedDose("future", "owner", "t-owner", "m1", 2, "2026-11-03T16:00:00Z"),
                    storedDose("cancelled", "owner", "t-owner", "m1", 2, "2026-11-03T17:00:00Z"),
                    storedDose("old20", "owner", "t-owner", "m1", 1, "2026-11-03T23:00:00Z"),
                    storedDose("new21", "owner", "t-owner", "m1", 2, "2026-11-04T00:00:00Z"),
                    storedDose("start", "owner", "t-boundary", "m3", 1, "2026-11-03T03:00:00Z"),
                    storedDose("before", "owner", "t-boundary", "m3", 1, "2026-11-03T02:59:59.999Z"),
                    storedDose("end", "owner", "t-boundary", "m3", 1, "2026-11-04T03:00:00Z"),
                    storedDose("foreign", "other", "t-foreign", "other-med", 1, "2026-11-03T11:00:00Z")));

                // O repository atual so cancela uma ocorrencia que ja foi persistida.
                planned.save(planned.findById("cancelled").orElseThrow().cancel());

                recorded.save(new RegistroDeDose("r-taken", "a-taken", "owner", DoseAction.TAKEN,
                    taken.getScheduledAt().plusSeconds(60), Instant.parse("2026-11-05T10:00:00Z")));
                recorded.save(new RegistroDeDose("r-skipped", "b-skipped", "owner", DoseAction.SKIPPED, null, reference));

                var sessions = new SessionService(new MongoSessionRepository(connection.getCollection("auth_sessions")));
                String ownerToken = sessions.create("owner");
                String otherToken = sessions.create("other");
                var extractor = new BearerTokenExtractor();
                var actualRouter = new Router(request -> extractor.extract(request).flatMap(sessions::resolve)
                    .map(session -> session.getUserId()));
                var actualService = new DoseHistoryService(planned, recorded, Clock.fixed(reference, ZoneOffset.UTC));
                actualRouter.register("GET", "/adherence/history", new HistoryHandler(actualService));

                int port;
                try (var available = new ServerSocket(0)) { port = available.getLocalPort(); }

                var server = new Servidor(port, actualRouter);
                var thread = new Thread(server::aceitadorDeConexao);
                thread.setDaemon(true);
                thread.start();

                try (var client = HttpClient.newHttpClient()) {

                    for (int attempt = 0; ; attempt++) {
                        try { get(client, port, query, null); break; }
                        catch (java.io.IOException error) {
                            if (attempt == 29) { throw error; }
                            Thread.sleep(100);
                        }
                    }

                    // Comparei os documentos para verificar que consultar nao grava nada.
                    var beforeReads = snapshot(connection);
                    assertEquals(401, get(client, port, query, null).statusCode());
                    assertEquals(401, get(client, port, query, "invalid").statusCode());
                    assertEquals(400, get(client, port, query + "&userId=other", ownerToken).statusCode());

                    var response = get(client, port, query, ownerToken);
                    assertEquals(200, response.statusCode());
                    var json = Document.parse(response.body());
                    var items = json.getList("items", Document.class);
                    assertEquals(reference.toString(), json.getString("referenceTime"));
                    assertEquals(List.of("start", "a-taken", "b-skipped", "missing", "future", "cancelled", "old20", "new21"),
                        items.stream().map(item -> item.getString("plannedDoseId")).toList());
                    assertEquals("TAKEN", items.get(1).getString("action"));
                    assertEquals("2026-11-05T10:00:00Z", items.get(1).getString("recordedAt"));
                    assertEquals("SKIPPED", items.get(2).getString("action"));
                    assertNull(items.get(3).get("action"));
                    assertNull(items.get(4).get("action"));
                    assertTrue(Instant.parse(items.get(3).getString("scheduledAt")).isBefore(reference));
                    assertTrue(Instant.parse(items.get(4).getString("scheduledAt")).isAfter(reference));
                    assertTrue(items.get(5).getBoolean("isCancelled"));
                    assertEquals("2026-11-03T23:00:00Z", items.get(6).getString("scheduledAt"));
                    assertEquals(1, items.get(6).getInteger("scheduleVersion"));
                    assertEquals("Nome historico", items.get(6).getString("medicationName"));
                    assertEquals(0.5, ((Number) items.get(6).get("plannedQuantity")).doubleValue());
                    assertNull(items.get(6).get("plannedUnit"));

                    assertEquals(6, resultItems(get(client, port, query + "&treatmentId=t-owner&medicationInUseId=m1", ownerToken)).size());
                    assertEquals(1, resultItems(get(client, port, query + "&medicationInUseId=m2", ownerToken)).size());
                    assertTrue(resultItems(get(client, port, query + "&treatmentId=t-foreign", ownerToken)).isEmpty());
                    assertEquals(List.of("foreign"), resultItems(get(client, port, query, otherToken)).stream()
                        .map(item -> item.getString("plannedDoseId")).toList());
                    assertTrue(resultItems(get(client, port, query + "&treatmentId=t-owner", otherToken)).isEmpty());
                    assertTrue(resultItems(get(client, port, "/adherence/history?from=2026-11-05&to=2026-11-05", ownerToken)).isEmpty());
                    assertEquals(beforeReads, snapshot(connection));

                    // Mudei o cadastro atual como dado de teste; as ocorrencias antigas devem continuar iguais.
                    var current = treatments.findById("t-owner").orElseThrow();
                    var changed = current.findMedicationById("m1").orElseThrow();
                    changed.setName("Nome alterado");
                    changed.addScheduleVersion(new CronogramaDose(List.of("22:00"), "2026-11-03", "America/Sao_Paulo"));
                    current.addMedication(changed);
                    current.close(reference);
                    treatments.update(current);

                    var afterClosing = snapshot(connection);
                    assertEquals(items, resultItems(get(client, port, query, ownerToken)));
                    assertEquals(afterClosing, snapshot(connection));

                    try (var reopened = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
                        assertEquals(8, new DoseHistoryService(new MongoPlannedDoseRepository(reopened.getDatabase()),
                            new MongoDoseRecordRepository(reopened.getDatabase())).getHistory(new AuthContext("owner"),
                                DoseHistoryPeriod.parse("2026-11-03", "2026-11-03"), null, null).items().size());
                    }
                } finally {
                    server.parar();
                    thread.join(3000);
                    assertFalse(thread.isAlive());
                }
            } finally {
                connection.getDatabase().drop();
            }
        }
    }

    private DosePrevista storedDose(String id, String user, String treatment, String medication, int version, String time) {

        return new DosePrevista(id, user, treatment, medication, "Nome historico", version,
            Instant.parse(time), "America/Sao_Paulo", new BigDecimal("0.5"), ScheduleStatus.SCHEDULED);
    }

    private java.net.http.HttpResponse<String> get(HttpClient client, int port, String path, String token) throws Exception {

        var request = java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10)).GET();

        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }

        return client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private List<Document> resultItems(java.net.http.HttpResponse<String> response) {

        assertEquals(200, response.statusCode());
        return Document.parse(response.body()).getList("items", Document.class);
    }

    private Map<String, List<Document>> snapshot(MongoConnection connection) {

        Map<String, List<Document>> documents = new HashMap<>();

        for (String collection : List.of("planned_doses", "dose_records", "treatments", "auth_sessions")) {
            documents.put(collection, connection.getCollection(collection).find()
                .sort(new Document("_id", 1)).into(new ArrayList<>()));
        }

        return documents;
    }

    private final DoseHistoryService service = mock(DoseHistoryService.class);
    private final HistoryHandler handler = new HistoryHandler(service);
    private final Router router = new Router(request -> Optional.of("owner"));
    private final String query = "/adherence/history?from=2026-11-03&to=2026-11-03";

    DoseHistoryIntegrationTest() {

        router.register("GET", "/adherence/history", handler);
    }

    @Test
    void serializesHistoryAndEmptyResultsUsingTheAuthenticatedIdentity() {

        var time = Instant.parse("2026-11-03T15:00:00Z");
        String name = "Medicamento \"X\"\\teste\n\t\u0001";
        var taken = new DoseHistoryItem("a", "t&1", "m", name, 1, time, "America/Sao_Paulo",
            new BigDecimal("0.1234567890123456789"), null, DoseAction.TAKEN, time, time, false);
        var cancelled = new DoseHistoryItem("b", "t&1", "m", null, 2, time, "America/Sao_Paulo",
            BigDecimal.ONE, null, null, null, null, true);
        var day = LocalDate.parse("2026-11-03");

        when(service.getHistory(any(), any(), eq("t&1"), eq("m")))
            .thenReturn(new DoseHistoryResponse(day, day, "America/Sao_Paulo", time, List.of(taken, cancelled)))
            .thenReturn(new DoseHistoryResponse(day, day, "America/Sao_Paulo", time, List.of()));

        var request = new HttpRequest("GET", query + "&treatmentId=t%261&medicationInUseId=m");
        var response = router.route(request);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"plannedQuantity\":0.1234567890123456789"));

        var json = Document.parse(response.getBody());
        var items = json.getList("items", Document.class);

        assertEquals("2026-11-03", json.getString("from"));
        assertEquals(time.toString(), json.getString("referenceTime"));
        assertEquals("America/Sao_Paulo", json.getString("timezone"));
        assertEquals(name, items.get(0).getString("medicationName"));
        assertEquals("TAKEN", items.get(0).getString("action"));
        assertNull(items.get(0).get("plannedUnit"));
        assertTrue(items.get(1).getBoolean("isCancelled"));

        for (String field : List.of("medicationName", "action", "actualTime", "recordedAt")) {
            assertTrue(items.get(1).containsKey(field));
            assertNull(items.get(1).get(field));
        }

        assertTrue(Document.parse(router.route(request).getBody()).getList("items", Document.class).isEmpty());

        verify(service, times(2)).getHistory(new AuthContext("owner"),
            DoseHistoryPeriod.parse("2026-11-03", "2026-11-03"), "t&1", "m");
        verifyNoMoreInteractions(service);
    }

    @Test
    void rejectsMissingAuthenticationAndInvalidQueriesBeforeCallingTheService() {

        var unauthenticated = new Router(request -> Optional.empty());
        unauthenticated.register("GET", "/adherence/history", handler);

        assertEquals(401, unauthenticated.route(new HttpRequest("GET", query)).getStatusCode());
        assertEquals(401, handler.handle(new HttpRequest("GET", query)).getStatusCode());

        for (String invalid : List.of(
                "", "from=2026-11-03", "to=2026-11-03", "from=2026-02-30&to=2026-03-01",
                "from=2026-11-04&to=2026-11-03", "from=2026-01-01&to=2026-04-01",
                "from=2026-11-03&to=2026-11-03&from=2026-11-03",
                "from=2026-11-03&to=2026-11-03&fr%6Fm=2026-11-03",
                "from=2026-11-03&to=2026-11-03&userId=other",
                "from=2026-11-03&to=2026-11-03&treatmentId=",
                "from=2026-11-03&to=2026-11-03&medicationInUseId=+",
                "from=%GG&to=2026-11-03", "from=2026-11-03&to=2026-11-03&")) {

            assertEquals(400, router.route(new HttpRequest("GET", "/adherence/history?" + invalid)).getStatusCode(), invalid);
        }

        assertEquals(400, router.route(new HttpRequest("GET", query, "HTTP/1.1", Map.of(), " ")).getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void technicalFailuresAreNotReportedAsClientErrorsOrExposedInJson() {

        // Falhas do repository nao devem parecer erros nos parametros enviados.
        for (RuntimeException failure : List.of(new IllegalStateException("secret"), new IllegalArgumentException("secret"))) {

            doThrow(failure).when(service).getHistory(any(), any(), isNull(), isNull());
            var response = router.route(new HttpRequest("GET", query));

            assertEquals(500, response.getStatusCode());
            assertFalse(response.getBody().contains("secret"));
        }
    }
}
