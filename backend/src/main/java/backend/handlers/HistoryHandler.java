// Desenvolvido por Miguel Gallinucci.
package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.DoseHistoryItem;
import backend.models.DoseHistoryPeriod;
import backend.models.DoseHistoryResponse;
import backend.router.HttpHandler;
import backend.services.DoseHistoryService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/** Recebe a consulta HTTP e entrega os dados preparados pelo servico de historico. */
public final class HistoryHandler implements HttpHandler {

    // Aceitei apenas os filtros do contrato; userId nao entra aqui porque vem da sessao.
    private static final Set<String> PARAMETERS = Set.of(
        "from", "to", "treatmentId", "medicationInUseId"
    );

    private final DoseHistoryService service;

    public HistoryHandler(DoseHistoryService service) {

        this.service = Objects.requireNonNull(service);
    }

    @Override
    // Entrada HTTP do historico: valido a consulta, chamo DoseHistoryService e devolvo seus dados em JSON.
    public HttpResponse handle(HttpRequest request) {

        var context = request.getAuthContext().orElse(null);

        if (context == null) {
            return HttpResponse.unauthorized("Invalid, expired, or missing session");
        }

        if (!"/adherence/history".equals(request.getPath())) {
            return HttpResponse.notFound("Recurso nao encontrado.");
        }

        if (!"GET".equals(request.getMethod())) {
            return HttpResponse.methodNotAllowed("Metodo nao permitido.");
        }

        if (!request.getBody().isEmpty()) {
            return HttpResponse.badRequest("A consulta nao aceita corpo.");
        }

        Map<String, String> parameters;
        DoseHistoryPeriod period;

        try {
            parameters = parseQuery(request.getRawQuery());
            period = DoseHistoryPeriod.parse(parameters.get("from"), parameters.get("to"));
        } catch (IllegalArgumentException invalid) {
            // Erros ao ler os parametros sao dados invalidos do cliente e recebem 400.
            return HttpResponse.badRequest("Parametros do historico invalidos.");
        }

        // Usei o contexto autenticado porque a query nao pode escolher o titular.
        var result = service.getHistory(
            context,
            period,
            parameters.get("treatmentId"),
            parameters.get("medicationInUseId")
        );

        // Falhas tecnicas seguem para o Router, que responde sem expor detalhes internos.
        return HttpResponse.ok(toJson(result));
    }

    private Map<String, String> parseQuery(String query) {

        Map<String, String> parameters = new HashMap<>();

        for (String pair : query.split("&", -1)) {

            // Dividi so no primeiro sinal de igual para nao cortar um valor que tambem contenha esse caractere.
            String[] parts = pair.split("=", 2);

            if (parts.length != 2) {
                throw new IllegalArgumentException("Parametro sem valor.");
            }

            // Decodifiquei antes de conferir repeticoes para nomes equivalentes nao burlarem a validacao.
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);

            if (!PARAMETERS.contains(key) || parameters.containsKey(key) || value.isBlank()) {
                // Rejeitei repeticoes para nao escolher silenciosamente entre dois valores do mesmo filtro.
                throw new IllegalArgumentException("Parametro desconhecido, repetido ou vazio.");
            }

            parameters.put(key, value);
        }

        return parameters;
    }

    private String toJson(DoseHistoryResponse response) {
        // StringJoiner cuida das virgulas e devolve [] quando o periodo nao possui ocorrencias.

        StringJoiner items = new StringJoiner(",", "[", "]");

        for (var item : response.items()) {
            items.add(itemToJson(item));
        }

        return "{"
            + "\"from\":" + quote(response.from())
            + ",\"to\":" + quote(response.to())
            + ",\"timezone\":" + quote(response.timezone())
            + ",\"referenceTime\":" + quote(response.referenceTime())
            + ",\"items\":" + items
            + "}";
    }

    private String itemToJson(DoseHistoryItem item) {

        // Mantive BigDecimal como numero JSON, sem converter para double e perder precisao.
        return "{"
            + "\"plannedDoseId\":" + quote(item.plannedDoseId())
            + ",\"treatmentId\":" + quote(item.treatmentId())
            + ",\"medicationInUseId\":" + quote(item.medicationInUseId())
            + ",\"medicationName\":" + quote(item.medicationName())
            + ",\"scheduleVersion\":" + item.scheduleVersion()
            + ",\"scheduledAt\":" + quote(item.scheduledAt())
            + ",\"scheduleTimezone\":" + quote(item.scheduleTimezone())
            + ",\"plannedQuantity\":" + item.plannedQuantity().toPlainString()
            + ",\"plannedUnit\":" + quote(item.plannedUnit())
            + ",\"action\":" + quote(item.action())
            + ",\"actualTime\":" + quote(item.actualTime())
            + ",\"recordedAt\":" + quote(item.recordedAt())
            + ",\"isCancelled\":" + item.isCancelled()
            + "}";
    }

    private String quote(Object value) {

        if (value == null) {
            return "null";
        }

        StringBuilder quoted = new StringBuilder("\"");

        // Fiz o escape porque nomes podem conter aspas, barras e quebras de linha.
        for (char character : value.toString().toCharArray()) {

            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }

        return quoted.append('"').toString();
    }
}
