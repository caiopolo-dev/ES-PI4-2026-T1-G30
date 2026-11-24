
package backend.tests;

import backend.models.CronogramaDose;
import backend.models.ScheduleFrequency;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CronogramaDoseValidationTest {

    private CronogramaDose criarDiario(
            List<String> horarios,
            String inicio,
            String fim,
            String fuso) {

        return new CronogramaDose(
                1,
                ScheduleFrequency.EVERY_DAY,
                null,
                horarios,
                inicio,
                fim,
                fuso,
                true
        );
    }

    private CronogramaDose criarSemanal(List<String> dias) {
        return new CronogramaDose(
                1,
                ScheduleFrequency.DAYS_OF_WEEK,
                dias,
                List.of("08:00"),
                "2026-11-01",
                null,
                "America/Sao_Paulo",
                true
        );
    }

    @Test
    void deveAceitarCronogramaDiario() {
        CronogramaDose cronograma = criarDiario(
                List.of("08:00", "20:00"),
                "2026-11-01",
                null,
                "America/Sao_Paulo"
        );

        assertEquals(
                ScheduleFrequency.EVERY_DAY,
                cronograma.getFrequency()
        );

        assertEquals(
                List.of("08:00", "20:00"),
                cronograma.getTimes()
        );

        assertTrue(cronograma.isActive());
    }

    @Test
    void deveAceitarCronogramaSemanal() {
        CronogramaDose cronograma = criarSemanal(
                List.of("MONDAY", "WEDNESDAY", "FRIDAY")
        );

        assertEquals(
                ScheduleFrequency.DAYS_OF_WEEK,
                cronograma.getFrequency()
        );

        assertEquals(
                3,
                cronograma.getDaysOfWeek().size()
        );
    }

    @Test
    void deveRejeitarDiasInvalidosOuDuplicados() {

        assertThrows(
                IllegalArgumentException.class,
                () -> criarSemanal(List.of("FUNDAY"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> criarSemanal(
                        List.of("MONDAY", "MONDAY")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> criarSemanal(List.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> criarSemanal(
                        Arrays.asList("MONDAY", null)
                )
        );
    }

    @Test
    void deveRejeitarHorariosInvalidos() {

        List<String> invalidos = List.of(
                "25:00",
                "12:70",
                "8:00",
                "08:00:00",
                ""
        );

        for (String horario : invalidos) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> criarDiario(
                            List.of(horario),
                            "2026-11-01",
                            null,
                            "America/Sao_Paulo"
                    )
            );
        }
    }

    @Test
    void deveRejeitarHorariosDuplicados() {
        assertThrows(
                IllegalArgumentException.class,
                () -> criarDiario(
                        List.of("08:00", "08:00"),
                        "2026-11-01",
                        null,
                        "America/Sao_Paulo"
                )
        );
    }

    @Test
    void deveRejeitarDatasInvalidas() {

        assertThrows(
                IllegalArgumentException.class,
                () -> criarDiario(
                        List.of("08:00"),
                        "2026-02-30",
                        null,
                        "America/Sao_Paulo"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> criarDiario(
                        List.of("08:00"),
                        "2026-11-10",
                        "2026-11-01",
                        "America/Sao_Paulo"
                )
        );
    }

    @Test
    void deveRejeitarFusoInvalido() {
        assertThrows(
                IllegalArgumentException.class,
                () -> criarDiario(
                        List.of("08:00"),
                        "2026-11-01",
                        null,
                        "Fuso/Inexistente"
                )
        );
    }

    @Test
    void devePreservarHistoricoAoCriarNovaVersao() {

        CronogramaDose versao1 = criarSemanal(
                List.of("MONDAY", "FRIDAY")
        );

        CronogramaDose versao2 = versao1.nextVersion(
                List.of("09:00"),
                "2026-11-10"
        );

        assertEquals(1, versao1.getVersion());
        assertEquals(2, versao2.getVersion());

        assertEquals(
                List.of("08:00"),
                versao1.getTimes()
        );

        assertEquals(
                List.of("09:00"),
                versao2.getTimes()
        );

        assertEquals(
                ScheduleFrequency.DAYS_OF_WEEK,
                versao2.getFrequency()
        );
    }

    @Test
    void devePreservarDadosAoDesativar() {

        CronogramaDose original = criarSemanal(
                List.of("MONDAY")
        );

        CronogramaDose desativado = original.deactivate();

        assertTrue(original.isActive());
        assertFalse(desativado.isActive());

        assertEquals(
                original.getFrequency(),
                desativado.getFrequency()
        );

        assertEquals(
                original.getTimes(),
                desativado.getTimes()
        );
    }

    @Test
    void naoDevePermitirModificarListaDeHorarios() {

        List<String> horarios = new ArrayList<>();
        horarios.add("08:00");

        CronogramaDose cronograma = criarDiario(
                horarios,
                "2026-11-01",
                null,
                "America/Sao_Paulo"
        );

        horarios.add("20:00");

        assertEquals(1, cronograma.getTimes().size());

        assertThrows(
                UnsupportedOperationException.class,
                () -> cronograma.getTimes().add("20:00")
        );
    }

    @Test
    void deveRejeitarVersaoInvalida() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new CronogramaDose(
                        0,
                        ScheduleFrequency.EVERY_DAY,
                        null,
                        List.of("08:00"),
                        "2026-11-01",
                        null,
                        "America/Sao_Paulo",
                        true
                )
        );
    }

    @Test
    void deveManterCompatibilidadeComConstrutorAntigo() {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00"),
                "2026-11-01",
                "America/Sao_Paulo"
        );

        assertEquals(1, cronograma.getVersion());

        assertEquals(
                ScheduleFrequency.EVERY_DAY,
                cronograma.getFrequency()
        );
    }

    @Test
    void deveReconhecerConfiguracoesIdenticas() {
        CronogramaDose original = criarDiario(
                List.of("08:00", "20:00"),
                "2026-11-01",
                null,
                "America/Sao_Paulo"
        );

        CronogramaDose repetido = new CronogramaDose(
                2,
                ScheduleFrequency.EVERY_DAY,
                null,
                List.of("20:00", "08:00"),
                "2026-11-01",
                null,
                "America/Sao_Paulo",
                false
        );

        assertTrue(original.hasSameConfiguration(repetido));
    }

    @Test
    void deveReconhecerConfiguracoesDiferentes() {
        CronogramaDose original = criarDiario(
                List.of("08:00", "20:00"),
                "2026-11-01",
                null,
                "America/Sao_Paulo"
        );

        CronogramaDose alterado = criarDiario(
                List.of("08:00", "21:00"),
                "2026-11-01",
                null,
                "America/Sao_Paulo"
        );

        assertFalse(original.hasSameConfiguration(alterado));
        assertFalse(original.hasSameConfiguration(null));
    }

    @Test
    void deveDefinirVigencia() {
        CronogramaDose original = criarDiario(
                List.of("08:00"),
                "2026-11-20",
                null,
                "America/Sao_Paulo"
        );

        Instant inicio = Instant.parse("2026-11-20T12:00:00Z");
        Instant fim = Instant.parse("2026-11-25T12:00:00Z");

        CronogramaDose atualizado = original.withValidity(inicio, fim);

        assertEquals(inicio, atualizado.getValidFrom());
        assertEquals(fim, atualizado.getValidTo());

        assertNull(original.getValidFrom());
        assertNull(original.getValidTo());
    }

    @Test
    void deveVerificarLimitesDaVigencia() {
        Instant inicio = Instant.parse("2026-11-20T12:00:00Z");
        Instant fim = Instant.parse("2026-11-25T12:00:00Z");

        CronogramaDose cronograma = criarDiario(
                List.of("08:00"),
                "2026-11-20",
                null,
                "America/Sao_Paulo"
        ).withValidity(inicio, fim);

        assertTrue(cronograma.coversInstant(inicio));
        assertTrue(cronograma.coversInstant(inicio.plusSeconds(60)));

        assertFalse(cronograma.coversInstant(fim));
        assertFalse(cronograma.coversInstant(inicio.minusSeconds(60)));
    }

    @Test
    void deveRejeitarVigenciaInvalida() {
        CronogramaDose cronograma = criarDiario(
                List.of("08:00"),
                "2026-11-20",
                null,
                "America/Sao_Paulo"
        );

        Instant inicio = Instant.parse("2026-11-20T12:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> cronograma.withValidity(inicio, inicio)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cronograma.withValidity(
                        inicio, inicio.minusSeconds(60))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cronograma.withValidity(null, inicio)
        );
    }
}
