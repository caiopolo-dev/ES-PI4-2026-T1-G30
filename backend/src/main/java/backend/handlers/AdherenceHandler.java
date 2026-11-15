package backend.handlers;

import backend.auth.SessionAuthenticator;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.AdherenceSummary;
import backend.router.HttpHandler;
import backend.services.AdherenceService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/**
 * Handler HTTP responsável pelo endpoint GET /adherence/summary.
 * Suporta os parâmetros obrigatórios from=AAAA-MM-DD e to=AAAA-MM-DD,
 * além dos filtros opcionais treatmentId e medicationInUseId.
 */
public class AdherenceHandler implements HttpHandler {

    private final AdherenceService adherenceService;
    private final SessionAuthenticator sessionAuthenticator;

    public AdherenceHandler(AdherenceService adherenceService, SessionAuthenticator sessionAuthenticator) {
        this.adherenceService = Objects.requireNonNull(adherenceService, "adherenceService não pode ser null");
        this.sessionAuthenticator = Objects.requireNonNull(
                sessionAuthenticator, "sessionAuthenticator não pode ser null");
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            Optional<String> authenticatedUserIdOpt = sessionAuthenticator.resolveAuthenticatedUserId(request);
            if (authenticatedUserIdOpt.isEmpty()) {
                return HttpResponse.unauthorized("Invalid, expired, or missing session");
            }
            String authenticatedUserId = authenticatedUserIdOpt.get();

            QueryParams params = parseAndValidateQuery(request.getRawQuery());
            if (params == null) {
                return HttpResponse.badRequest(
                        "Invalid or missing query parameters (expected 'from=YYYY-MM-DD&to=YYYY-MM-DD')");
            }

            AdherenceSummary summary = adherenceService.calculateSummary(
                    authenticatedUserId,
                    params.from,
                    params.to,
                    params.treatmentId,
                    params.medicationInUseId);

            return HttpResponse.ok(toJson(summary));
        } catch (Exception unexpectedError) {
            return HttpResponse.internalServerError("Internal server error");
        }
    }

    private QueryParams parseAndValidateQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return null;
        }

        String fromRaw = null;
        String toRaw = null;
        String treatmentId = null;
        String medicationInUseId = null;

        String[] pairs = rawQuery.split("&", -1);
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                return null;
            }
            String[] kv = pair.split("=", -1);
            if (kv.length != 2) {
                return null;
            }
            String key = kv[0];
            String value = decode(kv[1]);
            if (value == null || value.trim().isEmpty()) {
                return null;
            }

            switch (key) {
                case "from":
                    if (fromRaw != null) {
                        return null;
                    }
                    fromRaw = value;
                    break;
                case "to":
                    if (toRaw != null) {
                        return null;
                    }
                    toRaw = value;
                    break;
                case "treatmentId":
                    if (treatmentId != null) {
                        return null;
                    }
                    treatmentId = value.trim();
                    break;
                case "medicationInUseId":
                    if (medicationInUseId != null) {
                        return null;
                    }
                    medicationInUseId = value.trim();
                    break;
                default:
                    // Rejeita parâmetros desconhecidos (ex.: userId arbitrário enviado pelo cliente)
                    return null;
            }
        }

        LocalDate from = parseIsoDate(fromRaw);
        LocalDate to = parseIsoDate(toRaw);
        if (from == null || to == null || from.isAfter(to)) {
            return null;
        }

        return new QueryParams(from, to, treatmentId, medicationInUseId);
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private LocalDate parseIsoDate(String rawDate) {
        if (rawDate == null || !rawDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String toJson(AdherenceSummary summary) {
        return "{"
                + "\"period\":{\"from\":" + toJsonString(summary.getFrom().toString())
                + ",\"to\":" + toJsonString(summary.getTo().toString()) + "},"
                + "\"filters\":{\"treatmentId\":" + toJsonString(summary.getTreatmentId())
                + ",\"medicationInUseId\":" + toJsonString(summary.getMedicationInUseId()) + "},"
                + "\"eligiblePlannedDoses\":" + summary.getEligiblePlannedDoses() + ","
                + "\"takenDoses\":" + summary.getTakenDoses() + ","
                + "\"skippedDoses\":" + summary.getSkippedDoses() + ","
                + "\"unrecordedOverdueDoses\":" + summary.getUnrecordedOverdueDoses() + ","
                + "\"onTimeTakenDoses\":" + summary.getOnTimeTakenDoses() + ","
                + "\"lateTakenDoses\":" + summary.getLateTakenDoses() + ","
                + "\"executionPercent\":" + formatPercent(summary.getExecutionPercent()) + ","
                + "\"punctualityPercent\":" + formatPercent(summary.getPunctualityPercent()) + ","
                + "\"executionStatus\":" + toJsonString(summary.getExecutionStatus()) + ","
                + "\"punctualityStatus\":" + toJsonString(summary.getPunctualityStatus()) + ","
                + "\"punctualityToleranceMinutes\":" + summary.getPunctualityToleranceMinutes()
                + "}";
    }

    private String formatPercent(Double value) {
        return value != null ? String.valueOf(value) : "null";
    }

    private static String toJsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
            }
        }
        return json.append('"').toString();
    }

    private static final class QueryParams {
        private final LocalDate from;
        private final LocalDate to;
        private final String treatmentId;
        private final String medicationInUseId;

        private QueryParams(LocalDate from, LocalDate to, String treatmentId, String medicationInUseId) {
            this.from = from;
            this.to = to;
            this.treatmentId = treatmentId;
            this.medicationInUseId = medicationInUseId;
        }
    }
}
