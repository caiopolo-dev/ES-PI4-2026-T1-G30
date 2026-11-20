package backend.tests;

import backend.models.DoseAction;
import backend.models.RegistroDeDose;
import backend.repositories.MongoDoseRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import org.junit.jupiter.api.AfterEach;

import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DoseRecordOwnershipTest {
    private MongoClient mongoClient;
    private MongoDatabase testDatabase;

    @BeforeEach
    void preparar() {
        String uri = System.getenv("MONGODB_URI");

        assumeTrue(uri != null && !uri.isBlank(),
                "MONGODB_URI não configurada");

        mongoClient = MongoClients.create(uri);

        String nomeBanco = "dosecerta_test_"
                + UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 16);

        testDatabase = mongoClient.getDatabase(nomeBanco);
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

    @Test
    void batchQueryReturnsOnlyRecordsOfRequestedOwner() {
        var repository = new MongoDoseRecordRepository(testDatabase);
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
        var repository = new MongoDoseRecordRepository(testDatabase);
        assertTrue(repository.findByPlannedDoseIdsAndUserId(List.of("dose-a"), null).isEmpty());
        assertTrue(repository.findByPlannedDoseIdsAndUserId(List.of("dose-a"), " ").isEmpty());
        assertTrue(repository.findByPlannedDoseIdsAndUserId(null, "user-a").isEmpty());
    }
}
