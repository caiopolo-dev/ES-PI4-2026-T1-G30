package backend.tests;

import backend.handlers.AgendaHandler;
import backend.handlers.AdherenceHandler;
import backend.http.HttpRequest;
import backend.services.AgendaService;
import backend.services.AdherenceService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticatedIdentityTest {
    @Test
    void clientFieldsDoNotAuthenticateAgendaOrIndicators() {
        var agenda = mock(AgendaService.class);
        var adherence = mock(AdherenceService.class);
        var headers = Map.of("userId", "user-a", "X-User-Id", "user-a",
            "Authorization", "Bearer unvalidated-token");
        var request = new HttpRequest("GET", "/doses?date=2026-11-15&userId=user-a",
            "HTTP/1.1", headers, "{\"userId\":\"user-a\"}");
        assertEquals(401, new AgendaHandler(agenda).handle(request).getStatusCode());
        assertEquals(401, new AdherenceHandler(adherence).handle(request).getStatusCode());
        verifyNoInteractions(agenda, adherence);
    }
}
