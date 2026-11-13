package backend.handlers;

import backend.auth.SessionAuthenticator;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.DailyAgendaItem;
import backend.models.DailyAgendaResponse;
import backend.models.RegistroDeDose;
import backend.router.HttpHandler;
import backend.services.AgendaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Handler HTTP responsável pelo endpoint GET /doses?date=AAAA-MM-DD.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validar a sessão autenticada (retornando 401 Unauthorized se ausente, inválida ou expirada);</li>
 *   <li>Validar o parâmetro de query obrigatório date=AAAA-MM-DD e rejeitar parâmetros não suportados (400 Bad Request);</li>
 *   <li>Delegar a consulta ao AgendaService utilizando exclusivamente o userId da sessão autenticada;</li>
 *   <li>Serializar o resultado para o contrato JSON documentado (200 OK);</li>
 *   <li>Capturar falhas inesperadas retornando 500 Internal Server Error sem expor detalhes internos.</li>
 * </ul>
 */
public class AgendaHandler implements HttpHandler {

    private final AgendaService agendaService;
    private final SessionAuthenticator sessionAuthenticator;

    public AgendaHandler(AgendaService agendaService, SessionAuthenticator sessionAuthenticator) {
        this.agendaService = Objects.requireNonNull(agendaService, "agendaService não pode ser null");
        this.sessionAuthenticator = Objects.requireNonNull(
                sessionAuthenticator, "sessionAuthenticator não pode ser null");
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            // 1. Verificar autenticação da sessão
            Optional<String> authenticatedUserIdOpt = sessionAuthenticator.resolveAuthenticatedUserId(request);
            if (authenticatedUserIdOpt.isEmpty()) {
                return HttpResponse.unauthorized("Invalid, expired, or missing session");
            }
            String authenticatedUserId = authenticatedUserIdOpt.get();

            // 2. Extrair e validar estritamente o query parameter ?date=AAAA-MM-DD
            LocalDate requestedDate = parseAndValidateDateQuery(request.getRawQuery());
            if (requestedDate == null) {
                return HttpResponse.badRequest("Invalid or missing 'date' query parameter (expected format: YYYY-MM-DD)");
            }

            // 3. Consultar o serviço de domínio usando exclusivamente o titular da sessão
            DailyAgendaResponse agenda = agendaService.getDailyAgenda(authenticatedUserId, requestedDate);

            // 4. Serializar para JSON conforme o contrato
            return HttpResponse.ok(toJson(agenda));
        } catch (Exception unexpectedError) {
            // 500 Internal Server Error sem vazar stacktrace ou dados internos
            return HttpResponse.internalServerError("Internal server error");
        }
    }

    private LocalDate parseAndValidateDateQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return null;
        }

        String[] pairs = rawQuery.split("&", -1);
        String dateValue = null;

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                return null;
            }
            String[] kv = pair.split("=", -1);
            if (kv.length != 2) {
                return null;
            }
            String key = kv[0];
            String value = kv[1];

            // Rejeita qualquer parâmetro não suportado (como userId arbitrário enviado pelo cliente)
            // ou repetição do parâmetro date
            if (!"date".equals(key) || dateValue != null) {
                return null;
            }
            dateValue = value;
        }

        if (dateValue == null || !dateValue.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }

        try {
            return LocalDate.parse(dateValue);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String toJson(DailyAgendaResponse agenda) {
        StringJoiner dosesJoiner = new StringJoiner(",", "[", "]");
        for (DailyAgendaItem item : agenda.getDoses()) {
            dosesJoiner.add(toDoseItemJson(item));
        }

        return "{"
                + "\"date\":" + toJsonString(agenda.getDate().toString()) + ","
                + "\"timezone\":" + toJsonString(agenda.getTimezone()) + ","
                + "\"doses\":" + dosesJoiner.toString()
                + "}";
    }

    private String toDoseItemJson(DailyAgendaItem item) {
        return "{"
                + "\"id\":" + toJsonString(item.getId()) + ","
                + "\"treatmentId\":" + toJsonString(item.getTreatmentId()) + ","
                + "\"medicationInUseId\":" + toJsonString(item.getMedicationInUseId()) + ","
                + "\"medicationName\":" + toJsonString(item.getMedicationName()) + ","
                + "\"plannedQuantity\":" + formatQuantity(item.getPlannedQuantity()) + ","
                + "\"scheduledAt\":" + toJsonString(item.getScheduledAt().toString()) + ","
                + "\"scheduledLocalTime\":" + toJsonString(item.getScheduledLocalTime()) + ","
                + "\"scheduleStatus\":" + toJsonString(item.getScheduleStatus().name()) + ","
                + "\"record\":" + toRecordJson(item.getRecord()) + ","
                + "\"displayStatus\":" + toJsonString(item.getDisplayStatus().name())
                + "}";
    }

    private String toRecordJson(RegistroDeDose record) {
        if (record == null) {
            return "null";
        }
        return "{"
                + "\"id\":" + toJsonString(record.getId()) + ","
                + "\"action\":" + toJsonString(record.getAction().name()) + ","
                + "\"actualTime\":" + (record.getActualTime() != null ? toJsonString(record.getActualTime().toString()) : "null") + ","
                + "\"recordedAt\":" + toJsonString(record.getRecordedAt().toString())
                + "}";
    }

    private String formatQuantity(BigDecimal quantity) {
        BigDecimal normalized = quantity.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return normalized.toBigInteger().toString();
        }
        return normalized.toPlainString();
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
}
