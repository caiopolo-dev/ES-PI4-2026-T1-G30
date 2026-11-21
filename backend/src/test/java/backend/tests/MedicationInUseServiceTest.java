package backend.tests;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.repositories.MedicationInUseRepository;
import backend.services.MedicationInUseService;
import backend.services.TreatmentMedicationRules;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicationInUseServiceTest {
    @Test
    void preparesQuantityChangeThroughScheduleServiceWithoutWriting() {
        var repository = new TestRepository();
        var schedules = org.mockito.Mockito.mock(backend.services.ScheduleService.class);
        var effective = java.time.Instant.now().plusSeconds(86400);
        var previousSchedule = new CronogramaDose(List.of("08:00"), "2026-11-19", "UTC")
            .withValidity(effective.minusSeconds(172800), null);
        repository.add("t", "a", new MedicamentoEmUso("m", "Demo", "50 mg", 1,
            "comprimido", null, true, List.of(previousSchedule)));
        var history = List.of(previousSchedule.withActive(false).withValidity(previousSchedule.getValidFrom(), effective),
            previousSchedule.withVersion(2).withValidity(effective, null));
        org.mockito.Mockito.when(schedules.prepararTransicao(a, "t", "m", previousSchedule, effective)).thenReturn(history);
        var service = new MedicationInUseService(repository, new TestRules(), schedules);
        int writes = repository.writes;
        var prepared = service.prepareVersionedUpdate(a, "t", "m", "Demo", "50 mg", 2, "mL", null, 0, effective);
        assertEquals(1, prepared.previous().getQuantity());
        assertEquals("comprimido", prepared.previous().getUnit());
        assertEquals(2, prepared.updated().getQuantity());
        assertEquals("mL", prepared.updated().getUnit());
        assertEquals(List.of(
                history.get(0).withPosologySnapshot(1, "comprimido"),
                history.get(1).withPosologySnapshot(2, "mL")
        ), prepared.updated().getSchedules());
        assertEquals(0L, prepared.updated().getRevision());
        assertEquals(effective, prepared.validFrom());
        assertEquals(List.of(previousSchedule), service.find(a, "t", "m").getSchedules());
        assertEquals(1, service.find(a, "t", "m").getQuantity());
        assertEquals(writes, repository.writes);
        org.mockito.Mockito.verify(schedules).prepararTransicao(a, "t", "m", previousSchedule, effective);
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> service.prepareVersionedUpdate(a, "t", "m", "Demo", "50 mg", 2, "mL", null, 1, effective));
        assertThrows(ResourceNotFoundException.class,
            () -> service.prepareVersionedUpdate(b, "t", "m", "Demo", "50 mg", 2, "mL", null, 0, effective));
        assertThrows(IllegalArgumentException.class,
            () -> service.prepareVersionedUpdate(a, "t", "m", "Demo", "50 mg", 2, "mL", null, 0,
                java.time.Instant.now().minusSeconds(60)));
        assertEquals(writes, repository.writes);
    }

    @Test
    void treatmentRulesUseRealIdentityAndStates() {
        var repository = org.mockito.Mockito.mock(backend.repositories.TreatmentRepository.class);
        var owner = new AuthContext("owner");
        var rules = new backend.services.DefaultTreatmentMedicationRules(
            new backend.services.TreatmentService(repository));
        var schedule = new CronogramaDose(List.of("08:00"), "2026-11-19", "America/Sao_Paulo");
        var medication = new MedicamentoEmUso("m", "Demo", "50 mg", 1, "mL", null, true, List.of(schedule));
        var incomplete = new MedicamentoEmUso("new", "Demo", "50 mg", null, null, null, true, List.of());
        var draft = new backend.models.Treatment("t", "owner", "Demo", null,
            java.time.LocalDate.parse("2026-11-19"), null, backend.models.TreatmentStatus.DRAFT, null, List.of(medication));
        org.mockito.Mockito.when(repository.findByIdAndUserId("t", "owner")).thenReturn(Optional.of(draft));
        org.mockito.Mockito.when(repository.findByIdAndUserId("t", "other")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> rules.validateAddition(owner, "t", incomplete));
        assertDoesNotThrow(() -> rules.validateDeactivation(owner, "t", medication));
        assertEquals(0, rules.minimumRemainingActiveMedications(owner, "t"));
        assertThrows(ResourceNotFoundException.class,
            () -> rules.validateAddition(new AuthContext("other"), "t", medication));
        draft.activate();
        assertDoesNotThrow(() -> rules.validateUpdate(owner, "t", medication));
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> rules.validateAddition(owner, "t", incomplete));
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> rules.validateUpdate(owner, "t", incomplete));
        assertEquals(1, rules.minimumRemainingActiveMedications(owner, "t"));
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> rules.validateDeactivation(owner, "t", medication));
        var other = new MedicamentoEmUso("other", "Demo", "50 mg", 1, "mL", null, true, List.of(schedule));
        draft.addMedication(other);
        assertDoesNotThrow(() -> rules.validateDeactivation(owner, "t", medication));
        draft.close(java.time.Instant.now());
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> rules.validateAddition(owner, "t", medication));
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> rules.validateUpdate(owner, "t", medication));
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> rules.validateDeactivation(owner, "t", medication));
    }

    private final AuthContext a = new AuthContext("a");
    private final AuthContext b = new AuthContext("b");

    @Test
    void keepsLastActiveMedicationWhenRequiredByTreatmentRules() {
        var repository = new TestRepository();
        var rules = new TestRules();
        rules.minimum = 1;
        var service = new MedicationInUseService(repository, rules);
        var first = service.add(a, "t", "First", "50 mg", 1, "mL", null);
        var second = service.add(a, "t", "Second", "50 mg", 1, "mL", null);
        service.deactivate(a, "t", first.getId());
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> service.deactivate(a, "t", second.getId()));
        assertTrue(service.find(a, "t", second.getId()).isActive());
        rules.minimum = 0;
        service.deactivate(a, "t", second.getId());
        assertFalse(service.find(a, "t", second.getId()).isActive());
    }

    @Test
    void staleClientRevisionDoesNotOverwriteCurrentMedication() {
        var repository = new TestRepository();
        var service = new MedicationInUseService(repository, new TestRules());
        var created = service.add(a, "t", "Demo", "50 mg", 1, "comprimido", null);
        var first = service.update(a, "t", created.getId(), "First", "50 mg", 1, "comprimido", null, 0);
        assertEquals(1, first.getRevision());
        int before = repository.writes;
        assertThrows(backend.services.MedicationOperationConflictException.class,
            () -> service.update(a, "t", created.getId(), "Stale", "50 mg", 1, "comprimido", null, 0));
        assertEquals(before, repository.writes);
        assertEquals("First", service.find(a, "t", created.getId()).getName());
        var next = service.update(a, "t", created.getId(), "Next", "50 mg", 1, "comprimido", null, 1);
        assertEquals(2, next.getRevision());
    }

    @Test
    void createsEditsAndDeactivatesOwnMedicationWithStableId() {
        var repository = new TestRepository();
        var rules = new TestRules();
        var service = new MedicationInUseService(repository, rules);
        var created = service.add(a, "t", "Demo", "50 mg", 1, "comprimido", null);
        var edited = service.update(a, "t", created.getId(), "Updated", "50 mg", 2, "comprimido", "Notes");
        assertEquals(created.getId(), edited.getId());
        assertEquals(2, service.find(a, "t", created.getId()).getQuantity());
        service.deactivate(a, "t", created.getId());
        service.deactivate(a, "t", created.getId());
        assertFalse(service.find(a, "t", created.getId()).isActive());
        assertEquals(3, repository.writes);
        assertEquals(List.of("add", "update", "deactivate", "deactivate"), rules.calls);
    }

    @Test
    void deniesForeignAndWrongTreatmentOperationsWithoutWriting() {
        var repository = new TestRepository();
        var service = new MedicationInUseService(repository, new TestRules());
        var created = service.add(a, "t", "Demo", "50 mg", 1, "comprimido", null);
        int before = repository.writes;
        assertThrows(ResourceNotFoundException.class, () -> service.find(b, "t", created.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.add(b, "t", "Demo", "50 mg", 1, "mL", null));
        assertThrows(ResourceNotFoundException.class, () -> service.update(b, "t", created.getId(), "Other", "50 mg", 1, "mL", null));
        assertThrows(ResourceNotFoundException.class, () -> service.deactivate(b, "t", created.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.find(a, "other", created.getId()));
        assertEquals(before, repository.writes);
        assertTrue(service.find(a, "t", created.getId()).isActive());
    }

    @Test
    void treatmentRulesAreMandatoryAndCanDenyEveryWrite() {
        var repository = new TestRepository();
        var rules = new TestRules();
        var service = new MedicationInUseService(repository, rules);
        var created = service.add(a, "t", "Demo", "50 mg", 1, "comprimido", null);
        rules.deny = true;
        int before = repository.writes;
        assertThrows(IllegalStateException.class, () -> service.add(a, "t", "Demo", "50 mg", 1, "mL", null));
        assertThrows(IllegalStateException.class, () -> service.update(a, "t", created.getId(), "Other", "50 mg", 1, "mL", null));
        assertThrows(IllegalStateException.class, () -> service.deactivate(a, "t", created.getId()));
        assertEquals(before, repository.writes);
        assertThrows(NullPointerException.class, () -> new MedicationInUseService(repository, null));
        assertThrows(NullPointerException.class, () -> service.find(null, "t", created.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.add(a, "t", "Demo", "50 mg", 0, "mL", null));
    }

    @Test
    void preservesSchedulesAndBlocksChangesThatRequireVersioning() {
        var repository = new TestRepository();
        var service = new MedicationInUseService(repository, new TestRules());
        var schedule = new CronogramaDose(List.of("08:00"), "2026-11-18", "America/Sao_Paulo");
        repository.add("t", "a", new MedicamentoEmUso("m", "Demo", "50 mg", 1,
            "comprimido", null, true, List.of(schedule)));
        int before = repository.writes;
        assertThrows(IllegalStateException.class, () -> service.update(a, "t", "m", "Demo", "50 mg", 2, "comprimido", null));
        assertThrows(IllegalStateException.class, () -> service.update(a, "t", "m", "Demo", "50 mg", 1, "mL", null));
        assertEquals(before, repository.writes);
        var updated = service.update(a, "t", "m", "Renamed", "50 mg", 1, "comprimido", "Notes");
        assertEquals(List.of(schedule), updated.getSchedules());
        service.deactivate(a, "t", "m");
        assertEquals(List.of(schedule), service.find(a, "t", "m").getSchedules());
    }

    // Implementacoes exclusivas de teste; nao representam as regras da #26.
    private static class TestRules implements TreatmentMedicationRules {
        final List<String> calls = new ArrayList<>();
        boolean deny;
        int minimum;
        private void check(String action) {
            calls.add(action);
            if (deny) { throw new IllegalStateException("Operacao negada pelas regras do tratamento."); }
        }
        public void validateAddition(AuthContext context, String treatmentId, MedicamentoEmUso medication) { check("add"); }
        public void validateUpdate(AuthContext context, String treatmentId, MedicamentoEmUso medication) { check("update"); }
        public void validateDeactivation(AuthContext context, String treatmentId, MedicamentoEmUso medication) { check("deactivate"); }
        public int minimumRemainingActiveMedications(AuthContext context, String treatmentId) { return minimum; }
    }

    private static class TestRepository implements MedicationInUseRepository {
        final Map<String, MedicamentoEmUso> medications = new HashMap<>();
        int writes;
        public boolean add(String treatmentId, String userId, MedicamentoEmUso medication) {
            if (!treatmentId.equals("t") || !userId.equals("a") || medications.containsKey(medication.getId())) { return false; }
            medications.put(medication.getId(), medication); writes++; return true;
        }
        public Optional<MedicamentoEmUso> find(String treatmentId, String userId, String medicationId) {
            return treatmentId.equals("t") && userId.equals("a") ? Optional.ofNullable(medications.get(medicationId)) : Optional.empty();
        }
        public boolean update(String treatmentId, String userId, MedicamentoEmUso medication) {
            if (find(treatmentId, userId, medication.getId()).isEmpty()) { return false; }
            if (medications.get(medication.getId()).getRevision() != medication.getRevision()) { return false; }
            medications.put(medication.getId(), medication.withRevision(medication.getRevision() + 1)); writes++; return true;
        }
        public boolean deactivate(String treatmentId, String userId, String medicationId, long revision, int minimumRemainingActive) {
            var medication = find(treatmentId, userId, medicationId);
            if (medication.isEmpty()) { return false; }
            if (medication.get().getRevision() != revision) { return false; }
            if (medications.values().stream().filter(MedicamentoEmUso::isActive).count() <= minimumRemainingActive) { return false; }
            medication.get().deactivate();
            medications.put(medicationId, medication.get().withRevision(revision + 1)); writes++; return true;
        }
    }
}
