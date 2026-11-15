package backend.tests;

import backend.auth.AuthContext;
import backend.models.*;
import backend.repositories.MongoDoseRecordRepository;
import backend.repositories.MongoPlannedDoseRepository;
import backend.services.DoseAccessService;
import java.math.BigDecimal;
import java.time.*;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoseAccessServiceTest {
    private MongoPlannedDoseRepository doses;
    private MongoDoseRecordRepository records;
    private DoseAccessService service;
    private final AuthContext a = new AuthContext("user-a");
    private final AuthContext b = new AuthContext("user-b");
    private final Instant now = Instant.parse("2026-11-15T12:00:00Z");

    @BeforeEach
    void setup() {
        doses = new MongoPlannedDoseRepository();
        records = new MongoDoseRecordRepository();
        service = new DoseAccessService(doses, records, Clock.fixed(now, ZoneOffset.UTC));
        doses.save(dose("dose-a", "user-a", ScheduleStatus.SCHEDULED));
        doses.save(dose("dose-b", "user-b", ScheduleStatus.SCHEDULED));
        doses.save(dose("cancelled", "user-a", ScheduleStatus.CANCELLED));
    }

    private DosePrevista dose(String id, String user, ScheduleStatus status) {
        return new DosePrevista(id, user, "treatment-" + user, "med-" + id, "Medication",
            1, now, "UTC", BigDecimal.ONE, status);
    }

    @Test
    void deniesForeignReadsAndWritesWithoutCreatingRecord() {
        assertThrows(NoSuchElementException.class, () -> service.find(b, "dose-a"));
        assertThrows(NoSuchElementException.class, () -> service.findRecord(b, "dose-a"));
        assertThrows(NoSuchElementException.class, () -> service.record(b, "dose-a", DoseAction.TAKEN, now));
        assertEquals(0, records.countAll());
    }

    @Test
    void recordsOwnDoseUsingSessionIdentityAndServerTime() {
        var record = service.record(a, "dose-a", DoseAction.TAKEN, now.minusSeconds(60));
        assertEquals("user-a", record.getUserId());
        assertEquals("dose-a", record.getPlannedDoseId());
        assertEquals(now, record.getRecordedAt());
        assertEquals(record.getId(), service.findRecord(a, "dose-a").orElseThrow().getId());
        assertEquals(1, records.countAll());
        assertTrue(service.findRecord(b, "dose-b").isEmpty());
    }

    @Test
    void doesNotOverwriteExistingRecordOrRecordCancelledDose() {
        var original = service.record(a, "dose-a", DoseAction.SKIPPED, null);
        assertThrows(IllegalStateException.class, () -> service.record(a, "dose-a", DoseAction.TAKEN, now));
        assertThrows(IllegalStateException.class, () -> service.record(a, "cancelled", DoseAction.TAKEN, now));
        assertEquals(original.getId(), service.findRecord(a, "dose-a").orElseThrow().getId());
        assertEquals(DoseAction.SKIPPED, service.findRecord(a, "dose-a").orElseThrow().getAction());
        assertEquals(1, records.countAll());
    }

    @Test
    void missingAndForeignDosesHaveSameErrorAndRequireContext() {
        var missing = assertThrows(NoSuchElementException.class, () -> service.find(a, "missing"));
        var foreign = assertThrows(NoSuchElementException.class, () -> service.find(a, "dose-b"));
        assertEquals(missing.getMessage(), foreign.getMessage());
        assertThrows(NullPointerException.class, () -> service.find(null, "dose-a"));
    }
}
