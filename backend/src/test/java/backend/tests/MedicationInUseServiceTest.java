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
    private final AuthContext a = new AuthContext("a");
    private final AuthContext b = new AuthContext("b");

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
        private void check(String action) {
            calls.add(action);
            if (deny) { throw new IllegalStateException("Operacao negada pelas regras do tratamento."); }
        }
        public void validateAddition(AuthContext context, String treatmentId, MedicamentoEmUso medication) { check("add"); }
        public void validateUpdate(AuthContext context, String treatmentId, MedicamentoEmUso medication) { check("update"); }
        public void validateDeactivation(AuthContext context, String treatmentId, MedicamentoEmUso medication) { check("deactivate"); }
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
            medications.put(medication.getId(), medication); writes++; return true;
        }
        public boolean deactivate(String treatmentId, String userId, String medicationId) {
            var medication = find(treatmentId, userId, medicationId);
            if (medication.isEmpty()) { return false; }
            medication.get().deactivate(); writes++; return true;
        }
    }
}
