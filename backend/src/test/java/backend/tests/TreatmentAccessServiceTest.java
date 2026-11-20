//Autores: Cauã Bianchi Ferroni, Miguel Batista Gallinucci
package backend.tests;

import backend.auth.AuthContext;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.TreatmentRepository;
import backend.services.TreatmentAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentAccessServiceTest {
    private TreatmentRepository repository;
    private TreatmentAccessService service;
    private final AuthContext a = new AuthContext("user-a");
    private final AuthContext b = new AuthContext("user-b");

    @BeforeEach
    void setup() {
        Map<String, Treatment> storage = new LinkedHashMap<>();
        repository = new TreatmentRepository() {
            private Treatment copy(Treatment t) {
                return new Treatment(
                    t.getId(), t.getUserId(), t.getName(), t.getDescription(),
                    t.getStartDate(), t.getEndDate(), t.getStatus(), t.getClosedAt(),
                    t.getMedications());
            }

            @Override
            public Treatment save(Treatment treatment) {
                Treatment stored = copy(treatment);
                storage.put(stored.getId(), stored);
                return copy(stored);
            }

            @Override
            public Optional<Treatment> findById(String id) {
                Treatment stored = storage.get(id);
                return stored == null ? Optional.empty() : Optional.of(copy(stored));
            }

            @Override
            public List<Treatment> findByUserId(String userId) {
                return storage.values().stream()
                    .filter(treatment -> userId.equals(treatment.getUserId()))
                    .map(this::copy)
                    .toList();
            }

            @Override
            public Treatment update(Treatment treatment) {
                if (!storage.containsKey(treatment.getId())) {
                    throw new NoSuchElementException();
                }
                Treatment stored = copy(treatment);
                storage.put(stored.getId(), stored);
                return copy(stored);
            }
        };

        Treatment first = new Treatment(
            "treatment-a", "user-a", "Treatment A", null,
            null, null, TreatmentStatus.DRAFT, null, List.of());
        first.addMedication(new MedicamentoEmUso("med-a", "Medication A", "50 mg", 1, null));
        repository.save(first);
        Treatment second = new Treatment(
            "treatment-b", "user-b", "Treatment B", null,
            null, null, TreatmentStatus.DRAFT, null, List.of());
        second.addMedication(new MedicamentoEmUso("med-b", "Medication B", "20 mg", 1, null));
        repository.save(second);
        service = new TreatmentAccessService(repository);
    }

    @Test
    void listsAndReadsOnlyOwnTreatmentsAndMedications() {
        assertEquals(List.of("treatment-a"), service.list(a).stream().map(Treatment::getId).toList());
        assertEquals(List.of("treatment-b"), service.list(b).stream().map(Treatment::getId).toList());
        assertEquals("med-a", service.findMedication(a, "treatment-a", "med-a").getId());
        assertThrows(NoSuchElementException.class, () -> service.findMedication(b, "treatment-a", "med-a"));
        assertThrows(NoSuchElementException.class, () -> service.findMedication(a, "treatment-a", "med-b"));
    }

    @Test
    void deniesCrossUserUpdatesAndOwnershipChangesWithoutWriting() {
        Treatment foreign = service.find(a, "treatment-a");
        foreign.setName("Changed by B");
        assertThrows(NoSuchElementException.class, () -> service.update(b, foreign));
        Treatment forged = new Treatment(
            "treatment-a", "user-b", "Forged owner", null,
            null, null, TreatmentStatus.DRAFT, null, List.of());
        assertThrows(NoSuchElementException.class, () -> service.update(a, forged));
        assertEquals("Treatment A", service.find(a, "treatment-a").getName());
        assertEquals("user-a", service.find(a, "treatment-a").getUserId());
    }

    @Test
    void permitsOwnerUpdateAndDoesNotRevealForeignResourceExistence() {
        Treatment own = service.find(a, "treatment-a");
        own.setName("Updated A");
        service.update(a, own);
        assertEquals("Updated A", service.find(a, "treatment-a").getName());
        var missing = assertThrows(NoSuchElementException.class, () -> service.find(b, "missing"));
        var foreign = assertThrows(NoSuchElementException.class, () -> service.find(b, "treatment-a"));
        assertEquals(missing.getMessage(), foreign.getMessage());
        assertThrows(NullPointerException.class, () -> service.list(null));
    }
}
