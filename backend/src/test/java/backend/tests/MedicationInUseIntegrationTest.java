package backend.tests;

import backend.config.MongoConnection;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.repositories.MongoMedicationInUseRepository;
import backend.repositories.MongoTreatmentRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

class MedicationInUseIntegrationTest {
    private MedicamentoEmUso medication(String id, int quantity) {
        return new MedicamentoEmUso(id, "Demo", "50 mg", quantity, "comprimido", null, true, List.of());
    }

    @Test
    void existingTreatmentMappingKeepsUnitAndInactiveState() {
        var repository = new MongoTreatmentRepository();
        var medication = medication("m", 1);
        medication.deactivate();
        var treatment = new Treatment("t", "a", "Demo", List.of(medication));
        repository.save(treatment);
        assertEquals(medication, repository.findById("t").orElseThrow().getMedications().get(0));
        repository.update(treatment);
        assertEquals(medication, repository.findById("t").orElseThrow().getMedications().get(0));
    }

    @Test
    @EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
    void persistsAndUpdatesOnlyTheOwnedMedicationInMongo() throws Exception {
        String database = "dosecerta_test_med_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
            try {
                var collection = connection.getCollection("treatments");
                collection.insertOne(new Document("_id", "t").append("userId", "a")
                    .append("name", "Demo").append("medications", List.of()));
                var repository = new MongoMedicationInUseRepository(collection);
                assertTrue(repository.add("t", "a", medication("m1", 1)));
                assertTrue(repository.add("t", "a", medication("m2", 1)));
                assertFalse(repository.add("t", "a", medication("m1", 2)));
                Document before = collection.find().first();
                assertFalse(repository.add("t", "b", medication("m3", 1)));
                assertTrue(repository.find("t", "b", "m1").isEmpty());
                assertFalse(repository.update("t", "b", medication("m1", 2)));
                assertFalse(repository.deactivate("t", "b", "m1"));
                assertFalse(repository.update("missing-treatment", "a", medication("m1", 2)));
                assertEquals(before, collection.find().first());
                collection.updateOne(new Document("medications.id", "m1"),
                    new Document("$set", new Document("medications.$.historicalMarker", "preserve")));
                CountDownLatch start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> { start.await(); return repository.update("t", "a", medication("m1", 2)); });
                    var second = executor.submit(() -> { start.await(); return repository.update("t", "a", medication("m2", 3)); });
                    start.countDown();
                    assertTrue(first.get(10, TimeUnit.SECONDS));
                    assertTrue(second.get(10, TimeUnit.SECONDS));
                }
                assertTrue(repository.deactivate("t", "a", "m1"));
                try (var second = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
                    var reopened = new MongoMedicationInUseRepository(second.getCollection("treatments"));
                    var restored = reopened.find("t", "a", "m1").orElseThrow();
                    assertEquals(2, restored.getQuantity());
                    assertEquals("comprimido", restored.getUnit());
                    assertFalse(restored.isActive());
                    assertEquals(3, reopened.find("t", "a", "m2").orElseThrow().getQuantity());
                }
                var stored = collection.find().first().getList("medications", Document.class);
                assertEquals(2, stored.size());
                assertEquals("preserve", stored.get(0).getString("historicalMarker"));
            } finally { connection.getDatabase().drop(); }
        }
    }
}
