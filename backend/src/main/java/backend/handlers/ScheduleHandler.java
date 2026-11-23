// Autor: Caio Ferreira Polo
package backend.handlers;

import backend.auth.AuthContext;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.models.ScheduleFrequency;
import backend.router.HttpHandler;
import backend.router.Router;
import backend.services.ScheduleService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;

// issue 28 - Expõe somente a configuração e a consulta dos cronogramas.
public final class ScheduleHandler implements HttpHandler {
    private static final Set<String> FIELDS = Set.of(
            "frequency", "daysOfWeek", "times", "startDate", "endDate",
            "timezone", "validFrom", "revision");

    private final ScheduleService service;

    // issue 28 - Reutiliza o serviço de domínio sem acessar o MongoDB diretamente.
    public ScheduleHandler(ScheduleService service) {
        this.service = Objects.requireNonNull(service);
    }

    // issue 28 - Registra os dois contratos HTTP exigidos pela issue.
    public void registerRoutes(Router router) {
        router.register("PUT", "/treatments/{id}/medications/{medicationId}/schedule", this);
        router.register("GET", "/treatments/{id}/medications/{medicationId}/schedule", this);
    }

    @Override
    // issue 28 - Valida caminho, autenticação e converte erros de domínio em HTTP.
    public HttpResponse handle(HttpRequest request) {
        AuthContext context = request.getAuthContext().orElse(null);
        if (context == null) {
            return HttpResponse.unauthorized("Invalid, expired, or missing session");
        }

        String[] path = request.getPath().split("/", -1);
        if (path.length != 6
                || !"treatments".equals(path[1])
                || path[2].isEmpty()
                || !"medications".equals(path[3])
                || path[4].isEmpty()
                || !"schedule".equals(path[5])) {
            return HttpResponse.notFound("Recurso nao encontrado.");
        }
        if (!request.getRawQuery().isEmpty()) {
            return HttpResponse.badRequest("Query nao suportada.");
        }

        try {
            if ("GET".equals(request.getMethod())) {
                if (!request.getBody().isBlank()) {
                    return HttpResponse.badRequest("Corpo nao suportado.");
                }
                MedicamentoEmUso medication = service.consultarMedicamento(
                        context, path[2], path[4]);
                return HttpResponse.ok(toJson(context, path[2], path[4], medication));
            }

            if (!"PUT".equals(request.getMethod())) {
                return HttpResponse.methodNotAllowed("Metodo nao permitido.");
            }

            Document data = parseBody(request);
            CronogramaDose configuration = configuration(data);
            Instant validFrom = instant(data, "validFrom");
            long revision = revision(data);

            MedicamentoEmUso medication = service.salvar(
                    context, path[2], path[4], configuration, validFrom, revision);
            return HttpResponse.ok(toJson(context, path[2], path[4], medication));
        } catch (IllegalArgumentException invalid) {
            return HttpResponse.badRequest("Dados de cronograma invalidos.");
        } catch (IllegalStateException conflict) {
            return new HttpResponse(
                    409, "Conflict", "{\"error\":\"Operacao de cronograma em conflito.\"}");
        }
    }

    // issue 28 - Aceita apenas o payload documentado e limita o corpo a 16 KiB.
    private Document parseBody(HttpRequest request) {
        String contentType = request.getHeader("Content-Type");
        if (contentType == null
                || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw new IllegalArgumentException("Esperado JSON");
        }
        String body = request.getBody();
        if (body.isBlank() || body.getBytes(StandardCharsets.UTF_8).length > 16384) {
            throw new IllegalArgumentException("Corpo vazio ou acima de 16 KiB");
        }
        Document data;
        try {
            data = Document.parse(body);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("JSON invalido", error);
        }
        if (!FIELDS.containsAll(data.keySet())) {
            throw new IllegalArgumentException("Campo nao permitido");
        }
        return data;
    }

    // issue 28 - Constrói o modelo validado; versão, active e validTo são controlados pelo servidor.
    private CronogramaDose configuration(Document data) {
        String frequencyText = requiredText(data, "frequency");
        ScheduleFrequency frequency;
        try {
            frequency = ScheduleFrequency.valueOf(frequencyText);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Frequencia invalida", error);
        }

        List<String> days = stringList(data, "daysOfWeek", false);
        List<String> times = stringList(data, "times", true);
        return new CronogramaDose(
                1,
                frequency,
                days,
                times,
                requiredText(data, "startDate"),
                optionalText(data, "endDate"),
                requiredText(data, "timezone"),
                true
        );
    }

    // issue 28 - Serializa a revisão, a versão aplicável agora e todo o histórico.
    private String toJson(
            AuthContext context,
            String treatmentId,
            String medicationId,
            MedicamentoEmUso medication) {

        Optional<CronogramaDose> current = service.consultarAtual(
                context, treatmentId, medicationId);
        List<Document> versions = new ArrayList<>();
        for (CronogramaDose schedule : medication.getSchedules()) {
            versions.add(toDocument(schedule));
        }

        Document response = new Document("treatmentId", treatmentId)
                .append("medicationId", medicationId)
                .append("revision", medication.getRevision())
                .append("current", current.isPresent() ? toDocument(current.get()) : null)
                .append("versions", versions);
        return response.toJson();
    }

    // issue 28 - Mantém os nomes já usados pelo domínio e expõe a vigência em ISO-8601.
    private Document toDocument(CronogramaDose schedule) {
        return new Document("version", schedule.getVersion())
                .append("quantitySnapshot", schedule.getQuantitySnapshot())
                .append("unitSnapshot", schedule.getUnitSnapshot())
                .append("frequency", schedule.getFrequency().name())
                .append("daysOfWeek", schedule.getDaysOfWeek())
                .append("times", schedule.getTimes())
                .append("startDate", schedule.getStartDate())
                .append("endDate", schedule.getEndDate())
                .append("timezone", schedule.getTimezone())
                .append("active", schedule.isActive())
                .append("validFrom", schedule.getValidFrom() != null
                        ? schedule.getValidFrom().toString() : null)
                .append("validTo", schedule.getValidTo() != null
                        ? schedule.getValidTo().toString() : null);
    }

    // issue 28 - Lê listas JSON sem aceitar valores de outro tipo.
    private List<String> stringList(Document data, String field, boolean required) {
        Object value = data.get(field);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Lista obrigatoria ausente");
            }
            return null;
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("Esperada lista");
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof String)) {
                throw new IllegalArgumentException("Lista deve conter textos");
            }
            result.add((String) item);
        }
        return result;
    }

    // issue 28 - Valida textos obrigatórios sem normalização paralela à entidade.
    private String requiredText(Document data, String field) {
        String value = optionalText(data, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatorio ausente");
        }
        return value;
    }

    // issue 28 - Rejeita tipos JSON incompatíveis.
    private String optionalText(Document data, String field) {
        Object value = data.get(field);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Esperado texto");
        }
        return (String) value;
    }

    // issue 28 - Converte a vigência efetiva em Instant ISO-8601.
    private Instant instant(Document data, String field) {
        String value = requiredText(data, field);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Instante invalido", error);
        }
    }

    // issue 28 - Exige a revisão otimista enviada pelo cliente.
    private long revision(Document data) {
        Object value = data.get("revision");
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new IllegalArgumentException("Revisao obrigatoria");
        }
        long revision = ((Number) value).longValue();
        if (revision < 0 || revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Revisao invalida");
        }
        return revision;
    }
}
