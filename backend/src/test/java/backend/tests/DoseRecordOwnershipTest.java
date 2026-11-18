package backend.tests;

import backend.models.DoseAction;
import backend.models.RegistroDeDose;
import backend.repositories.MongoDoseRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoseRecordOwnershipTest {
    @Test
    void batchQueryReturnsOnlyRecordsOfRequestedOwner() {
        var repository = new MongoDoseRecordRepository();
        Instant now = Instant.parse("2026-11-15T12:00:00Z");
        repository.save(new RegistroDeDose("record-a", "dose-a", "user-a", DoseAction.TAKEN, now, now));
        repository.save(new RegistroDeDose("record-b", "dose-b", "user-b", DoseAction.SKIPPED, null, now));
        var ids = List.of("dose-a", "dose-b", "missing");
        assertEquals(Set.of("dose-a"), repository.findByPlannedDoseIdsAndUserId(ids, "user-a").keySet());
        assertEquals(Set.of("dose-b"), repository.findByPlannedDoseIdsAndUserId(ids, "user-b").keySet());
        assertTrue(repository.findByPlannedDoseIdsAndUserId(List.of("dose-b"), "user-a").isEmpty());
        assertEquals(2, repository.countAll());
    }

    @Test
    void missingIdentityOrDoseSelectionDoesNotExposeRecords() {
        var repository = new MongoDoseRecordRepository();
        assertTrue(repository.findByPlannedDoseIdsAndUserId(List.of("dose-a"), null).isEmpty());
        assertTrue(repository.findByPlannedDoseIdsAndUserId(List.of("dose-a"), " ").isEmpty());
        assertTrue(repository.findByPlannedDoseIdsAndUserId(null, "user-a").isEmpty());
    }
}
