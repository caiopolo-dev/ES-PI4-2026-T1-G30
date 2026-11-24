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
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import org.junit.jupiter.api.AfterEach;

import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DoseAccessServiceTest {
    private MongoClient mongoClient;
    private MongoDatabase testDatabase;

    private MongoPlannedDoseRepository doses;
    private MongoDoseRecordRepository records;
    private DoseAccessService service;
    private final AuthContext a = new AuthContext("user-a");
    private final AuthContext b = new AuthContext("user-b");
    private final Instant now = Instant.parse("2026-11-15T12:00:00Z");

    @BeforeEach
    void setup() {
        String uri = System.getenv("MONGODB_URI");

        // Não executa os testes MongoDB sem uma conexão configurada.
        assumeTrue(uri != null && !uri.isBlank(),
                "MONGODB_URI não configurada");

        mongoClient = MongoClients.create(uri);

        // Banco exclusivo para cada teste, evitando misturar os dados.
        String nomeBanco = "dosecerta_test_"
                + UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 16);

        testDatabase = mongoClient.getDatabase(nomeBanco);

        // Instancia os repositories usando o banco de teste.
        doses = new MongoPlannedDoseRepository(testDatabase);
        records = new MongoDoseRecordRepository(testDatabase);

        service = new DoseAccessService(doses, records, Clock.fixed(now, ZoneOffset.UTC));
        doses.save(dose("dose-a", "user-a", ScheduleStatus.SCHEDULED));
        doses.save(dose("dose-b", "user-b", ScheduleStatus.SCHEDULED));
        var cancelled = doses.save(dose("cancelled", "user-a", ScheduleStatus.SCHEDULED));
        doses.save(cancelled.cancel());
    }

    @AfterEach
    void limpar() {
        try {
            // Remove exclusivamente o banco temporário de testes.
            if (testDatabase != null
                    && testDatabase.getName().startsWith("dosecerta_test_")) {
                testDatabase.drop();
            }
        } finally {
            if (mongoClient != null) {
                mongoClient.close();
            }
        }
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
