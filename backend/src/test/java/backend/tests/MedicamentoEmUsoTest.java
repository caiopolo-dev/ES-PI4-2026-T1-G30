package backend.tests;

import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.repositories.MongoTreatmentRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicamentoEmUsoTest {
    @Test
    void distinguishesConcentrationQuantityAndUnitAndKeepsIdentity() {
        var medication = new MedicamentoEmUso(null, " Demo ", "50 mg", new BigDecimal("0.50"),
            " comprimido ", " notes ", true, List.of());
        String id = medication.getId();
        assertEquals("Demo", medication.getName());
        assertEquals("50 mg", medication.getDose());
        assertEquals(new BigDecimal("0.5"), medication.getQuantity());
        assertEquals("comprimido", medication.getUnit());
        medication.validateForActivation();
        medication.deactivate();
        medication.deactivate();
        assertFalse(medication.isActive());
        assertEquals(id, medication.getId());
    }

    @Test
    void permitsIncompleteDraftButRejectsInvalidSuppliedValues() {
        var draft = new MedicamentoEmUso(null, null, null, null, null, null, true, List.of());
        assertNotNull(draft.getId());
        assertThrows(IllegalStateException.class, draft::validateForActivation);
        assertThrows(IllegalArgumentException.class, () -> draft.setQuantity(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> draft.setQuantity(new BigDecimal("-0.5")));
        assertThrows(IllegalArgumentException.class, () -> draft.setName("a".repeat(201)));
        assertThrows(IllegalArgumentException.class, () -> draft.setUnit("a".repeat(51)));
        draft.setUnit(" ");
        assertNull(draft.getUnit());
    }

    @Test
    void documentMappingPreservesFractionUnitAndInactiveState() {
        var repository = new MongoTreatmentRepository();
        var medication = new MedicamentoEmUso("med", "Demo", "50 mg", new BigDecimal("0.5"),
            "comprimido", null, false, List.of());
        var treatment = new Treatment("t", "u", "Demo", List.of(medication));
        repository.save(treatment);
        var restored = repository.findById("t").orElseThrow().getMedications().get(0);
        assertEquals(medication, restored);
        repository.update(treatment);
        assertEquals(medication, repository.findById("t").orElseThrow().getMedications().get(0));
        var document = repository.toDocument(treatment);
        @SuppressWarnings("unchecked")
        var documents = (List<java.util.Map<String, Object>>) document.get("medications");
        documents.get(0).put("quantity", 2);
        documents.get(0).remove("unit");
        documents.get(0).remove("active");
        var legacy = repository.fromDocument(document).getMedications().get(0);
        assertEquals(new BigDecimal("2"), legacy.getQuantity());
        assertNull(legacy.getUnit());
        assertTrue(legacy.isActive());
    }
}
