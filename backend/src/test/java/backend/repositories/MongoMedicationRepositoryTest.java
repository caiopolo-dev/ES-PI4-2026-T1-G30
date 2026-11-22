//Caio Ferreira Polo issue 40
package backend.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import backend.models.Medication;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.UpdateOptions;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MongoMedicationRepositoryTest {
    @Test
    void constructionDoesNotModifyThePersistentCatalog() {
        MongoCollection<Document> collection = mockCollection();

        new MongoMedicationRepository(collection);

        verifyNoInteractions(collection);
    }

    @Test
    void catalogPreservesTenPresentationsAndAddsFiftyCanonicalMedications() {
        List<Document> catalog = MongoMedicationRepository.defaultCatalogDocuments();

        assertEquals(60, catalog.size());
        for (int index = 0; index < 10; index++) {
            Document presentation = catalog.get(index);
            assertEquals(String.format("P%03d", index + 1), presentation.getString("_id"));
            assertEquals("PRESENTATION", presentation.getString("catalogEntryType"));
            assertNotNull(presentation.getString("activeIngredient"));
            assertNotNull(presentation.getString("manufacturer"));
        }
        for (int index = 10; index < catalog.size(); index++) {
            Document medication = catalog.get(index);
            assertEquals(String.format("P%03d", index + 1), medication.getString("_id"));
            assertEquals("CANONICAL", medication.getString("catalogEntryType"));
            assertEquals(MongoMedicationRepository.RENAME_SOURCE,
                    medication.getString("catalogSource"));
            assertNotNull(medication.getString("sourceReference"));
            assertFalse(medication.containsKey("manufacturer"));
            assertFalse(medication.containsKey("concentration"));
        }
    }

    @Test
    void catalogHasNoDuplicateIdsOrCanonicalNames() {
        List<Document> catalog = MongoMedicationRepository.defaultCatalogDocuments();
        Set<String> ids = new HashSet<>();
        Set<String> canonicalNames = new HashSet<>();

        for (Document document : catalog) {
            assertTrue(ids.add(document.getString("_id")));
            if ("CANONICAL".equals(document.getString("catalogEntryType"))) {
                assertTrue(canonicalNames.add(document.getString("name").toLowerCase()));
            }
        }
        assertEquals(60, ids.size());
        assertEquals(50, canonicalNames.size());
    }

    @Test
    void repeatedInitializationUsesTheSameIdempotentUpserts() {
        MongoCollection<Document> collection = mockCollection();
        MongoMedicationRepository repository = new MongoMedicationRepository(collection);

        repository.ensureDefaultCatalog();
        repository.ensureDefaultCatalog();

        ArgumentCaptor<Bson> filters = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> updates = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<UpdateOptions> options = ArgumentCaptor.forClass(UpdateOptions.class);
        verify(collection, times(120)).updateOne(
                filters.capture(), updates.capture(), options.capture());

        Set<String> firstPassIds = new HashSet<>();
        Set<String> secondPassIds = new HashSet<>();
        for (int index = 0; index < 120; index++) {
            BsonDocument filter = filters.getAllValues().get(index).toBsonDocument(
                    Document.class, MongoClientSettings.getDefaultCodecRegistry());
            String id = filter.getString("_id").getValue();
            if (index < 60) {
                firstPassIds.add(id);
            } else {
                secondPassIds.add(id);
            }
            assertTrue(options.getAllValues().get(index).isUpsert());
            BsonDocument update = updates.getAllValues().get(index).toBsonDocument(
                    Document.class, MongoClientSettings.getDefaultCodecRegistry());
            assertTrue(update.containsKey("$setOnInsert"));
        }
        assertEquals(firstPassIds, secondPassIds);
        assertEquals(60, firstPassIds.size());
    }

    @Test
    void findsMedicationByIdFromMongoDocument() {
        MongoCollection<Document> collection = mockCollection();
        FindIterable<Document> query = mockFindIterable();
        when(collection.find(any(Bson.class))).thenReturn(query);
        when(query.first()).thenReturn(new Document("_id", "P011")
                .append("name", "Aciclovir")
                .append("catalogEntryType", "CANONICAL"));
        MongoMedicationRepository repository = new MongoMedicationRepository(collection);

        Optional<Medication> found = repository.findById("P011");

        assertTrue(found.isPresent());
        assertEquals("P011", found.get().getId());
        assertEquals("Aciclovir", found.get().getName());
    }

    @Test
    void listsPersistedDocumentsIncludingLegacyPresentationShape() {
        MongoCollection<Document> collection = mockCollection();
        FindIterable<Document> query = mockFindIterable();
        MongoCursor<Document> cursor = mockCursor();
        when(collection.find()).thenReturn(query);
        when(query.sort(any(Bson.class))).thenReturn(query);
        when(query.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                new Document("_id", "P001")
                        .append("activeIngredient", "Losartana potássica")
                        .append("concentration", "50 mg")
                        .append("manufacturer", "EMS")
                        .append("unitCount", 30)
                        .append("dosageForm", "comprimido"),
                new Document("_id", "P011").append("name", "Aciclovir"));
        MongoMedicationRepository repository = new MongoMedicationRepository(collection);

        List<Medication> medications = repository.findAll();

        assertEquals(2, medications.size());
        assertEquals("Losartana potássica 50 mg EMS - 30 comprimido", medications.get(0).getName());
        assertEquals("Aciclovir", medications.get(1).getName());
    }

    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> mockCollection() {
        return (MongoCollection<Document>) mock(MongoCollection.class);
    }

    @SuppressWarnings("unchecked")
    private static FindIterable<Document> mockFindIterable() {
        return (FindIterable<Document>) mock(FindIterable.class);
    }

    @SuppressWarnings("unchecked")
    private static MongoCursor<Document> mockCursor() {
        return (MongoCursor<Document>) mock(MongoCursor.class);
    }
}
