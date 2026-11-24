// Desenvolvido por Caio Ferreira Polo.
package backend.handlers;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.router.HttpHandler;
import backend.services.DoseRecordConflictException;
import backend.services.DoseService;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import org.bson.Document;

/**
 * Recebe o registro de uma dose pela rota PUT /doses/{id}/record.
 * O handler valida o contrato HTTP e entrega ao DoseService as regras
 * relacionadas à dose, à titularidade e aos conflitos de registro.
 */
public final class DoseRecordHandler implements HttpHandler {
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Set<String> FIELDS = Set.of("action");

    private final DoseService service;

    public DoseRecordHandler(DoseService service) {
        this.service = Objects.requireNonNull(service);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        // A identidade vem da sessão autenticada, não de um ID enviado no corpo.
        AuthContext context = request.getAuthContext().orElse(null);
        if (context == null) {
            return HttpResponse.unauthorized("Invalid, expired, or missing session");
        }

        if (!"PUT".equals(request.getMethod())) {
            return HttpResponse.methodNotAllowed("Metodo nao permitido.");
        }

        String[] path = request.getPath().split("/", -1);
        if (path.length != 4 || !"doses".equals(path[1]) || path[2].isEmpty() || !"record".equals(path[3])) {
            return HttpResponse.notFound("Recurso nao encontrado.");
        }

        if (!request.getRawQuery().isEmpty()) {
            return HttpResponse.badRequest("Query nao suportada.");
        }

        try {

            // Primeiro interpreto a ação; depois o service localiza e registra a dose.
            DoseAction action = parseAction(request);
            DosePrevista plannedDose = service.findPlannedDose(context, path[2]);
            RegistroDeDose record = service.recordDose(context, path[2], action);
            return HttpResponse.ok(toJson(record, plannedDose));

        } catch (ResourceNotFoundException error) {

            return HttpResponse.notFound("Dose nao encontrada.");
        } catch (DoseRecordConflictException error) {

            // O conflito indica que a dose não pode receber a operação solicitada.
            return new HttpResponse(409, "Conflict", "{\"error\":\"Operacao de registro em conflito.\"}");
        } catch (IllegalArgumentException error) {

            return HttpResponse.badRequest("Dados do registro invalidos.");
        } catch (RuntimeException error) {

            return HttpResponse.internalServerError("Internal server error");
        }
    }

    private DoseAction parseAction(HttpRequest request) {
        // Este endpoint aceita somente um objeto JSON com a ação da dose.
        String contentType = request.getHeader("Content-Type");

        if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw new IllegalArgumentException("Esperado JSON.");
        }

        String body = request.getBody();
        // O limite evita processar um corpo inesperadamente grande para uma operação simples.
        if (body.isBlank() || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Corpo vazio ou acima de 16 KiB.");
        }

        Document data;
        try {
            data = Document.parse(body);

        } catch (RuntimeException error) {
            throw new IllegalArgumentException("JSON invalido.", error);
        }

        if (!FIELDS.containsAll(data.keySet()) || !data.containsKey("action")) {
            // Campos extras ou a ausência de action tornam o comando inválido.
            throw new IllegalArgumentException("Payload invalido.");
        }

        Object value = data.get("action");
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Acao deve ser textual.");
        }

        String action = (String) value;
        if ("TAKEN".equals(action)) {
            return DoseAction.TAKEN;
        }
        if ("SKIPPED".equals(action)) {
            return DoseAction.SKIPPED;
        }
        throw new IllegalArgumentException("Acao desconhecida.");
    }

    private String toJson(RegistroDeDose record, DosePrevista plannedDose) {
        // A resposta combina o registro realizado com os dados da dose planejada.
        return new Document("plannedDoseId", record.getPlannedDoseId())
                .append("action", record.getAction().name())
                .append("scheduledAt", plannedDose.getScheduledAt().toString())
                .append("scheduleVersion", plannedDose.getScheduleVersion())
                .append("actualTime", record.getActualTime() == null
                        ? null : record.getActualTime().toString())
                .append("recordedAt", record.getRecordedAt().toString())
                .toJson();
    }
}
