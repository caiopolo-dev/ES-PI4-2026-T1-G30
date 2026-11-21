// Autor: Samuel Campovilla
package backend.tests;

import backend.models.PlannedDoseIdentity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class PlannedDoseIdentityTest {

    @Test
    void mesmaOcorrenciaDeveGerarMesmoId() {

        Instant horario = Instant.parse("2026-11-21T11:00:00Z");

        String id1 = PlannedDoseIdentity.generateId(
                "user-1", "treatment-1", "med-1", 1, horario);

        String id2 = PlannedDoseIdentity.generateId(
                "user-1", "treatment-1", "med-1", 1, horario);

        // Uma segunda geração deve produzir exatamente o mesmo ID.
        assertEquals(id1, id2);
    }

    @Test
    void versoesDiferentesDevemGerarIdsDiferentes() {

        Instant horario = Instant.parse("2026-11-21T11:00:00Z");

        String idV1 = PlannedDoseIdentity.generateId(
                "user-1", "treatment-1", "med-1", 1, horario);

        String idV2 = PlannedDoseIdentity.generateId(
                "user-1", "treatment-1", "med-1", 2, horario);

        // Versões distintas representam ocorrências distintas.
        assertNotEquals(idV1, idV2);
    }

    @Test
    void horariosDiferentesDevemGerarIdsDiferentes() {

        String idManha = PlannedDoseIdentity.generateId(
                "user-1", "treatment-1", "med-1", 1,
                Instant.parse("2026-11-21T11:00:00Z"));

        String idNoite = PlannedDoseIdentity.generateId(
                "user-1", "treatment-1", "med-1", 1,
                Instant.parse("2026-11-21T23:00:00Z"));

        assertNotEquals(idManha, idNoite);
    }

    @Test
    void separadoresNosIdsNaoDevemCausarColisoes() {

        Instant horario = Instant.parse("2026-11-21T11:00:00Z");

        String id1 = PlannedDoseIdentity.generateId(
                "a|b", "c", "med-1", 1, horario);

        String id2 = PlannedDoseIdentity.generateId(
                "a", "b|c", "med-1", 1, horario);

        // Mesmo contendo separadores, as identidades são distintas.
        assertNotEquals(id1, id2);
    }
}
