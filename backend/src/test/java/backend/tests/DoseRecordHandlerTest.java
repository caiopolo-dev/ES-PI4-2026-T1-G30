// Caio Ferreira Polo issue 32
package backend.tests;

import backend.auth.SessionAuthenticator;
import backend.handlers.DoseRecordHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.repositories.DoseRecordRepository;
import backend.repositories.PlannedDoseRepository;
import backend.router.Router;
import backend.services.DoseAccessService;
import backend.services.DoseService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DoseRecordHandlerTest {

    private static final Instant NOW =
            Instant.parse("2026-11-21T12:00:00Z");

    private MemoryPlannedRepository planned;
    private MemoryRecordRepository records;
    private Router router;

    @BeforeEach
    void setup() {
        planned = new MemoryPlannedRepository();
        records = new MemoryRecordRepository();

        planned.save(createDose(
                "dose-a", "user-a", ScheduleStatus.SCHEDULED));

        planned.save(createDose(
                "dose-b", "user-b", ScheduleStatus.SCHEDULED));

        planned.save(createDose(
                "cancelled", "user-a", ScheduleStatus.CANCELLED));

        DoseAccessService access =
                new DoseAccessService(planned, records);

        DoseService service = new DoseService(
                records,
                access,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        router = createRouter(service);
    }

    private DosePrevista createDose(
            String id, String userId, ScheduleStatus status) {

        return new DosePrevista(
                id,
                userId,
                "treatment-" + userId,
                "medication-" + id,
                "Medicamento ficticio",
                1,
                NOW.minusSeconds(300),
                "America/Sao_Paulo",
                BigDecimal.ONE,
                status
        );
    }

    private Router createRouter(DoseService service) {
        SessionAuthenticator authenticator =
                new SessionAuthenticator() {
                    @Override
                    public Optional<String> resolveAuthenticatedUserId(
                            HttpRequest request) {

                        String authorization =
                                request.getHeader("Authorization");

                        if ("Bearer token-a".equals(authorization)) {
                            return Optional.of("user-a");
                        }

                        if ("Bearer token-b".equals(authorization)) {
                            return Optional.of("user-b");
                        }

                        return Optional.empty();
                    }
                };

        Router result = new Router(authenticator);

        result.register(
                "PUT",
                "/doses/{id}/record",
                new DoseRecordHandler(service)
        );

        return result;
    }

    private HttpRequest request(
            String method,
            String path,
            String body,
            String authorization,
            String contentType) {

        Map<String, String> headers = new HashMap<>();

        if (authorization != null) {
            headers.put("Authorization", authorization);
        }

        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }

        return new HttpRequest(
                method,
                path,
                "HTTP/1.1",
                headers,
                body
        );
    }

    private HttpRequest validRequest(String body) {
        return request(
                "PUT",
                "/doses/dose-a/record",
                body,
                "Bearer token-a",
                "application/json"
        );
    }

    @Test
    void putTakenDeveRetornar200() {
        HttpResponse response = router.route(
                validRequest("{\"action\":\"TAKEN\"}")
        );

        assertEquals(200, response.getStatusCode());

        Document json = Document.parse(response.getBody());

        assertEquals(
                "dose-a",
                json.getString("plannedDoseId")
        );
        assertEquals("TAKEN", json.getString("action"));
        assertEquals(1, json.getInteger("scheduleVersion"));
        assertEquals(
                NOW.minusSeconds(300).toString(),
                json.getString("scheduledAt")
        );
        assertEquals(
                NOW.toString(),
                json.getString("actualTime")
        );
        assertEquals(
                NOW.toString(),
                json.getString("recordedAt")
        );

        assertEquals(1, records.countAll());
    }

    @Test
    void putSkippedDeveRetornar200SemActualTime() {
        HttpResponse response = router.route(
                validRequest("{\"action\":\"SKIPPED\"}")
        );

        assertEquals(200, response.getStatusCode());

        Document json = Document.parse(response.getBody());

        assertEquals("SKIPPED", json.getString("action"));
        assertNull(json.get("actualTime"));
        assertEquals(
                NOW.toString(),
                json.getString("recordedAt")
        );
        assertEquals(1, records.countAll());
    }

    @Test
    void retryHttpDeveRetornarMesmoRegistro() {
        HttpResponse first = router.route(
                validRequest("{\"action\":\"TAKEN\"}")
        );

        HttpResponse second = router.route(
                validRequest("{\"action\":\"TAKEN\"}")
        );

        assertEquals(200, first.getStatusCode());
        assertEquals(200, second.getStatusCode());
        assertEquals(first.getBody(), second.getBody());
        assertEquals(1, records.countAll());
    }

    @Test
    void acaoContraditoriaDeveRetornar409() {
        HttpResponse first = router.route(
                validRequest("{\"action\":\"TAKEN\"}")
        );

        HttpResponse second = router.route(
                validRequest("{\"action\":\"SKIPPED\"}")
        );

        assertEquals(200, first.getStatusCode());
        assertEquals(409, second.getStatusCode());
        assertEquals(1, records.countAll());

        Document error = Document.parse(second.getBody());
        assertNotNull(error.getString("error"));
    }

    @Test
    void semAutenticacaoDeveRetornar401() {
        HttpResponse response = router.route(
                request(
                        "PUT",
                        "/doses/dose-a/record",
                        "{\"action\":\"TAKEN\"}",
                        null,
                        "application/json"
                )
        );

        assertEquals(401, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void tokenInvalidoDeveRetornar401() {
        HttpResponse response = router.route(
                request(
                        "PUT",
                        "/doses/dose-a/record",
                        "{\"action\":\"TAKEN\"}",
                        "Bearer invalid-token",
                        "application/json"
                )
        );

        assertEquals(401, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void usuarioBDeveReceber404ParaDoseDeA() {
        HttpResponse response = router.route(
                request(
                        "PUT",
                        "/doses/dose-a/record",
                        "{\"action\":\"TAKEN\"}",
                        "Bearer token-b",
                        "application/json"
                )
        );

        assertEquals(404, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void doseInexistenteDeveRetornar404() {
        HttpResponse response = router.route(
                request(
                        "PUT",
                        "/doses/missing/record",
                        "{\"action\":\"TAKEN\"}",
                        "Bearer token-a",
                        "application/json"
                )
        );

        assertEquals(404, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void doseCanceladaDeveRetornar409() {
        HttpResponse response = router.route(
                request(
                        "PUT",
                        "/doses/cancelled/record",
                        "{\"action\":\"TAKEN\"}",
                        "Bearer token-a",
                        "application/json"
                )
        );

        assertEquals(409, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void payloadsInvalidosDevemRetornar400() {
        String[] invalidBodies = {
                "{}",
                "{\"action\":\"UNKNOWN\"}",
                "{\"action\":null}",
                "{\"action\":123}",
                "{\"action\":\"taken\"}",
                "{\"action\":\"TAKEN\",\"userId\":\"user-b\"}",
                "{\"action\":\"TAKEN\",\"actualTime\":\"2026-11-21T12:00:00Z\"}",
                "{\"action\":\"TAKEN\",\"recordedAt\":\"2026-11-21T12:00:00Z\"}",
                "{\"action\":\"TAKEN\",\"plannedDoseId\":\"dose-b\"}",
                "{\"action\":\"TAKEN\",\"scheduleVersion\":999}",
                "{json invalido",
                ""
        };

        for (String body : invalidBodies) {
            HttpResponse response = router.route(
                    validRequest(body)
            );

            assertEquals(
                    400,
                    response.getStatusCode(),
                    "Payload deveria ser rejeitado: " + body
            );
        }

        assertEquals(0, records.countAll());
    }

    @Test
    void contentTypeInvalidoDeveRetornar400() {
        HttpResponse response = router.route(
                request(
                        "PUT",
                        "/doses/dose-a/record",
                        "{\"action\":\"TAKEN\"}",
                        "Bearer token-a",
                        "text/plain"
                )
        );

        assertEquals(400, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void contentTypeAusenteDeveRetornar400() {
        HttpResponse response = router.route(
                request(
                        "PUT",
                        "/doses/dose-a/record",
                        "{\"action\":\"TAKEN\"}",
                        "Bearer token-a",
                        null
                )
        );

        assertEquals(400, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void bodyAcimaDe16KiBDeveRetornar400() {
        String body =
                "{\"action\":\"TAKEN\"}" + " ".repeat(17000);

        HttpResponse response = router.route(
                validRequest(body)
        );

        assertEquals(400, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void queryNaoSuportadaDeveRetornar400() {
        HttpResponse response = router.route(
                request(
                        "PUT",
                        "/doses/dose-a/record?userId=user-b",
                        "{\"action\":\"TAKEN\"}",
                        "Bearer token-a",
                        "application/json"
                )
        );

        assertEquals(400, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void metodoNaoPermitidoDeveRetornar405() {
        HttpResponse response = router.route(
                request(
                        "GET",
                        "/doses/dose-a/record",
                        "",
                        "Bearer token-a",
                        null
                )
        );

        assertEquals(405, response.getStatusCode());
        assertEquals(0, records.countAll());
    }

    @Test
    void erroInesperadoDeveRetornar500SemVazarDetalhes() {
        MemoryRecordRepository failingRecords =
                new MemoryRecordRepository() {
                    @Override
                    public synchronized Optional<RegistroDeDose>
                            findByPlannedDoseId(String id) {

                        throw new IllegalStateException(
                                "SEGREDO_INTERNO_SIMULADO");
                    }
                };

        DoseAccessService access =
                new DoseAccessService(planned, failingRecords);

        DoseService failingService = new DoseService(
                failingRecords,
                access,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        Router failingRouter = createRouter(failingService);

        HttpResponse response = failingRouter.route(
                validRequest("{\"action\":\"TAKEN\"}")
        );

        assertEquals(500, response.getStatusCode());
        assertFalse(
                response.getBody().contains("SEGREDO_INTERNO_SIMULADO")
        );
    }

    private static class MemoryPlannedRepository
            implements PlannedDoseRepository {

        private final Map<String, DosePrevista> data =
                new HashMap<>();

        @Override
        public DosePrevista save(DosePrevista dose) {
            data.put(dose.getId(), dose);
            return dose;
        }

        @Override
        public List<DosePrevista> saveAll(List<DosePrevista> doses) {
            List<DosePrevista> saved = new ArrayList<>();

            for (DosePrevista dose : doses) {
                saved.add(save(dose));
            }

            return saved;
        }

        @Override
        public Optional<DosePrevista> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public List<DosePrevista> findByUserAndDate(
                String userId, LocalDate date) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DosePrevista> findByTreatmentAndPeriod(
                String id, Instant start, Instant end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DosePrevista> findByUserAndPeriod(
                String id, Instant start, Instant end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countAll() {
            return data.size();
        }

        // Estes testes nao exercitam cancelamento; uma chamada inesperada deve falhar.
        @Override
        public long cancelScheduledForTreatmentFrom(String userId, String treatmentId, Instant start) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long cancelScheduledForMedicationVersionFrom(
                String userId, String treatmentId, String medicationId, int version, Instant start) {
            throw new UnsupportedOperationException();
        }
    }

    private static class MemoryRecordRepository
            implements DoseRecordRepository {

        private final Map<String, RegistroDeDose> data =
                new HashMap<>();

        @Override
        public synchronized RegistroDeDose save(
                RegistroDeDose record) {

            RegistroDeDose existing =
                    data.get(record.getPlannedDoseId());

            if (existing != null) {
                if (!existing.getUserId().equals(record.getUserId())
                        || existing.getAction() != record.getAction()) {
                    throw new IllegalStateException(
                            "Registro em conflito");
                }

                return existing;
            }

            data.put(record.getPlannedDoseId(), record);
            return record;
        }

        @Override
        public synchronized Optional<RegistroDeDose>
                findByPlannedDoseId(String id) {

            return Optional.ofNullable(data.get(id));
        }

        @Override
        public List<RegistroDeDose> findByPeriod(
                Instant start, Instant end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, RegistroDeDose> findByPlannedDoseIds(
                Collection<String> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized int countAll() {
            return data.size();
        }
    }
}
