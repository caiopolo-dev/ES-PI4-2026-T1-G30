package backend.tests;

import backend.config.MongoConnection;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.repositories.MongoMedicationInUseRepository;
import backend.repositories.MongoTreatmentRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

class MedicationInUseIntegrationTest {
    @Test
    void medicationRoutesDelegateOnlyWithAuthenticatedContext() {
        var service = org.mockito.Mockito.mock(backend.services.MedicationInUseService.class);
        var router = new backend.router.Router(request -> java.util.Optional.of("a"));
        new backend.handlers.MedicationInUseHandler(service).registerRoutes(router);
        var stored = medication("m", 1);
        org.mockito.Mockito.when(service.add(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("t"),
            org.mockito.ArgumentMatchers.eq("Demo"), org.mockito.ArgumentMatchers.eq("50 mg"), org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.eq("comprimido"), org.mockito.ArgumentMatchers.isNull())).thenReturn(stored);
        org.mockito.Mockito.when(service.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("t"),
            org.mockito.ArgumentMatchers.eq("m"), org.mockito.ArgumentMatchers.eq("Demo"), org.mockito.ArgumentMatchers.eq("50 mg"),
            org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq("comprimido"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(0L))).thenReturn(stored.withRevision(1));
        String body = "{\"name\":\"Demo\",\"informedDose\":\"50 mg\",\"quantityPerAdministration\":1,\"unit\":\"comprimido\"}";
        assertEquals(201, router.route(httpRequest("POST", "/treatments/t/medications", body)).getStatusCode());
        assertEquals(200, router.route(httpRequest("PUT", "/treatments/t/medications/m", body.substring(0, body.length() - 1) + ",\"revision\":0}")).getStatusCode());
        assertEquals(204, router.route(httpRequest("DELETE", "/treatments/t/medications/m", "")).getStatusCode());
        org.mockito.Mockito.verify(service).deactivate(org.mockito.ArgumentMatchers.argThat(context -> context.userId().equals("a")),
            org.mockito.ArgumentMatchers.eq("t"), org.mockito.ArgumentMatchers.eq("m"));
        var denied = new backend.router.Router();
        new backend.handlers.MedicationInUseHandler(service).registerRoutes(denied);
        assertEquals(401, denied.route(httpRequest("POST", "/treatments/t/medications", body)).getStatusCode());
        assertEquals(405, router.route(httpRequest("GET", "/treatments/t/medications/m", "")).getStatusCode());
        assertEquals(404, router.route(httpRequest("PUT", "/treatments/t/medications/m/extra", body)).getStatusCode());
        router.register("POST", "/treatments/fixed/medications", request -> backend.http.HttpResponse.ok("exact"));
        assertEquals("exact", router.route(httpRequest("POST", "/treatments/fixed/medications", body)).getBody());
    }

    @Test
    void medicationRoutesRejectInvalidPayloadAndHideInternalFailures() {
        var service = org.mockito.Mockito.mock(backend.services.MedicationInUseService.class);
        var router = new backend.router.Router(request -> java.util.Optional.of("a"));
        new backend.handlers.MedicationInUseHandler(service).registerRoutes(router);
        for (String body : List.of("", "invalid-json", "[]", "{}", "{\"name\":123}", "{\"userId\":\"b\"}",
                "{\"quantityPerAdministration\":0.5}", "{\"quantityPerAdministration\":0}",
                "{\"quantityPerAdministration\":-1}", "{\"active\":false}", "{\"schedules\":[]}")) {
            assertEquals(400, router.route(httpRequest("POST", "/treatments/t/medications", body)).getStatusCode(), body);
        }
        assertEquals(400, router.route(httpRequest("POST", "/treatments/t/medications?userId=b", "{}")).getStatusCode());
        assertEquals(400, router.route(httpRequest("DELETE", "/treatments/t/medications/m", "{}")).getStatusCode());
        assertEquals(400, router.route(new backend.http.HttpRequest("POST", "/treatments/t/medications",
            "HTTP/1.1", java.util.Map.of("Content-Type", "text/plain"), "{}")).getStatusCode());
        assertEquals(400, router.route(new backend.http.HttpRequest("POST", "/treatments/t/medications", "HTTP/1.1",
            java.util.Map.of(), "{}")).getStatusCode());
        String oversized = "{\"name\":\"Demo\",\"informedDose\":\"50 mg\",\"notes\":\"" + "\u00e9".repeat(9000) + "\"}";
        assertEquals(400, router.route(httpRequest("POST", "/treatments/t/medications", oversized)).getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(service);
        String body = "{\"name\":\"Demo\",\"informedDose\":\"50 mg\"}";
        org.mockito.Mockito.when(service.add(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
            .thenThrow(new IllegalStateException("secret database error"))
            .thenThrow(new backend.auth.ResourceNotFoundException())
            .thenThrow(new backend.services.MedicationOperationConflictException("secret rule"));
        var response = router.route(httpRequest("POST", "/treatments/t/medications", body));
        assertEquals(500, response.getStatusCode());
        assertFalse(response.getBody().contains("secret"));
        assertEquals(404, router.route(httpRequest("POST", "/treatments/t/medications", body)).getStatusCode());
        var conflict = router.route(httpRequest("POST", "/treatments/t/medications", body));
        assertEquals(409, conflict.getStatusCode());
        assertFalse(conflict.getBody().contains("secret"));
    }

    private backend.http.HttpRequest httpRequest(String method, String path, String body) {
        return new backend.http.HttpRequest(method, path, "HTTP/1.1", java.util.Map.of("Content-Type", "application/json"), body);
    }

    @Test
    @EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
    void medicationEndpointsWorkThroughRealHttpAndMongo() throws Exception {
        String database = "dosecerta_test_med_http_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
            try {
                var collection = connection.getCollection("treatments");
                collection.insertOne(new Document("_id", "t").append("userId", "a")
                    .append("name", "Demo").append("medications", List.of()));
                var repository = new MongoMedicationInUseRepository(collection);
                var deny = new java.util.concurrent.atomic.AtomicBoolean();
                // Politica exclusiva de teste: nao implementa os estados da #26.
                var rules = new backend.services.TreatmentMedicationRules() {
                    private void check() {
                        if (deny.get()) { throw new backend.services.MedicationOperationConflictException("Test policy denied"); }
                    }
                    public int minimumRemainingActiveMedications(backend.auth.AuthContext context, String id) { return 0; }
                    public void validateAddition(backend.auth.AuthContext context, String id, MedicamentoEmUso medication) { check(); }
                    public void validateUpdate(backend.auth.AuthContext context, String id, MedicamentoEmUso medication) { check(); }
                    public void validateDeactivation(backend.auth.AuthContext context, String id, MedicamentoEmUso medication) { check(); }
                };
                var sessions = new backend.services.SessionService(
                    new backend.repositories.MongoSessionRepository(connection.getCollection("auth_sessions")));
                String tokenA = sessions.create("a");
                String tokenB = sessions.create("b");
                var extractor = new backend.services.BearerTokenExtractor();
                var router = new backend.router.Router(request -> extractor.extract(request).flatMap(sessions::resolve)
                    .map(session -> session.getUserId()));
                new backend.handlers.MedicationInUseHandler(new backend.services.MedicationInUseService(repository, rules))
                    .registerRoutes(router);
                int port;
                try (var available = new java.net.ServerSocket(0)) { port = available.getLocalPort(); }
                var server = new backend.server.Servidor(port, router);
                Thread thread = new Thread(server::aceitadorDeConexao);
                thread.setDaemon(true);
                thread.start();
                try {
                    var client = java.net.http.HttpClient.newHttpClient();
                    for (int attempt = 0; ; attempt++) {
                        try { sendHttp(client, port, "POST", "/treatments/t/medications", null, "{}"); break; }
                        catch (java.io.IOException error) { if (attempt == 29) { throw error; } Thread.sleep(100); }
                    }
                    String path = "/treatments/t/medications";
                    String body = "{\"name\":\"Demo\",\"informedDose\":\"50 mg\",\"quantityPerAdministration\":1,\"unit\":\"comprimido\"}";
                    assertEquals(401, sendHttp(client, port, "POST", path, null, body).statusCode());
                    var created = sendHttp(client, port, "POST", path, tokenA, body);
                    assertEquals(201, created.statusCode());
                    String medicationId = Document.parse(created.body()).getString("id");
                    String item = path + "/" + medicationId;
                    Document before = collection.find().first();
                    assertEquals(404, sendHttp(client, port, "POST", path, tokenB, body).statusCode());
                    assertEquals(404, sendHttp(client, port, "PUT", item, tokenB, body.substring(0, body.length() - 1) + ",\"revision\":0}").statusCode());
                    assertEquals(404, sendHttp(client, port, "DELETE", item, tokenB, "").statusCode());
                    assertEquals(before, collection.find().first());
                    assertEquals(400, sendHttp(client, port, "PUT", item, tokenA, body.replace(":1,", ":0.5,")).statusCode());
                    var updated = sendHttp(client, port, "PUT", item, tokenA, body.replace(":1,", ":2,").replace("}", ",\"revision\":0}"));
                    assertEquals(200, updated.statusCode());
                    assertEquals(medicationId, Document.parse(updated.body()).getString("id"));
                    assertEquals(1L, ((Number) Document.parse(updated.body()).get("revision")).longValue());
                    assertEquals(409, sendHttp(client, port, "PUT", item, tokenA,
                        body.replace("}", ",\"revision\":0}")).statusCode());
                    assertEquals(400, sendHttp(client, port, "PUT", item, tokenA, body).statusCode());
                    try (var reopened = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
                        assertEquals(2, new MongoMedicationInUseRepository(reopened.getCollection("treatments"))
                            .find("t", "a", medicationId).orElseThrow().getQuantity());
                    }
                    deny.set(true);
                    assertEquals(409, sendHttp(client, port, "DELETE", item, tokenA, "").statusCode());
                    assertTrue(repository.find("t", "a", medicationId).orElseThrow().isActive());
                    deny.set(false);
                    assertEquals(204, sendHttp(client, port, "DELETE", item, tokenA, "").statusCode());
                    assertFalse(repository.find("t", "a", medicationId).orElseThrow().isActive());
                    assertEquals(409, sendHttp(client, port, "PUT", item, tokenA, body.replace("}", ",\"revision\":1}")).statusCode());
                    sessions.revoke(tokenA);
                    assertEquals(401, sendHttp(client, port, "DELETE", item, tokenA, "").statusCode());
                } finally {
                    server.parar();
                    thread.join(3000);
                    assertFalse(thread.isAlive());
                }
            } finally { connection.getDatabase().drop(); }
        }
    }

    private java.net.http.HttpResponse<String> sendHttp(java.net.http.HttpClient client, int port,
            String method, String path, String token, String body) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path))
            .timeout(java.time.Duration.ofSeconds(10)).header("Content-Type", "application/json")
            .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body));
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private MedicamentoEmUso medication(String id, int quantity) {
        return new MedicamentoEmUso(id, "Demo", "50 mg", quantity, "comprimido", null, true, List.of());
    }

    @Test
    @EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
    void concurrentWritesHaveOneWinnerAndKeepLastActiveMedication() throws Exception {
        String database = "dosecerta_test_med_race_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
            try {
                var collection = connection.getCollection("treatments");
                collection.insertOne(new Document("_id", "t").append("userId", "a").append("name", "Demo")
                    .append("medications", List.of()));
                var repository = new MongoMedicationInUseRepository(collection);
                assertTrue(repository.add("t", "a", medication("m1", 1)));
                assertTrue(repository.add("t", "a", medication("m2", 1)));
                CountDownLatch start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> { start.await(); return repository.update("t", "a", medication("m1", 2)) ? 1 : 0; });
                    var second = executor.submit(() -> { start.await(); return repository.update("t", "a", medication("m1", 3)) ? 1 : 0; });
                    start.countDown();
                    assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
                }
                assertEquals(1, repository.find("t", "a", "m1").orElseThrow().getRevision());
                assertFalse(repository.update("t", "a", medication("m1", 9)));
                CountDownLatch deactivateStart = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> { deactivateStart.await(); return repository.deactivate("t", "a", "m1", 1, 1) ? 1 : 0; });
                    var second = executor.submit(() -> { deactivateStart.await(); return repository.deactivate("t", "a", "m2", 0, 1) ? 1 : 0; });
                    deactivateStart.countDown();
                    assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
                }
                assertEquals(1, collection.find().first().getList("medications", Document.class).stream()
                    .filter(med -> !Boolean.FALSE.equals(med.get("active"))).count());
                for (String id : List.of("m1", "m2")) {
                    var current = repository.find("t", "a", id).orElseThrow();
                    if (current.isActive()) {
                        assertFalse(repository.deactivate("t", "a", id, current.getRevision(), 1));
                        // O contrato permite explicitamente ficar vazio quando o tratamento nao exige ativo.
                        assertTrue(repository.deactivate("t", "a", id, current.getRevision(), 0));
                    } else {
                        assertFalse(repository.update("t", "a", current));
                    }
                }
                assertEquals(2, collection.find().first().getList("medications", Document.class).size());
                assertTrue(repository.find("t", "a", "m1").orElseThrow().getSchedules().isEmpty());
                collection.insertOne(new Document("_id", "legacy").append("userId", "a").append("medications",
                    List.of(new Document("id", "old").append("name", "Demo").append("dose", "50 mg").append("quantity", 1)
                        .append("schedules", List.of()))));
                assertFalse(repository.deactivate("legacy", "a", "old", 0, 1));
                assertTrue(repository.update("legacy", "a", medication("old", 2)));
                assertEquals(1, repository.find("legacy", "a", "old").orElseThrow().getRevision());
                assertFalse(repository.update("legacy", "a", medication("old", 3)));
            } finally { connection.getDatabase().drop(); }
        }
    }

    @Test
    void existingTreatmentMappingKeepsUnitAndInactiveState() {
        var repository = new MongoTreatmentRepository();
        var medication = medication("m", 1);
        medication.deactivate();
        var treatment = new Treatment("t", "a", "Demo", List.of(medication));
        repository.save(treatment);
        assertEquals(medication, repository.findById("t").orElseThrow().getMedications().get(0));
        repository.update(treatment);
        assertEquals(medication, repository.findById("t").orElseThrow().getMedications().get(0));
    }

    @Test
    @EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
    void persistsAndUpdatesOnlyTheOwnedMedicationInMongo() throws Exception {
        String database = "dosecerta_test_med_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
            try {
                var collection = connection.getCollection("treatments");
                collection.insertOne(new Document("_id", "t").append("userId", "a")
                    .append("name", "Demo").append("medications", List.of()));
                var repository = new MongoMedicationInUseRepository(collection);
                assertTrue(repository.add("t", "a", medication("m1", 1)));
                assertTrue(repository.add("t", "a", medication("m2", 1)));
                assertFalse(repository.add("t", "a", medication("m1", 2)));
                Document before = collection.find().first();
                assertFalse(repository.add("t", "b", medication("m3", 1)));
                assertTrue(repository.find("t", "b", "m1").isEmpty());
                assertFalse(repository.update("t", "b", medication("m1", 2)));
                assertFalse(repository.deactivate("t", "b", "m1", 0, 0));
                assertFalse(repository.update("missing-treatment", "a", medication("m1", 2)));
                assertEquals(before, collection.find().first());
                collection.updateOne(new Document("medications.id", "m1"),
                    new Document("$set", new Document("medications.$.historicalMarker", "preserve")));
                CountDownLatch start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> { start.await(); return repository.update("t", "a", medication("m1", 2)); });
                    var second = executor.submit(() -> { start.await(); return repository.update("t", "a", medication("m2", 3)); });
                    start.countDown();
                    assertTrue(first.get(10, TimeUnit.SECONDS));
                    assertTrue(second.get(10, TimeUnit.SECONDS));
                }
                assertTrue(repository.deactivate("t", "a", "m1", 1, 1));
                try (var second = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
                    var reopened = new MongoMedicationInUseRepository(second.getCollection("treatments"));
                    var restored = reopened.find("t", "a", "m1").orElseThrow();
                    assertEquals(2, restored.getQuantity());
                    assertEquals("comprimido", restored.getUnit());
                    assertFalse(restored.isActive());
                    assertEquals(3, reopened.find("t", "a", "m2").orElseThrow().getQuantity());
                }
                var stored = collection.find().first().getList("medications", Document.class);
                assertEquals(2, stored.size());
                assertEquals("preserve", stored.get(0).getString("historicalMarker"));
            } finally { connection.getDatabase().drop(); }
        }
    }
}
