package backend.tests;

import backend.models.DoseHistoryPeriod;
import backend.auth.AuthContext;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.repositories.PlannedDoseRepository;
import backend.repositories.DoseRecordRepository;
import backend.services.DoseHistoryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DoseHistoryServiceTest {
    private final AuthContext owner = new AuthContext("owner");
    private final DoseHistoryPeriod day = DoseHistoryPeriod.parse("2026-11-03", "2026-11-03");
    private final Instant reference = Instant.parse("2026-11-03T15:00:00Z");

    private final PlannedDoseRepository doses = mock(PlannedDoseRepository.class);
    private final DoseRecordRepository records = mock(DoseRecordRepository.class);
    private final DoseHistoryService history = new DoseHistoryService(doses, records, Clock.fixed(reference, ZoneOffset.UTC));

    @Test
    void keepsExplicitActionsAndDistinguishesUnrecordedPastFutureAndCancellation() {
        // Usei um horario fixo para comparar doses futuras e vencidas sem depender do relogio real.
        var taken = dose("taken", "t", "m", reference.minusSeconds(7200));
        var skipped = dose("skipped", "t", "m", reference.minusSeconds(3600));
        var overdue = dose("overdue", "t", "m", reference.minusSeconds(60));
        var future = dose("future", "t", "m", reference.plusSeconds(60));
        var cancelled = dose("cancelled", "t", "m", reference.plusSeconds(3600)).cancel();

        when(doses.findByUserAndPeriod("owner", day.startInclusive(), day.endExclusive()))
            .thenReturn(List.of(taken, skipped, overdue, future, cancelled));
        var takenRecord = new RegistroDeDose("r1", "taken", "owner", DoseAction.TAKEN,
            taken.getScheduledAt().plusSeconds(60), reference.plusSeconds(86400));
        var skippedRecord = new RegistroDeDose("r2", "skipped", "owner", DoseAction.SKIPPED, null, reference);
        var cancelledRecord = new RegistroDeDose("r3", "cancelled", "owner", DoseAction.TAKEN, reference, reference);

        when(records.findByPlannedDoseIdsAndUserId(anyCollection(), eq("owner")))
            .thenReturn(Map.of("taken", takenRecord, "skipped", skippedRecord, "cancelled", cancelledRecord));

        var result = history.getHistory(owner, day, null, null).items();
        assertEquals(List.of(DoseAction.TAKEN, DoseAction.SKIPPED), List.of(result.get(0).action(), result.get(1).action()));
        assertEquals(takenRecord.getRecordedAt(), result.get(0).recordedAt());
        assertNull(result.get(1).actualTime());
        for (int index : List.of(2, 3)) {
            assertNull(result.get(index).action());
            assertNull(result.get(index).actualTime());
            assertNull(result.get(index).recordedAt());
        }
        assertTrue(result.get(2).scheduledAt().isBefore(reference));
        assertTrue(result.get(3).scheduledAt().isAfter(reference));
        assertTrue(result.get(4).isCancelled());
        assertEquals(DoseAction.TAKEN, result.get(4).action());

        verify(doses).findByUserAndPeriod("owner", day.startInclusive(), day.endExclusive());
        verify(records).findByPlannedDoseIdsAndUserId(List.of("taken", "skipped", "overdue", "future", "cancelled"), "owner");
        verifyNoMoreInteractions(doses, records);
    }

    @Test
    void appliesBothFiltersAndExactPeriodBoundariesWithoutLeakingRecords() {
        var first = dose("first", "t1", "m1", day.startInclusive());
        var last = dose("last", "t1", "m1", day.endExclusive().minusNanos(1));
        var anotherTreatment = dose("t2-dose", "t2", "m1", reference);
        var anotherMedication = dose("m2-dose", "t1", "m2", reference);

        when(doses.findByUserAndPeriod("owner", day.startInclusive(), day.endExclusive())).thenReturn(List.of(
            first, last, anotherTreatment, anotherMedication,
            dose("before", "t1", "m1", day.startInclusive().minusNanos(1)),
            dose("after", "t1", "m1", day.endExclusive())));

        // Coloquei registros inconsistentes para verificar se o servico impede associacoes indevidas.
        when(records.findByPlannedDoseIdsAndUserId(anyCollection(), eq("owner"))).thenReturn(Map.of(
            "first", new RegistroDeDose("foreign", "first", "other", DoseAction.SKIPPED, null, reference),
            "last", new RegistroDeDose("wrong-dose", "different-id", "owner", DoseAction.SKIPPED, null, reference)));

        var both = history.getHistory(owner, day, "t1", "m1").items();
        assertEquals(List.of("first", "last"), both.stream().map(backend.models.DoseHistoryItem::plannedDoseId).toList());
        assertTrue(both.stream().allMatch(item -> item.action() == null));
        assertEquals(3, history.getHistory(owner, day, "t1", null).items().size());
        assertEquals(3, history.getHistory(owner, day, null, "m1").items().size());
        assertTrue(history.getHistory(owner, day, "foreign-treatment", null).items().isEmpty());
        verify(records).findByPlannedDoseIdsAndUserId(List.of("first", "last"), "owner");
    }

    @Test
    void returnsEmptyWithoutRecordQueriesAndRejectsInvalidInputsBeforeReading() {

        when(doses.findByUserAndPeriod("owner", day.startInclusive(), day.endExclusive())).thenReturn(List.of());

        var empty = history.getHistory(owner, day, null, null);
        assertTrue(empty.items().isEmpty());
        assertEquals(reference, empty.referenceTime());
        assertThrows(NullPointerException.class, () -> history.getHistory(null, day, null, null));
        assertThrows(NullPointerException.class, () -> history.getHistory(owner, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> history.getHistory(owner, day, " ", null));
        assertThrows(IllegalArgumentException.class, () -> history.getHistory(owner, day, null, ""));

        verify(doses).findByUserAndPeriod("owner", day.startInclusive(), day.endExclusive());
        verifyNoMoreInteractions(doses);
        // Sem ocorrencias, consultar registros seria uma ida desnecessaria ao repository.
        verifyNoInteractions(records);
    }

    @Test
    void preservesOccurrenceTimeQuantityAndVersionIncludingCancelledFutureOccurrences() {
        // 20h e 21h locais pertencem ao mesmo dia civil da consulta.
        var old = new DosePrevista("old", "owner", "t", "m", "Nome antigo", 1,
            Instant.parse("2026-11-03T23:00:00Z"), "America/Sao_Paulo", new BigDecimal("0.5"), ScheduleStatus.SCHEDULED);
        var newer = new DosePrevista("new", "owner", "t", "m", "Nome novo", 2,
            Instant.parse("2026-11-04T00:00:00Z"), "America/Sao_Paulo", new BigDecimal("2"), ScheduleStatus.CANCELLED);

        when(doses.findByUserAndPeriod("owner", day.startInclusive(), day.endExclusive())).thenReturn(List.of(newer, old));

        when(records.findByPlannedDoseIdsAndUserId(List.of("old", "new"), "owner")).thenReturn(Map.of());

        var result = history.getHistory(owner, day, null, null).items();
        assertEquals(old.getScheduledAt(), result.get(0).scheduledAt());
        assertEquals(1, result.get(0).scheduleVersion());
        assertEquals(new BigDecimal("0.5"), result.get(0).plannedQuantity());
        assertEquals("Nome antigo", result.get(0).medicationName());
        assertEquals(newer.getScheduledAt(), result.get(1).scheduledAt());
        assertEquals(2, result.get(1).scheduleVersion());
        assertTrue(result.get(1).isCancelled());

        verify(doses).findByUserAndPeriod("owner", day.startInclusive(), day.endExclusive());
        verify(records).findByPlannedDoseIdsAndUserId(List.of("old", "new"), "owner");
        verifyNoMoreInteractions(doses, records);
    }

    private DosePrevista dose(String id, String treatmentId, String medicationId, Instant scheduledAt) {
        return new DosePrevista(id, "owner", treatmentId, medicationId, "Demo", 1,
            scheduledAt, "America/Sao_Paulo", BigDecimal.ONE, ScheduleStatus.SCHEDULED);
    }

    @Test
    void composesOwnedOccurrencesAndRecordsInOneBatchWithoutWriting() {
        var doses = org.mockito.Mockito.mock(backend.repositories.PlannedDoseRepository.class);
        var records = org.mockito.Mockito.mock(backend.repositories.DoseRecordRepository.class);
        var period = DoseHistoryPeriod.parse("2026-11-03", "2026-11-03");
        var now = Instant.parse("2026-11-03T15:00:00Z");
        var service = new backend.services.DoseHistoryService(doses, records,
            java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        var taken = new backend.models.DosePrevista("a", "owner", "t", "m", "Nome historico", 1,
            Instant.parse("2026-11-03T11:00:00Z"), "America/Sao_Paulo", new java.math.BigDecimal("0.5"), backend.models.ScheduleStatus.SCHEDULED);
        var pending = new backend.models.DosePrevista("b", "owner", "t", "m", null, 2,
            taken.getScheduledAt(), "America/Sao_Paulo", java.math.BigDecimal.ONE, backend.models.ScheduleStatus.SCHEDULED);
        var cancelled = new backend.models.DosePrevista("c", "owner", "t", "m", "Demo", 1,
            now.plusSeconds(3600), "America/Sao_Paulo", java.math.BigDecimal.ONE, backend.models.ScheduleStatus.CANCELLED);
        var foreign = new backend.models.DosePrevista("foreign", "other", "t", "m", "Demo", 1,
            taken.getScheduledAt(), "UTC", java.math.BigDecimal.ONE, backend.models.ScheduleStatus.SCHEDULED);

        org.mockito.Mockito.when(doses.findByUserAndPeriod("owner", period.startInclusive(), period.endExclusive()))
            .thenReturn(java.util.List.of(cancelled, pending, foreign, taken));
        var record = new backend.models.RegistroDeDose("r", "a", "owner", backend.models.DoseAction.TAKEN,
            taken.getScheduledAt().plusSeconds(60), now.plusSeconds(86400));

        org.mockito.Mockito.when(records.findByPlannedDoseIdsAndUserId(java.util.List.of("a", "b", "c"), "owner"))
            .thenReturn(java.util.Map.of("a", record));

        var response = service.getHistory(new backend.auth.AuthContext("owner"), period, null, null);
        assertEquals(java.util.List.of("a", "b", "c"), response.items().stream().map(backend.models.DoseHistoryItem::plannedDoseId).toList());
        assertEquals(now, response.referenceTime());
        assertEquals(record.getRecordedAt(), response.items().get(0).recordedAt());
        assertEquals(new java.math.BigDecimal("0.5"), response.items().get(0).plannedQuantity());
        assertEquals("Nome historico", response.items().get(0).medicationName());
        assertNull(response.items().get(0).plannedUnit());
        assertNull(response.items().get(1).action());
        assertNull(response.items().get(1).medicationName());
        assertTrue(response.items().get(2).isCancelled());

        org.mockito.Mockito.verify(doses).findByUserAndPeriod("owner", period.startInclusive(), period.endExclusive());
        org.mockito.Mockito.verify(records).findByPlannedDoseIdsAndUserId(java.util.List.of("a", "b", "c"), "owner");
        org.mockito.Mockito.verifyNoMoreInteractions(doses, records);
    }

    @Test
    void requiresStrictExistingDates() {
        for (String invalid : new String[] {null, "", "2026-2-03", "2026/02/03", "2026-02-30", "2026-02-29", " 2026-02-03"}) {
            assertThrows(IllegalArgumentException.class, () -> DoseHistoryPeriod.parse(invalid, "2026-03-01"));
            assertThrows(IllegalArgumentException.class, () -> DoseHistoryPeriod.parse("2026-02-01", invalid));
        }
        assertEquals(LocalDate.of(2024, 2, 29), DoseHistoryPeriod.parse("2024-02-29", "2024-02-29").from());
    }

    @Test
    void countsBothDatesForTheNinetyDayLimit() {
        LocalDate first = LocalDate.of(2026, 1, 1);
        assertDoesNotThrow(() -> new DoseHistoryPeriod(first, first.plusDays(89)));
        assertThrows(IllegalArgumentException.class, () -> new DoseHistoryPeriod(first, first.plusDays(90)));
        assertThrows(IllegalArgumentException.class, () -> new DoseHistoryPeriod(first, first.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new DoseHistoryPeriod(null, first));
        assertThrows(IllegalArgumentException.class, () -> new DoseHistoryPeriod(first, null));
    }

    @Test
    void usesLocalDayBoundariesIncludingHistoricalDaylightSaving() {
        var normal = DoseHistoryPeriod.parse("2026-11-03", "2026-11-03");
        assertEquals(Instant.parse("2026-11-03T03:00:00Z"), normal.startInclusive());
        assertEquals(Instant.parse("2026-11-04T03:00:00Z"), normal.endExclusive());
        // Neste dia o inicio do horario de verao deixou o dia civil com 23 horas.
        var daylightSaving = DoseHistoryPeriod.parse("2018-11-04", "2018-11-04");
        assertEquals(Instant.parse("2018-11-04T03:00:00Z"), daylightSaving.startInclusive());
        assertEquals(Instant.parse("2018-11-05T02:00:00Z"), daylightSaving.endExclusive());
        assertEquals(Duration.ofHours(23), Duration.between(daylightSaving.startInclusive(), daylightSaving.endExclusive()));
    }
}
