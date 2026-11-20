
package backend.tests;

import backend.models.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TreatmentTest {

    // Cria um tratamento em DRAFT para os testes
    private Treatment criarRascunho() {
        return new Treatment(
                "usuario-1",
                "Tratamento A",
                "Observações",
                LocalDate.of(2026, 11, 19),
                null
        );
    }

    // Cria um medicamento com cronograma diário
    private MedicamentoEmUso criarMedicamento() {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00", "20:00"),
                "2026-11-19",
                "America/Sao_Paulo"
        );

        return new MedicamentoEmUso(
                "med-1",
                "Medicamento A",
                "1 comprimido",
                cronograma
        );
    }

    // Cria um tratamento válido e ativa
    private Treatment criarTratamentoAtivo() {

        Treatment treatment = criarRascunho();
        treatment.addMedication(criarMedicamento());
        treatment.activate();

        return treatment;
    }

    @Test
    void devePermitirRascunhoIncompleto() {

        Treatment treatment = new Treatment(
                "usuario-1", null, null, null, null
        );

        assertEquals(TreatmentStatus.DRAFT, treatment.getStatus());
    }

    @Test
    void naoDeveAtivarSemMedicamentos() {

        Treatment treatment = criarRascunho();

        assertThrows(
                IllegalArgumentException.class,
                treatment::activate
        );

        assertEquals(TreatmentStatus.DRAFT, treatment.getStatus());
    }

    @Test
    void deveAtivarTratamentoValido() {

        Treatment treatment = criarTratamentoAtivo();

        assertEquals(TreatmentStatus.ACTIVE, treatment.getStatus());
    }

    @Test
    void naoDeveAtivarDuasVezes() {

        Treatment treatment = criarTratamentoAtivo();

        assertThrows(
                IllegalStateException.class,
                treatment::activate
        );
    }

    @Test
    void deveEncerrarTratamento() {

        Treatment treatment = criarTratamentoAtivo();
        Instant instante = Instant.parse("2026-11-19T15:00:00Z");

        treatment.close(instante);

        assertEquals(TreatmentStatus.CLOSED, treatment.getStatus());
        assertEquals(instante, treatment.getClosedAt());

        // Os medicamentos devem permanecer armazenados
        assertEquals(1, treatment.getMedications().size());
    }

    @Test
    void naoDeveReativarTratamentoEncerrado() {

        Treatment treatment = criarTratamentoAtivo();
        treatment.close(Instant.now());

        assertThrows(
                IllegalStateException.class,
                treatment::activate
        );
    }

    @Test
    void naoDeveAceitarPeriodoInvalido() {

        Treatment treatment = criarRascunho();
        LocalDate dataOriginal = treatment.getStartDate();

        assertThrows(
                IllegalArgumentException.class,
                () -> treatment.updatePeriod(
                        LocalDate.of(2026, 12, 10),
                        LocalDate.of(2026, 12, 1)
                )
        );

        // A tentativa inválida não pode modificar o período
        assertEquals(dataOriginal, treatment.getStartDate());
    }

    @Test
    void naoDeveAlterarMedicamentoPorReferencia() {

        Treatment treatment = criarTratamentoAtivo();
        treatment.close(Instant.now());

        // Modifica uma cópia retornada pelo getter
        MedicamentoEmUso copia = treatment.getMedications().get(0);
        copia.setName("Nome alterado");

        // O medicamento original deve permanecer igual
        assertEquals(
                "Medicamento A",
                treatment.getMedications().get(0).getName()
        );
    }

    @Test
    void naoDeveAlterarTratamentoEncerrado() {

        Treatment treatment = criarTratamentoAtivo();
        treatment.close(Instant.now());

        assertThrows(
                IllegalStateException.class,
                () -> treatment.setName("Novo nome")
        );

        assertThrows(
                IllegalStateException.class,
                () -> treatment.addMedication(criarMedicamento())
        );
    }
}
