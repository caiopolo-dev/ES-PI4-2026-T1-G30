package backend.tests;

import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicamentoEmUsoTest {
    @Test
    void separatesDoseQuantityAndUnitAndKeepsHistoryWhenDeactivated() {
        var schedule = new CronogramaDose(List.of("08:00"), "2026-11-17", "America/Sao_Paulo");
        var medication = new MedicamentoEmUso("med-1", "Demo", "50 mg", 1,
            " comprimido ", "Example", true, List.of(schedule));
        assertEquals("50 mg", medication.getDose());
        assertEquals(1, medication.getQuantity());
        assertEquals("comprimido", medication.getUnit());
        medication.validateForActivation();
        medication.setName("Updated demo");
        medication.deactivate();
        medication.deactivate();
        assertFalse(medication.isActive());
        assertEquals("med-1", medication.getId());
        assertEquals(List.of(schedule), medication.getSchedules());
    }

    @Test
    void legacyConstructorRemainsUsableButNeedsUnitBeforeActivation() {
        var medication = new MedicamentoEmUso(null, "Demo", "50 mg", 1, null);
        assertNotNull(medication.getId());
        assertTrue(medication.isActive());
        assertNull(medication.getUnit());
        assertThrows(IllegalStateException.class, medication::validateForActivation);
        medication.setUnit("mL");
        medication.validateForActivation();
        medication.setQuantity(null);
        assertThrows(IllegalStateException.class, medication::validateForActivation);
    }

    @Test
    void rejectsNonPositiveQuantityAndIncludesNewFieldsInEquality() {
        var medication = new MedicamentoEmUso("m", "Demo", "50 mg", 1, null);
        assertThrows(IllegalArgumentException.class, () -> medication.setQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> medication.setQuantity(-1));
        assertThrows(IllegalArgumentException.class, () -> new MedicamentoEmUso("m", "Demo", "50 mg", 0, null));
        var same = new MedicamentoEmUso("m", "Demo", "50 mg", 1, null);
        assertEquals(medication, same);
        medication.setUnit("comprimido");
        assertNotEquals(medication, same);
        same.setUnit("comprimido");
        assertEquals(medication, same);
        medication.deactivate();
        assertNotEquals(medication, same);
    }
}
