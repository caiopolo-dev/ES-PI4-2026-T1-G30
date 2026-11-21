package backend.tests;

import backend.handlers.ScheduleHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.TreatmentRepository;
import backend.router.Router;
import backend.services.ScheduleService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleHandlerTest {
    private InMemoryRepository repository;
    private Router router;

    @BeforeEach
    void setUp() {
        MedicamentoEmUso medication = new MedicamentoEmUso(
                "med-1", "Medicamento", "1 comprimido", 1, "comprimido",
                null, true, List.of(), 0L);
        Treatment treatment = new Treatment(
                "treatment-1", "user-1", "Tratamento", null,
                LocalDate.of(2020, 1, 1), LocalDate.of(2099, 12, 31),
                TreatmentStatus.DRAFT, null, List.of(medication));
        repository = new InMemoryRepository(treatment);
        ScheduleService service = new ScheduleService(repository);
        router = new Router(request -> Optional.of("user-1"));
        new ScheduleHandler(service).registerRoutes(router);
    }

    @Test
    void putEGetDevemExporConfiguracaoAtualEHistorico() {
        HttpResponse put = router.route(request("PUT", validBody()));
        HttpResponse get = router.route(new HttpRequest(
                "GET", path(), Map.of("Authorization", "Bearer token")));

        assertEquals(200, put.getStatusCode());
        assertEquals(200, get.getStatusCode());
        assertTrue(get.getBody().contains("\"current\""));
        assertTrue(get.getBody().contains("\"versions\""));
        assertTrue(get.getBody().contains("\"frequency\": \"EVERY_DAY\"")
                || get.getBody().contains("\"frequency\":\"EVERY_DAY\""));
        assertTrue(get.getBody().contains("\"revision\": 1")
                || get.getBody().contains("\"revision\":1"));
    }

    @Test
    void deveMapearValidacaoEConcorrencia() {
        HttpResponse invalid = router.route(request("PUT", validBody().replace("08:00", "8:00")));
        assertEquals(400, invalid.getStatusCode());

        assertEquals(200, router.route(request("PUT", validBody())).getStatusCode());
        String stale = validBody().replace("08:00", "09:00");
        assertEquals(409, router.route(request("PUT", stale)).getStatusCode());
    }

    @Test
    void deveSerIdempotenteEProtegerTitularidade() {
        assertEquals(200, router.route(request("PUT", validBody())).getStatusCode());
        assertEquals(200, router.route(request("PUT", validBody())).getStatusCode());
        assertEquals(1, repository.writes);

        Router otherUser = new Router(request -> Optional.of("user-2"));
        new ScheduleHandler(new ScheduleService(repository)).registerRoutes(otherUser);
        HttpResponse denied = otherUser.route(new HttpRequest(
                "GET", path(), Map.of("Authorization", "Bearer other")));
        assertEquals(404, denied.getStatusCode());
    }

    private HttpRequest request(String method, String body) {
        return new HttpRequest(
                method, path(), "HTTP/1.1",
                Map.of("Authorization", "Bearer token", "Content-Type", "application/json"),
                body);
    }

    private String path() {
        return "/treatments/treatment-1/medications/med-1/schedule";
    }

    private String validBody() {
        return "{"
                + "\"frequency\":\"EVERY_DAY\","
                + "\"times\":[\"08:00\"],"
                + "\"startDate\":\"2020-01-01\","
                + "\"endDate\":\"2099-12-31\","
                + "\"timezone\":\"America/Sao_Paulo\","
                + "\"validFrom\":\"2020-01-01T03:00:00Z\","
                + "\"revision\":0"
                + "}";
    }

    private static final class InMemoryRepository implements TreatmentRepository {
        private Treatment treatment;
        private int writes;

        private InMemoryRepository(Treatment treatment) {
            this.treatment = treatment;
        }

        @Override
        public Treatment save(Treatment value) {
            treatment = value;
            return value;
        }

        @Override
        public Optional<Treatment> findById(String id) {
            return treatment.getId().equals(id) ? Optional.of(treatment) : Optional.empty();
        }

        @Override
        public List<Treatment> findByUserId(String userId) {
            return treatment.getUserId().equals(userId) ? List.of(treatment) : List.of();
        }

        @Override
        public Treatment update(Treatment value) {
            treatment = value;
            return value;
        }

        @Override
        public boolean updateMedicationSchedules(
                String treatmentId, String userId, String medicationId,
                long expectedRevision, List<CronogramaDose> schedules) {
            if (!treatment.getId().equals(treatmentId)
                    || !treatment.getUserId().equals(userId)) {
                return false;
            }
            MedicamentoEmUso current = treatment.findMedicationById(medicationId).orElse(null);
            if (current == null || !current.isActive()
                    || current.getRevision() != expectedRevision) {
                return false;
            }

            List<MedicamentoEmUso> medications = new ArrayList<>();
            for (MedicamentoEmUso medication : treatment.getMedications()) {
                if (medication.getId().equals(medicationId)) {
                    medications.add(new MedicamentoEmUso(
                            medication.getId(), medication.getName(), medication.getDose(),
                            medication.getQuantity(), medication.getUnit(), medication.getObservations(),
                            medication.isActive(), schedules, expectedRevision + 1));
                } else {
                    medications.add(medication);
                }
            }
            treatment = new Treatment(
                    treatment.getId(), treatment.getUserId(), treatment.getName(),
                    treatment.getDescription(), treatment.getStartDate(), treatment.getEndDate(),
                    treatment.getStatus(), treatment.getClosedAt(), medications);
            writes++;
            return true;
        }
    }
}
