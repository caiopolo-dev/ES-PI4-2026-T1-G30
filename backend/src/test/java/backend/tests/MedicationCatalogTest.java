//Caio Ferreira Polo issue 40
package backend.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.handlers.MedicationHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.Medication;
import backend.repositories.MedicationRepository;
import backend.router.Router;
import backend.services.MedicationService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MedicationCatalogTest {
    @Test
    void medicationsEndpointReturnsAllSixtyCatalogEntries() {
        MedicationRepository repository = new SixtyMedicationRepository();
        MedicationHandler handler = new MedicationHandler(new MedicationService(repository));

        HttpResponse response = handler.handle(new HttpRequest("GET", "/medications"));

        assertEquals(200, response.getStatusCode());
        assertEquals(60, occurrences(response.getBody(), "\"id\":"));
        assertTrue(response.getBody().contains("\"id\":\"P001\""));
        assertTrue(response.getBody().contains("\"id\":\"P060\""));
    }

    @Test
    void medicationsEndpointRemainsPrivateByDefault() {
        Router router = new Router();
        router.register("GET", "/medications", new MedicationHandler(
                new MedicationService(new SixtyMedicationRepository())));

        HttpResponse response = router.route(new HttpRequest("GET", "/medications"));

        assertEquals(401, response.getStatusCode());
    }

    @Test
    void repositoryDefaultLookupRemainsCompatibleWithExistingImplementations() {
        MedicationRepository repository = new SixtyMedicationRepository();

        assertTrue(repository.findById("P060").isPresent());
        assertFalse(repository.findById("missing").isPresent());
    }

    private static int occurrences(String text, String expected) {
        int count = 0;
        int offset = 0;
        while (true) {
            int found = text.indexOf(expected, offset);
            if (found < 0) {
                return count;
            }
            count++;
            offset = found + expected.length();
        }
    }

    private static final class SixtyMedicationRepository implements MedicationRepository {
        private final List<Medication> medications;

        private SixtyMedicationRepository() {
            medications = new ArrayList<>();
            for (int index = 1; index <= 60; index++) {
                medications.add(new Medication(
                        String.format("P%03d", index), "Medicamento " + index));
            }
        }

        @Override
        public List<Medication> findAll() {
            return medications;
        }
    }
}
