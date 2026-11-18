package backend.tests;

import backend.models.DosePrevista;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class DosePrevistaTest {

    private DosePrevista criarDose(
            String id, int versao, int quantidade,
            Instant horario, String status) {

        return new DosePrevista(
                id,
                "user-001",
                "treatment-001",
                "med-001",
                versao,
                horario,
                "America/Sao_Paulo",
                quantidade,
                status
        );
    }

    @Test
    public void deveCriarDosePrevista() {
        Instant horario = Instant.parse("2026-11-13T11:00:00Z");

        DosePrevista dose = criarDose(
                "dose-001", 1, 2, horario, DosePrevista.SCHEDULED
        );

        assertEquals("dose-001", dose.getId());
        assertEquals("user-001", dose.getUserId());
        assertEquals(1, dose.getScheduleVersion());
        assertEquals(2, dose.getPlannedQuantity());
        assertEquals(horario, dose.getScheduledAt());
        assertEquals(DosePrevista.SCHEDULED, dose.getStatus());
    }

    @Test
    public void deveRejeitarIdInvalido() {
        Instant horario = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
                criarDose(null, 1, 1, horario, DosePrevista.SCHEDULED));

        assertThrows(IllegalArgumentException.class, () ->
                criarDose("", 1, 1, horario, DosePrevista.SCHEDULED));

        assertThrows(IllegalArgumentException.class, () ->
                criarDose("   ", 1, 1, horario, DosePrevista.SCHEDULED));
    }

    @Test
    public void deveRejeitarVersaoInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 0, 1, Instant.now(), DosePrevista.SCHEDULED));
    }

    @Test
    public void deveRejeitarQuantidadeInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 1, 0, Instant.now(), DosePrevista.SCHEDULED));

        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 1, -2, Instant.now(), DosePrevista.SCHEDULED));
    }

    @Test
    public void deveRejeitarHorarioNulo() {
        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 1, 1, null, DosePrevista.SCHEDULED));
    }

    @Test
    public void deveRejeitarStatusInvalido() {
        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 1, 1, Instant.now(), "INVALID"));
    }

    @Test
    public void deveCancelarDoseSemAlterarOriginal() {
        DosePrevista original = criarDose(
                "dose-001", 1, 1, Instant.now(), DosePrevista.SCHEDULED
        );

        DosePrevista cancelada = original.cancel();

        assertEquals(DosePrevista.SCHEDULED, original.getStatus());
        assertEquals(DosePrevista.CANCELLED, cancelada.getStatus());

        // A ocorrência continua sendo a mesma, só muda o status
        assertEquals(original.getId(), cancelada.getId());
        assertEquals(original.getScheduledAt(), cancelada.getScheduledAt());
        assertNotSame(original, cancelada);
    }

    @Test
    public void deveManterDoseJaCancelada() {
        DosePrevista dose = criarDose(
                "dose-001", 1, 1, Instant.now(), DosePrevista.CANCELLED
        );

        assertSame(dose, dose.cancel());
    }
}
