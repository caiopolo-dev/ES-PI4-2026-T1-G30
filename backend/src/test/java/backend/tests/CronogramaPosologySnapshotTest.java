
package backend.tests;

import backend.models.CronogramaDose;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CronogramaPosologySnapshotTest {

    private CronogramaDose criarCronograma() {

        return new CronogramaDose(
                List.of("08:00", "20:00"),
                "2026-11-21",
                "America/Sao_Paulo"
        );
    }

    @Test
    void devePreservarCompatibilidadeComCronogramasLegados() {

        CronogramaDose cronograma = criarCronograma();

        assertNull(cronograma.getQuantitySnapshot());
        assertNull(cronograma.getUnitSnapshot());
        assertFalse(cronograma.hasPosologySnapshot());
    }

    @Test
    void deveCriarSnapshotSemAlterarCronogramaOriginal() {

        CronogramaDose original = criarCronograma();

        CronogramaDose copia = original.withPosologySnapshot(1, "comprimido");

        // O cronograma original deve permanecer inalterado.
        assertFalse(original.hasPosologySnapshot());

        assertTrue(copia.hasPosologySnapshot());
        assertEquals(Integer.valueOf(1), copia.getQuantitySnapshot());
        assertEquals("comprimido", copia.getUnitSnapshot());
    }

    @Test
    void devePreservarSnapshotAoCopiarCronograma() {

        CronogramaDose cronograma = criarCronograma()
                .withPosologySnapshot(2, "comprimido");

        CronogramaDose copia = cronograma.withVersion(2)
                .withValidity(Instant.parse("2026-11-25T11:00:00Z"), null)
                .deactivate();

        assertEquals(Integer.valueOf(2), copia.getQuantitySnapshot());
        assertEquals("comprimido", copia.getUnitSnapshot());

        assertEquals(2, copia.getVersion());
        assertFalse(copia.isActive());
    }

    @Test
    void novaVersaoNaoDeveAlterarSnapshotAnterior() {

        CronogramaDose versao1 = criarCronograma()
                .withPosologySnapshot(1, "comprimido");

        CronogramaDose versao2 = versao1.withVersion(2)
                .withPosologySnapshot(2, "comprimido");

        // Cada versão deve preservar sua própria quantidade.
        assertEquals(Integer.valueOf(1), versao1.getQuantitySnapshot());
        assertEquals(Integer.valueOf(2), versao2.getQuantitySnapshot());
    }

    @Test
    void naoDeveAceitarSnapshotInvalido() {

        CronogramaDose cronograma = criarCronograma();

        assertThrows(IllegalArgumentException.class, () ->
                cronograma.withPosologySnapshot(0, "comprimido"));

        assertThrows(IllegalArgumentException.class, () ->
                cronograma.withPosologySnapshot(-1, "comprimido"));

        assertThrows(IllegalArgumentException.class, () ->
                cronograma.withPosologySnapshot(1, null));

        assertThrows(IllegalArgumentException.class, () ->
                cronograma.withPosologySnapshot(1, ""));
    }

    @Test
    void snapshotsDiferentesDevemRepresentarEstadosDiferentes() {

        CronogramaDose primeiro = criarCronograma()
                .withPosologySnapshot(1, "comprimido");

        CronogramaDose segundo = criarCronograma()
                .withPosologySnapshot(2, "comprimido");

        assertNotEquals(primeiro, segundo);
    }
}
