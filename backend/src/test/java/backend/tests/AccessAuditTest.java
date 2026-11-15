package backend.tests;

import backend.auth.AccessAudit;
import backend.auth.AccessDeniedException;
import backend.auth.ResourceNotFoundException;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.router.Router;
import java.util.*;
import java.util.logging.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessAuditTest {
    @Test
    void logsOnlyFixedEventsWithoutSensitiveRequestData() {
        Logger logger = Logger.getLogger(AccessAudit.class.getName());
        List<LogRecord> entries = new ArrayList<>();
        Handler capture = new Handler() {
            public void publish(LogRecord entry) { entries.add(entry); }
            public void flush() { }
            public void close() { }
        };
        Level previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(capture);
        try {
            Router missingSession = new Router();
            missingSession.register("GET", "/private", request -> { throw new AssertionError(); });
            HttpRequest request = new HttpRequest("GET", "/private?userId=private-user",
                "HTTP/1.1", Map.of("Authorization", "Bearer secret-token"),
                "{\"password\":\"secret-password\",\"treatment\":\"private-treatment\"}");
            assertEquals(401, missingSession.route(request).getStatusCode());
            Router authenticated = new Router(input -> Optional.of("private-user"));
            authenticated.register("GET", "/private", input -> { throw new AccessDeniedException(); });
            assertEquals(403, authenticated.route(request).getStatusCode());
            authenticated.register("GET", "/private", input -> { throw new ResourceNotFoundException(); });
            assertEquals(404, authenticated.route(request).getStatusCode());
            authenticated.register("GET", "/private", input -> HttpResponse.ok("{}"));
            assertEquals(200, authenticated.route(request).getStatusCode());
            assertEquals(List.of("AUTHENTICATION_REQUIRED", "PERMISSION_DENIED", "RESOURCE_UNAVAILABLE"),
                entries.stream().map(LogRecord::getMessage).toList());
            for (LogRecord entry : entries) {
                assertNull(entry.getThrown());
                assertNull(entry.getParameters());
            }
        } finally {
            logger.removeHandler(capture);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
    }
}
