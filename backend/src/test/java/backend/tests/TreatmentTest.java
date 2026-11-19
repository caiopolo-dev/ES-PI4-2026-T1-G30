
package backend.tests;

import backend.models.Treatment;
import backend.models.TreatmentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class TreatmentTest {

    @Test
    void naoDeveAtivarTratamentoSemCronograma() {

        // Cria um tratamento em DRAFT sem medicamentos
        Treatment treatment = new Treatment(
                "usuario-1",
                "Tratamento A",
                null,
                LocalDate.of(2026, 11, 19),
                null
        );

        // A ativação deve ser rejeitada
        assertThrows(
                IllegalArgumentException.class,
                treatment::activate
        );

        // O tratamento deve continuar em DRAFT
        assertEquals(TreatmentStatus.DRAFT, treatment.getStatus());
    }
}
