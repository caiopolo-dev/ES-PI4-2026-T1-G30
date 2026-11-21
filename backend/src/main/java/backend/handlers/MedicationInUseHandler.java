package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.MedicamentoEmUso;
import backend.router.HttpHandler;
import backend.router.Router;
import backend.services.MedicationInUseService;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import org.bson.Document;

/** Converte HTTP nas operacoes do servico de medicamentos da #27. */
public final class MedicationInUseHandler implements HttpHandler {
    private static final Set<String> FIELDS = Set.of(
        "name", "informedDose", "quantityPerAdministration", "unit", "notes", "revision", "validFrom"
    );
    private final MedicationInUseService service;

    public MedicationInUseHandler(MedicationInUseService service) {
        this.service = Objects.requireNonNull(service);
    }

    public void registerRoutes(Router router) {
        router.register("POST", "/treatments/{id}/medications", this);
        router.register("PUT", "/treatments/{id}/medications/{medicationId}", this);
        router.register("DELETE", "/treatments/{id}/medications/{medicationId}", this);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        var context = request.getAuthContext().orElse(null);
        if (context == null) { return HttpResponse.unauthorized("Invalid, expired, or missing session"); }
        String[] path = request.getPath().split("/", -1);
        if ((path.length != 4 && path.length != 5) || !path[1].equals("treatments")
                || !path[3].equals("medications") || path[2].isEmpty()
                || (path.length == 5 && path[4].isEmpty())) {
            return HttpResponse.notFound("Recurso nao encontrado.");
        }
        if (!request.getRawQuery().isEmpty()) { return HttpResponse.badRequest("Query nao suportada."); }
        try {
            if (request.getMethod().equals("DELETE") && path.length == 5) {
                if (!request.getBody().isBlank()) { return HttpResponse.badRequest("Corpo nao suportado."); }
                service.deactivate(context, path[2], path[4]);
                return new HttpResponse(204, "No Content", "");
            }
            boolean create = request.getMethod().equals("POST") && path.length == 4;
            boolean edit = request.getMethod().equals("PUT") && path.length == 5;
            if (!create && !edit) { return HttpResponse.methodNotAllowed("Metodo nao permitido."); }
            Document data = parseBody(request);
            if (create && (data.containsKey("revision") || data.containsKey("validFrom"))) {
                throw new IllegalArgumentException("Revisao e vigencia nao pertencem ao cadastro inicial.");
            }
            long revision = edit ? revision(data) : 0L;
            Integer quantity = quantity(data);
            String name = text(data, "name");
            String dose = text(data, "informedDose");
            String unit = text(data, "unit");
            String notes = text(data, "notes");
            if (name == null || name.isBlank() || dose == null || dose.isBlank()) {
                return HttpResponse.badRequest("Nome e dose informada obrigatorios no modelo atual.");
            }
            MedicamentoEmUso medication;

            if (create) {
                medication = service.add(context, path[2], name, dose, quantity, unit, notes);
            } else if (data.containsKey("validFrom")) {
                String validFrom = text(data, "validFrom");

                if (validFrom == null || validFrom.isBlank()) {
                    throw new IllegalArgumentException("Inicio de vigencia obrigatorio.");
                }

                java.time.Instant effective;

                try {
                    effective = java.time.Instant.parse(validFrom);
                } catch (java.time.format.DateTimeParseException invalid) {
                    throw new IllegalArgumentException("Vigencia invalida.", invalid);
                }

                // Encaminhei a edicao com vigencia para gravar medicamento e versoes juntos.
                medication = service.saveVersionedUpdate(context, path[2], path[4], name, dose,
                    quantity, unit, notes, revision, effective);
            } else {
                medication = service.update(context, path[2], path[4], name, dose, quantity, unit, notes, revision);
            }
            return new HttpResponse(create ? 201 : 200, create ? "Created" : "OK", toJson(medication));
        } catch (IllegalArgumentException invalid) {
            return HttpResponse.badRequest("Dados de medicamento invalidos.");
        } catch (backend.services.MedicationOperationConflictException conflict) {
            return new HttpResponse(409, "Conflict", "{\"error\":\"Operacao negada pelas regras do tratamento ou exige versionamento.\"}");
        }
        // O Router trata falta de titularidade e falhas inesperadas sem expor detalhes.
    }

    private Document parseBody(HttpRequest request) {
        String contentType = request.getHeader("Content-Type");
        if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw new IllegalArgumentException("Esperado JSON.");
        }
        String body = request.getBody();
        if (body.isBlank() || body.getBytes(StandardCharsets.UTF_8).length > 16384) {
            throw new IllegalArgumentException("Corpo vazio ou acima de 16 KiB.");
        }
        Document data;
        try { data = Document.parse(body); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("JSON invalido."); }
        if (!FIELDS.containsAll(data.keySet())) { throw new IllegalArgumentException("Campo nao permitido."); }
        return data;
    }

    private String text(Document data, String field) {
        Object value = data.get(field);
        if (value != null && !(value instanceof String)) { throw new IllegalArgumentException("Esperado texto."); }
        return (String) value;
    }

    private Integer quantity(Document data) {
        Object value = data.get("quantityPerAdministration");
        if (value == null) { return null; }
        // A quantidade atual do dominio e inteira; nao trunca valores fracionarios.
        if (!(value instanceof Integer quantity) || quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser inteira e positiva.");
        }
        return quantity;
    }

    private String toJson(MedicamentoEmUso medication) {
        return new Document("id", medication.getId()).append("name", medication.getName())
            .append("informedDose", medication.getDose()).append("quantityPerAdministration", medication.getQuantity())
            .append("unit", medication.getUnit()).append("notes", medication.getObservations())
            .append("active", medication.isActive()).append("revision", medication.getRevision()).toJson();
    }

    private long revision(Document data) {
        Object value = data.get("revision");
        if (!(value instanceof Integer) && !(value instanceof Long)) { throw new IllegalArgumentException("Revisao obrigatoria."); }
        long revision = ((Number) value).longValue();
        if (revision < 0 || revision == Long.MAX_VALUE) { throw new IllegalArgumentException("Revisao invalida."); }
        return revision;
    }
}
