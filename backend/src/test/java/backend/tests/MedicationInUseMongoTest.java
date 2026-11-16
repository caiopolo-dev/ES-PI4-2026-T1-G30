package backend.tests;

import backend.config.MongoConnection;
import backend.models.MedicamentoEmUso;
import backend.repositories.MongoMedicationInUseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
class MedicationInUseMongoTest {
    private MedicamentoEmUso medication(String id, String quantity) {
        return new MedicamentoEmUso(id, "Demo", "50 mg", new BigDecimal(quantity),
            "comprimido", null, true, List.of());
    }

    @Test
    void persistsDecimalAndUpdatesDifferentMedicationsWithoutLostWrites() throws Exception {
        String database = "dosecerta_test_medications_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
            try {
                var collection = connection.getCollection("treatments");
                collection.insertOne(new Document("_id", "t").append("userId", "a")
                    .append("name", "Demo").append("medications", List.of()));
                var repository = new MongoMedicationInUseRepository(collection);
                assertTrue(repository.add("t", "a", medication("m1", "0.5")));
                assertTrue(repository.add("t", "a", medication("m2", "1")));
                assertFalse(repository.add("t", "a", medication("m1", "9")));
                assertFalse(repository.add("t", "b", medication("foreign", "1")));
                assertFalse(repository.update("t", "b", medication("m1", "9")));
                assertTrue(repository.find("t", "b", "m1").isEmpty());
                var stored = collection.find().first().getList("medications", Document.class).get(0);
                assertInstanceOf(Decimal128.class, stored.get("quantity"));
                assertEquals(new BigDecimal("0.5"), stored.get("quantity", Decimal128.class).bigDecimalValue());
                // Um campo de cronograma desconhecido deve sobreviver sem ser reescrito.
                collection.updateOne(new Document("medications.id", "m1"),
                    new Document("$set", new Document("medications.$.scheduleMarker", "preserve")));
                CountDownLatch start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> { start.await(); return repository.update("t", "a", medication("m1", "0.25")); });
                    var second = executor.submit(() -> { start.await(); return repository.update("t", "a", medication("m2", "2")); });
                    start.countDown();
                    assertTrue(first.get(10, TimeUnit.SECONDS));
                    assertTrue(second.get(10, TimeUnit.SECONDS));
                }
                try (var reopened = new MongoConnection("mongodb://127.0.0.1:27017", database)) {
                    var fresh = new MongoMedicationInUseRepository(reopened.getCollection("treatments"));
                    assertEquals(new BigDecimal("0.25"), fresh.find("t", "a", "m1").orElseThrow().getQuantity());
                    assertEquals(new BigDecimal("2"), fresh.find("t", "a", "m2").orElseThrow().getQuantity());
                    assertEquals("comprimido", fresh.find("t", "a", "m1").orElseThrow().getUnit());
                }
                stored = collection.find().first().getList("medications", Document.class).get(0);
                assertEquals("preserve", stored.getString("scheduleMarker"));
                assertEquals(2, collection.find().first().getList("medications", Document.class).size());
                Document before = collection.find().first();
                assertThrows(NumberFormatException.class,
                    () -> repository.update("t", "a", medication("m1", "1E+7000")));
                assertEquals(before, collection.find().first());
                collection.updateOne(new Document("_id", "t"), new Document("$set", new Document("status", "ACTIVE")));
                var service = new backend.services.MedicationInUseService(repository);
                var context = new backend.auth.AuthContext("a");
                var created = service.add(context, "t", "Service demo", "20 mg", BigDecimal.ONE, "mL", null);
                var edited = service.update(context, "t", created.getId(), "Edited", "20 mg", new BigDecimal("0.5"), "mL", null);
                assertEquals("Edited", edited.getName());
                service.deactivate(context, "t", created.getId());
                service.deactivate(context, "t", created.getId());
                assertFalse(service.find(context, "t", created.getId()).isActive());
                CountDownLatch deactivateStart = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> { deactivateStart.await(); try { service.deactivate(context, "t", "m1"); return 1; } catch (IllegalStateException denied) { return 0; } });
                    var second = executor.submit(() -> { deactivateStart.await(); try { service.deactivate(context, "t", "m2"); return 1; } catch (IllegalStateException denied) { return 0; } });
                    deactivateStart.countDown();
                    assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
                }
                assertEquals(1, collection.find().first().getList("medications", Document.class).stream()
                    .filter(med -> !Boolean.FALSE.equals(med.get("active"))).count());
                collection.updateOne(new Document("_id", "t"), new Document("$set", new Document("status", "CLOSED")));
                assertThrows(IllegalStateException.class, () -> service.add(context, "t", "Demo", "50 mg", BigDecimal.ONE, "mL", null));

            } finally { connection.getDatabase().drop(); }
        }
    }
}
