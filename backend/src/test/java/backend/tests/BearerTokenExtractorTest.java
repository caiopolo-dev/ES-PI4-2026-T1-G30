package backend.tests;

import backend.http.HttpRequest;
import backend.services.BearerTokenExtractor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearerTokenExtractorTest {
    private static final String TOKEN = "A".repeat(43);
    private final BearerTokenExtractor extractor = new BearerTokenExtractor();

    @Test
    void deveExtrairBearerComNomeECredencialCaseInsensitive() {
        HttpRequest request = requestWith(Map.of("aUtHoRiZaTiOn", "bEaReR " + TOKEN));

        assertEquals(TOKEN, extractor.extract(request).orElseThrow());
    }

    @Test
    void deveRejeitarCabecalhoAusenteOuMalformado() {
        assertTrue(extractor.extract(requestWith(Map.of())).isEmpty());
        assertTrue(extractor.extract(requestWith(
            Map.of("Authorization", TOKEN)
        )).isEmpty());
        assertTrue(extractor.extract(requestWith(
            Map.of("Authorization", "Basic " + TOKEN)
        )).isEmpty());
        assertTrue(extractor.extract(requestWith(
            Map.of("Authorization", "Bearer " + TOKEN + ", Bearer " + TOKEN)
        )).isEmpty());
    }

    @Test
    void deveRejeitarMaisDeUmCabecalhoAuthorization() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + TOKEN);
        headers.put("authorization", "Bearer " + TOKEN);

        assertTrue(extractor.extract(requestWith(headers)).isEmpty());
    }

    private HttpRequest requestWith(Map<String, String> headers) {
        return new HttpRequest("GET", "/auth/me", "HTTP/1.1", headers, "");
    }
}
