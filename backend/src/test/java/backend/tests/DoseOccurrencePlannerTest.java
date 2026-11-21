
package backend.tests;

import backend.models.*;
import backend.services.DoseOccurrencePlanner;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DoseOccurrencePlannerTest {


    private Treatment criarTratamento(
            CronogramaDose cronograma,
            boolean medicamentoAtivo,
            TreatmentStatus status) {

        MedicamentoEmUso medicamento = new MedicamentoEmUso(
                "med-1",
                "Medicamento de teste",
                "50 mg",
                1,
                "comprimido",
                null,
                medicamentoAtivo,
                List.of(cronograma)
        );

        // Tratamentos encerrados precisam possuir closedAt.
        Instant encerramento = status == TreatmentStatus.CLOSED
                ? Instant.parse("2026-11-20T12:00:00Z")
                : null;

        return new Treatment(
                "trat-1",
                "user-1",
                "Tratamento de teste",
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                status,
                encerramento,
                List.of(medicamento)
        );
    }


    @Test
    void cronogramaSemanalDeveRespeitarDiasSelecionados() {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("MONDAY", "WEDNESDAY"),
                List.of("08:00"),
                "2026-11-23",
                "2026-11-29",
                "America/Sao_Paulo"
        );

        Treatment tratamento = criarTratamento(
                cronograma, true, TreatmentStatus.ACTIVE
        );

        DoseOccurrencePlanner planner = new DoseOccurrencePlanner();

        Instant inicio = Instant.parse("2026-11-23T03:00:00Z");
        Instant fim = Instant.parse("2026-11-30T03:00:00Z");

        List<DosePrevista> doses = planner.planOccurrences(
                tratamento, inicio, fim
        );

        // O intervalo contém uma segunda e uma quarta-feira.
        assertEquals(2, doses.size());

        assertEquals(
                Instant.parse("2026-11-23T11:00:00Z"),
                doses.get(0).getScheduledAt()
        );

        assertEquals(
                Instant.parse("2026-11-25T11:00:00Z"),
                doses.get(1).getScheduledAt()
        );
    }

    @Test
    void intervaloDeveIncluirInicioEExcluirFim() {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00", "20:00"),
                "2026-11-21",
                "America/Sao_Paulo"
        );

        Treatment tratamento = criarTratamento(
                cronograma, true, TreatmentStatus.ACTIVE
        );

        DoseOccurrencePlanner planner = new DoseOccurrencePlanner();

        Instant inicio = Instant.parse("2026-11-21T11:00:00Z");
        Instant fim = Instant.parse("2026-11-21T23:00:00Z");

        List<DosePrevista> doses = planner.planOccurrences(
                tratamento, inicio, fim
        );

        // A dose das 08h está exatamente no início.
        // A dose das 20h está exatamente no fim e deve ser excluída.
        assertEquals(1, doses.size());
        assertEquals(inicio, doses.get(0).getScheduledAt());
    }


    @Test
    void cronogramaDiarioDeveGerarDuasDosesPorDia() {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00", "20:00"),
                "2026-11-21",
                "America/Sao_Paulo"
        );

        MedicamentoEmUso medicamento = new MedicamentoEmUso(
                "med-1",
                "Medicamento de teste",
                "50 mg",
                1,
                "comprimido",
                null,
                true,
                List.of(cronograma)
        );

        Treatment tratamento = new Treatment(
                "trat-1",
                "user-1",
                "Tratamento de teste",
                null,
                LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 11, 22),
                TreatmentStatus.ACTIVE,
                null,
                List.of(medicamento)
        );

        DoseOccurrencePlanner planner = new DoseOccurrencePlanner();

        // Intervalo que cobre dois dias completos em São Paulo.
        Instant inicio = Instant.parse("2026-11-21T03:00:00Z");
        Instant fim = Instant.parse("2026-11-23T03:00:00Z");

        List<DosePrevista> doses = planner.planOccurrences(
                tratamento, inicio, fim
        );

        // Dois horários por dia, durante dois dias.
        assertEquals(4, doses.size());

        // Verifica a conversão do horário local para UTC.
        assertEquals(
                Instant.parse("2026-11-21T11:00:00Z"),
                doses.get(0).getScheduledAt()
        );

        assertEquals(
                Instant.parse("2026-11-21T23:00:00Z"),
                doses.get(1).getScheduledAt()
        );

        // Todas as ocorrências devem possuir status SCHEDULED.
        for (DosePrevista dose : doses) {
            assertEquals(ScheduleStatus.SCHEDULED, dose.getStatus());
        }
    }

    @Test
    void executarDuasVezesDeveProduzirOsMesmosIds() {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00"),
                "2026-11-21",
                "America/Sao_Paulo"
        );

        MedicamentoEmUso medicamento = new MedicamentoEmUso(
                "med-1",
                "Medicamento de teste",
                "50 mg",
                1,
                "comprimido",
                null,
                true,
                List.of(cronograma)
        );

        Treatment tratamento = new Treatment(
                "trat-1",
                "user-1",
                "Tratamento de teste",
                null,
                LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 11, 22),
                TreatmentStatus.ACTIVE,
                null,
                List.of(medicamento)
        );

        DoseOccurrencePlanner planner = new DoseOccurrencePlanner();

        Instant inicio = Instant.parse("2026-11-21T03:00:00Z");
        Instant fim = Instant.parse("2026-11-23T03:00:00Z");

        List<DosePrevista> primeira = planner.planOccurrences(
                tratamento, inicio, fim
        );

        List<DosePrevista> segunda = planner.planOccurrences(
                tratamento, inicio, fim
        );

        assertEquals(2, primeira.size());
        assertEquals(primeira.size(), segunda.size());

        for (int i = 0; i < primeira.size(); i++) {

            // Mesma ocorrência deve receber sempre o mesmo ID.
            assertEquals(primeira.get(i).getId(), segunda.get(i).getId());

            assertEquals(
                    primeira.get(i).getScheduledAt(),
                    segunda.get(i).getScheduledAt()
            );
        }
    }
}
