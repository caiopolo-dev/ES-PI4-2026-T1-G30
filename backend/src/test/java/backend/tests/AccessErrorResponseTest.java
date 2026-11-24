//Autores: Cauã Bianchi Ferroni, Miguel Batista Gallinucci
package backend.tests;

import backend.auth.AccessDeniedException;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.TreatmentRepository;
import backend.router.Router;
import backend.services.TreatmentAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessErrorResponseTest {
    @Test
    void foreignAndMissingResourceHaveIdentical404Response() {
        Map<String, Treatment> storage = new LinkedHashMap<>();
        TreatmentRepository repository = new TreatmentRepository() {
            @Override
            public Treatment save(Treatment treatment) {
                storage.put(treatment.getId(), treatment);
                return treatment;
            }

            @Override
            public Optional<Treatment> findById(String id) {
                return Optional.ofNullable(storage.get(id));
            }

            @Override
            public List<Treatment> findByUserId(String userId) {
                return storage.values().stream()
                    .filter(treatment -> userId.equals(treatment.getUserId()))
                    .toList();
            }

            @Override
            public Treatment update(Treatment treatment) {
                if (!storage.containsKey(treatment.getId())) {
                    throw new NoSuchElementException();
                }
                storage.put(treatment.getId(), treatment);
                return treatment;
            }
        };
        repository.save(new Treatment(
            "private-a", "user-a", "Private treatment", null,
            null, null, TreatmentStatus.DRAFT, null, List.of()));
        var service = new TreatmentAccessService(repository);
        var router = new Router(request -> Optional.of("user-b"));
        router.register("GET", "/foreign", request -> {
            service.find(request.getAuthContext().orElseThrow(), "private-a");
            return HttpResponse.ok("{}");
        });
        router.register("GET", "/missing", request -> {
            service.find(request.getAuthContext().orElseThrow(), "missing");
            return HttpResponse.ok("{}");
        });
        var foreign = router.route(new HttpRequest("GET", "/foreign"));
        var missing = router.route(new HttpRequest("GET", "/missing"));
        assertEquals(404, foreign.getStatusCode());
        assertEquals(foreign.getStatusCode(), missing.getStatusCode());
        assertEquals(foreign.getBody(), missing.getBody());
        assertFalse(foreign.getBody().contains("private-a"));
    }

    @Test
    void distinguishesMissingSessionPermissionAndUnexpectedFailure() {
        Router unauthenticated = new Router();
        unauthenticated.register("GET", "/private", request -> { throw new AssertionError(); });
        assertEquals(401, unauthenticated.route(new HttpRequest("GET", "/private")).getStatusCode());
        Router authenticated = new Router(request -> Optional.of("user-a"));
        authenticated.register("GET", "/denied", request -> { throw new AccessDeniedException(); });
        authenticated.register("GET", "/broken", request -> { throw new IllegalStateException("secret"); });
        var denied = authenticated.route(new HttpRequest("GET", "/denied"));
        assertEquals(403, denied.getStatusCode());
        assertEquals("Forbidden", denied.getReasonPhrase());
        var broken = authenticated.route(new HttpRequest("GET", "/broken"));
        assertEquals(500, broken.getStatusCode());
        assertFalse(broken.getBody().contains("secret"));
    }
}
