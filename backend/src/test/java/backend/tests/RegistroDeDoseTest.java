package backend.tests;

import backend.models.DoseAction;
import backend.models.RegistroDeDose;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class RegistroDeDoseTest {

    @Test
    public void deveCriarRegistroTaken() {
        Instant horarioReal = Instant.parse("2026-11-13T11:05:00Z");
        Instant horarioRegistro = Instant.parse("2026-11-13T11:06:00Z");

        RegistroDeDose registro = new RegistroDeDose(
                "registro-001",
                "dose-001",
                "user-001",
                DoseAction.TAKEN,
                horarioReal,
                horarioRegistro
        );

        assertEquals("registro-001", registro.getId());
        assertEquals("dose-001", registro.getPlannedDoseId());
        assertEquals("user-001", registro.getUserId());
        assertEquals(DoseAction.TAKEN, registro.getAction());
        assertEquals(horarioReal, registro.getActualTime());
        assertEquals(horarioRegistro, registro.getRecordedAt());
    }

    @Test
    public void deveCriarRegistroSkipped() {
        RegistroDeDose registro = new RegistroDeDose(
                "registro-002",
                "dose-001",
                "user-001",
                DoseAction.SKIPPED,
                null,
                Instant.now()
        );

        assertEquals(DoseAction.SKIPPED, registro.getAction());
        assertNull(registro.getActualTime());
    }

    @Test
    public void deveRejeitarTakenSemHorarioReal() {
        assertThrows(IllegalArgumentException.class, () ->
                new RegistroDeDose(
                        "registro-001",
                        "dose-001",
                        "user-001",
                        DoseAction.TAKEN,
                        null,
                        Instant.now()
                ));
    }

    @Test
    public void deveRejeitarSkippedComHorarioReal() {
        assertThrows(IllegalArgumentException.class, () ->
                new RegistroDeDose(
                        "registro-001",
                        "dose-001",
                        "user-001",
                        DoseAction.SKIPPED,
                        Instant.now(),
                        Instant.now()
                ));
    }

    @Test
    public void deveRejeitarAcaoInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
                new RegistroDeDose(
                        "registro-001",
                        "dose-001",
                        "user-001",
                        null,
                        null,
                        Instant.now()
                ));
    }

    @Test
    public void deveGerarIdAutomaticamente() {
        RegistroDeDose registro = new RegistroDeDose(
                "",
                "dose-001",
                "user-001",
                DoseAction.SKIPPED,
                null,
                Instant.now()
        );

        assertNotNull(registro.getId());
        assertFalse(registro.getId().isBlank());
    }

    @Test
    public void deveRejeitarDosePrevistaInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
                new RegistroDeDose(
                        "registro-001",
                        null,
                        "user-001",
                        DoseAction.SKIPPED,
                        null,
                        Instant.now()
                ));
    }

    @Test
    public void deveRejeitarHorarioRegistroNulo() {
        assertThrows(IllegalArgumentException.class, () ->
                new RegistroDeDose(
                        "registro-001",
                        "dose-001",
                        "user-001",
                        DoseAction.SKIPPED,
                        null,
                        null
                ));
    }
}
