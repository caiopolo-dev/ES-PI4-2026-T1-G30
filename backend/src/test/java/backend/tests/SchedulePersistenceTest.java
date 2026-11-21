package backend.tests;

import backend.auth.AuthContext;
import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.models.ScheduleFrequency;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.TreatmentRepository;
import backend.services.MedicationOperationConflictException;
import backend.services.ScheduleService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchedulePersistenceTest {
    private final AuthContext context = new AuthContext("user-1");

    @Test
    void devePersistirVersoesEIncrementarRevisao() {
        InMemoryScheduleRepository repository = new InMemoryScheduleRepository(
                treatment(true, 0L, List.of()));
        ScheduleService service = new ScheduleService(repository);
        Instant firstStart = Instant.parse("2026-11-10T12:00:00Z");
        Instant secondStart = Instant.parse("2026-11-20T12:00:00Z");

        MedicamentoEmUso first = service.salvar(
                context, "treatment-1", "med-1", schedule("08:00"), firstStart, 0L);
        MedicamentoEmUso second = service.salvar(
                context, "treatment-1", "med-1", schedule("09:00"), secondStart, 1L);

        assertEquals(1L, first.getRevision());
        assertEquals(2L, second.getRevision());
        assertEquals(2, second.getSchedules().size());
        assertEquals(secondStart, second.getSchedules().get(0).getValidTo());
        assertFalse(second.getSchedules().get(0).isActive());
        assertTrue(second.getSchedules().get(1).isActive());
        assertEquals(2, repository.writes);
    }

    @Test
    void requisicaoIdenticaNaoDeveDuplicarVersao() {
        InMemoryScheduleRepository repository = new InMemoryScheduleRepository(
                treatment(true, 0L, List.of()));
        ScheduleService service = new ScheduleService(repository);
        Instant start = Instant.parse("2026-11-10T12:00:00Z");

        service.salvar(context, "treatment-1", "med-1", schedule("08:00"), start, 0L);
        MedicamentoEmUso repeated = service.salvar(
                context, "treatment-1", "med-1", schedule("08:00"), start, 0L);

        assertEquals(1, repeated.getSchedules().size());
        assertEquals(1L, repeated.getRevision());
        assertEquals(1, repository.writes);
    }

    @Test
    void revisaoDesatualizadaDeveGerarConflito() {
        InMemoryScheduleRepository repository = new InMemoryScheduleRepository(
                treatment(true, 0L, List.of()));
        ScheduleService service = new ScheduleService(repository);
        service.salvar(context, "treatment-1", "med-1", schedule("08:00"),
                Instant.parse("2026-11-10T12:00:00Z"), 0L);

        assertThrows(MedicationOperationConflictException.class, () -> service.salvar(
                context, "treatment-1", "med-1", schedule("09:00"),
                Instant.parse("2026-11-20T12:00:00Z"), 0L));
        assertEquals(1, repository.writes);
    }

    @Test
    void deveConsultarVersaoPelaVigenciaMesmoQuandoHistorica() {
        Instant firstStart = Instant.parse("2026-11-10T12:00:00Z");
        Instant secondStart = Instant.parse("2026-11-20T12:00:00Z");
        CronogramaDose first = schedule("08:00")
                .withValidity(firstStart, secondStart).deactivate();
        CronogramaDose second = schedule("09:00")
                .withVersion(2).withValidity(secondStart, null);
        ScheduleService service = new ScheduleService(new InMemoryScheduleRepository(
                treatment(true, 2L, List.of(first, second))));

        CronogramaDose current = service.consultarNoInstante(
                context, "treatment-1", "med-1",
                Instant.parse("2026-11-15T12:00:00Z")).orElseThrow();
        CronogramaDose futureAtBoundary = service.consultarNoInstante(
                context, "treatment-1", "med-1", secondStart).orElseThrow();

        assertEquals(1, current.getVersion());
        assertEquals(List.of("08:00"), current.getTimes());
        assertFalse(current.isActive());
        assertEquals(2, futureAtBoundary.getVersion());
        assertTrue(futureAtBoundary.isActive());
        assertTrue(service.consultarNoInstante(
                context, "treatment-1", "med-1",
                Instant.parse("2026-11-01T12:00:00Z")).isEmpty());
    }

    @Test
    void requisicaoIdenticaDeveRejeitarHistoricoInconsistente() {
        Instant firstStart = Instant.parse("2026-11-10T12:00:00Z");
        Instant secondStart = Instant.parse("2026-11-20T12:00:00Z");
        CronogramaDose overlapping = schedule("07:00")
                .withValidity(firstStart, Instant.parse("2026-12-01T12:00:00Z"))
                .deactivate();
        CronogramaDose current = schedule("08:00")
                .withVersion(2).withValidity(secondStart, null);
        InMemoryScheduleRepository repository = new InMemoryScheduleRepository(
                treatment(true, 2L, List.of(overlapping, current)));
        ScheduleService service = new ScheduleService(repository);

        assertThrows(IllegalStateException.class, () -> service.salvar(
                context, "treatment-1", "med-1", schedule("08:00"),
                secondStart, 0L));
        assertEquals(0, repository.writes);
    }

    private CronogramaDose schedule(String time) {
        return new CronogramaDose(
                1, ScheduleFrequency.EVERY_DAY, null, List.of(time),
                "2026-11-01", "2026-12-31", "America/Sao_Paulo", true);
    }

    private Treatment treatment(boolean medicationActive, long revision, List<CronogramaDose> schedules) {
        MedicamentoEmUso medication = new MedicamentoEmUso(
                "med-1", "Medicamento", "1 comprimido", 1, "comprimido",
                null, medicationActive, schedules, revision);
        return new Treatment(
                "treatment-1", "user-1", "Tratamento", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
                TreatmentStatus.DRAFT, null, List.of(medication));
    }

    private static final class InMemoryScheduleRepository implements TreatmentRepository {
        private Treatment treatment;
        private int writes;

        private InMemoryScheduleRepository(Treatment treatment) {
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
                    || !treatment.getUserId().equals(userId)
                    || treatment.getStatus() == TreatmentStatus.CLOSED) {
                return false;
            }
            Optional<MedicamentoEmUso> found = treatment.findMedicationById(medicationId);
            if (found.isEmpty() || !found.get().isActive()
                    || found.get().getRevision() != expectedRevision) {
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
