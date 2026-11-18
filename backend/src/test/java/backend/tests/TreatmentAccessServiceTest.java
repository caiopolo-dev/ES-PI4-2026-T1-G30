package backend.tests;

import backend.auth.AuthContext;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.repositories.MongoTreatmentRepository;
import backend.services.TreatmentAccessService;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentAccessServiceTest {
    private MongoTreatmentRepository repository;
    private TreatmentAccessService service;
    private final AuthContext a = new AuthContext("user-a");
    private final AuthContext b = new AuthContext("user-b");

    @BeforeEach
    void setup() {
        repository = new MongoTreatmentRepository();
        Treatment first = new Treatment("treatment-a", "user-a", "Treatment A");
        first.addMedication(new MedicamentoEmUso("med-a", "Medication A", "50 mg", 1, null));
        repository.save(first);
        Treatment second = new Treatment("treatment-b", "user-b", "Treatment B");
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
        Treatment forged = new Treatment("treatment-a", "user-b", "Forged owner");
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
