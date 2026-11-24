package backend.tests;

import backend.models.DosePrevista;
import backend.models.ScheduleStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class DosePrevistaTest {

    private DosePrevista criarDose(
            String id, int versao, int quantidade,
            Instant horario, ScheduleStatus status) {

        return new DosePrevista(
                id,
                "user-001",
                "treatment-001",
                "med-001",
                "Medicamento A",
                versao,
                horario,
                "America/Sao_Paulo",
                BigDecimal.valueOf(quantidade),
                status
        );
    }

    @Test
    public void deveCriarDosePrevista() {
        Instant horario = Instant.parse("2026-11-13T11:00:00Z");

        DosePrevista dose = criarDose(
                "dose-001", 1, 2, horario, ScheduleStatus.SCHEDULED
        );

        assertEquals("dose-001", dose.getId());
        assertEquals("user-001", dose.getUserId());
        assertEquals(1, dose.getScheduleVersion());
        assertEquals(BigDecimal.valueOf(2), dose.getPlannedQuantity());
        assertEquals(horario, dose.getScheduledAt());
        assertEquals(ScheduleStatus.SCHEDULED, dose.getStatus());
    }

    @Test
    public void deveGerarIdAutomaticamente() {
        DosePrevista dose = criarDose(
                null,
                1,
                1,
                Instant.now(),
                ScheduleStatus.SCHEDULED
        );

        assertNotNull(dose.getId());
        assertFalse(dose.getId().isBlank());
    }

    @Test
    public void deveRejeitarVersaoInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 0, 1, Instant.now(), ScheduleStatus.SCHEDULED));
    }

    @Test
    public void deveRejeitarQuantidadeInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 1, 0, Instant.now(), ScheduleStatus.SCHEDULED));

        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 1, -2, Instant.now(), ScheduleStatus.SCHEDULED));
    }

    @Test
    public void deveRejeitarHorarioNulo() {
        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 1, 1, null, ScheduleStatus.SCHEDULED));
    }

    @Test
    public void deveRejeitarStatusInvalido() {
        assertThrows(IllegalArgumentException.class, () ->
                criarDose("dose-001", 1, 1, Instant.now(), null));
    }

    @Test
    public void deveCancelarDoseSemAlterarOriginal() {
        DosePrevista original = criarDose(
                "dose-001", 1, 1, Instant.now(), ScheduleStatus.SCHEDULED
        );

        DosePrevista cancelada = original.cancel();

        assertEquals(ScheduleStatus.SCHEDULED, original.getStatus());
        assertEquals(ScheduleStatus.CANCELLED, cancelada.getStatus());

        // A ocorrência continua sendo a mesma, só muda o status
        assertEquals(original.getId(), cancelada.getId());
        assertEquals(original.getScheduledAt(), cancelada.getScheduledAt());
        assertNotSame(original, cancelada);
    }

    @Test
    public void deveManterDoseJaCancelada() {
        DosePrevista dose = criarDose(
                "dose-001", 1, 1, Instant.now(), ScheduleStatus.CANCELLED
        );

        assertSame(dose, dose.cancel());
    }
}
