//Autor: Samuel Campovilla
package backend.handlers;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.router.HttpHandler;
import backend.services.TreatmentService;

import org.bson.Document;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class TreatmentHandler implements HttpHandler {

    // Serviço responsável pelas operações de negócio.
    // O Handler não consulta o MongoDB diretamente.
    private final TreatmentService treatmentService;

    public TreatmentHandler(TreatmentService treatmentService) {
        this.treatmentService = Objects.requireNonNull(treatmentService);
    }

    // Ponto de entrada chamado pelo Router para processar a requisição.
    @Override
    public HttpResponse handle(HttpRequest request) {

        // Obtém a identidade validada pelo Router, nunca pelo JSON enviado.
        AuthContext context = request.getAuthContext().orElse(null);

        if (context == null) {
            return HttpResponse.unauthorized("Autenticação necessária.");
        }

        String metodo = request.getMethod();
        String[] partes = request.getPath().split("/", -1);

        try {

            // POST /treatments
            if (partes.length == 2 && "POST".equals(metodo)) {
                return criar(request, context);
            }

            // GET /treatments
            if (partes.length == 2 && "GET".equals(metodo)) {
                return listar(context);
            }

            if (partes.length >= 3 && !partes[2].isBlank()) {

                String treatmentId = partes[2];

                // GET /treatments/{id}
                if (partes.length == 3 && "GET".equals(metodo)) {
                    return HttpResponse.ok(
                            toDocument(treatmentService.find(context, treatmentId)).toJson()
                    );
                }

                // PUT /treatments/{id}
                if (partes.length == 3 && "PUT".equals(metodo)) {
                    return atualizar(request, context, treatmentId);
                }

                if (partes.length == 4 && "POST".equals(metodo)) {

                    if (!request.getBody().isBlank()) {
                        return HttpResponse.badRequest("Corpo não esperado.");
                    }

                    // POST /treatments/{id}/activate
                    if ("activate".equals(partes[3])) {
                        Treatment treatment = treatmentService.activate(context, treatmentId);
                        return HttpResponse.ok(toDocument(treatment).toJson());
                    }

                    // POST /treatments/{id}/close
                    if ("close".equals(partes[3])) {
                        Treatment treatment = treatmentService.close(context, treatmentId);
                        return HttpResponse.ok(toDocument(treatment).toJson());
                    }
                }
            }

            return HttpResponse.notFound("Rota não encontrada.");

        } catch (ResourceNotFoundException e) {
            // Mesmo retorno para recurso inexistente ou de outro usuário
            return HttpResponse.notFound("Tratamento não encontrado.");

        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Dados do tratamento inválidos.");

        } catch (IllegalStateException e) {
            // Transição de estado incompatível
            return new HttpResponse(
                    409, "Conflict",
                    "{\"error\":\"Operação incompatível com o estado do tratamento.\"}"
            );

        } catch (RuntimeException e) {
            return HttpResponse.internalServerError("Erro interno.");
        }
    }

    // Cadastra um tratamento em DRAFT.
    private HttpResponse criar(HttpRequest request, AuthContext context) {

        Document json = lerJson(request);

        Treatment treatment = treatmentService.createDraft(
                context,
                texto(json, "name"),
                texto(json, "description"),
                data(json, "startDate"),
                data(json, "endDate")
        );

        return new HttpResponse(
                201, "Created", toDocument(treatment).toJson()
        );
    }

    // Lista somente os tratamentos pertencentes ao usuário autenticado.
    private HttpResponse listar(AuthContext context) {

        List<Treatment> tratamentos = treatmentService.list(context);
        StringJoiner json = new StringJoiner(",", "[", "]");

        for (Treatment treatment : tratamentos) {
            json.add(toDocument(treatment).toJson());
        }

        return HttpResponse.ok(json.toString());
    }

    // Atualiza os metadados permitidos de um tratamento.
    private HttpResponse atualizar(
            HttpRequest request,
            AuthContext context,
            String treatmentId) {

        Document json = lerJson(request);

        // PUT utiliza a representação completa dos metadados.
        if (!json.containsKey("name")
                || !json.containsKey("description")
                || !json.containsKey("startDate")
                || !json.containsKey("endDate")) {
            throw new IllegalArgumentException("Campos obrigatórios ausentes.");
        }

        Treatment treatment = treatmentService.updateMetadata(
                context,
                treatmentId,
                texto(json, "name"),
                texto(json, "description"),
                data(json, "startDate"),
                data(json, "endDate")
        );

        return HttpResponse.ok(toDocument(treatment).toJson());
    }

    // Converte o corpo da requisição em Document.
    // Rejeita campos desconhecidos, incluindo userId vindo do cliente.
    private Document lerJson(HttpRequest request) {

        String contentType = request.getHeader("Content-Type");

        if (contentType == null
                || !contentType.split(";", 2)[0].trim()
                .equalsIgnoreCase("application/json")) {
            throw new IllegalArgumentException("Content-Type inválido.");
        }

        if (request.getBody().isBlank()) {
            throw new IllegalArgumentException("Corpo vazio.");
        }

        Document json;

        try {
            json = Document.parse(request.getBody());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON inválido.");
        }

        for (String chave : json.keySet()) {
            if (!List.of("name", "description", "startDate", "endDate")
                    .contains(chave)) {
                throw new IllegalArgumentException("Campo não permitido.");
            }
        }

        return json;
    }

    // Obtém um campo textual, aceitando null em rascunhos.
    private String texto(Document json, String campo) {

        Object valor = json.get(campo);

        if (valor != null && !(valor instanceof String)) {
            throw new IllegalArgumentException("Campo deve ser texto.");
        }

        return (String) valor;
    }

    // Converte datas recebidas no formato yyyy-MM-dd.
    private LocalDate data(Document json, String campo) {

        String valor = texto(json, campo);

        if (valor == null) {
            return null;
        }

        if (!valor.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Formato de data inválido.");
        }

        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida.");
        }
    }

    // Converte o tratamento e seus medicamentos para a resposta JSON.
    // Mantém as versões dos cronogramas para consulta do histórico.
    private Document toDocument(Treatment treatment) {

        Document json = new Document()
                .append("id", treatment.getId())
                .append("userId", treatment.getUserId())
                .append("name", treatment.getName())
                .append("description", treatment.getDescription())
                .append("startDate", treatment.getStartDate() == null
                        ? null : treatment.getStartDate().toString())
                .append("endDate", treatment.getEndDate() == null
                        ? null : treatment.getEndDate().toString())
                .append("status", treatment.getStatus().name())
                .append("closedAt", treatment.getClosedAt() == null
                        ? null : treatment.getClosedAt().toString());

        java.util.List<Document> medicamentos = new java.util.ArrayList<>();

        for (MedicamentoEmUso med : treatment.getMedications()) {

            Document medicamento = new Document()
                    .append("id", med.getId())
                    .append("name", med.getName())
                    .append("dose", med.getDose())
                    .append("quantity", med.getQuantity())
                    .append("observations", med.getObservations());

            java.util.List<Document> cronogramas = new java.util.ArrayList<>();

            for (CronogramaDose cronograma : med.getSchedules()) {
                cronogramas.add(new Document()
                        .append("version", cronograma.getVersion())
                        .append("daysOfWeek", cronograma.getDaysOfWeek())
                        .append("times", cronograma.getTimes())
                        .append("startDate", cronograma.getStartDate())
                        .append("endDate", cronograma.getEndDate())
                        .append("timezone", cronograma.getTimezone())
                        .append("active", cronograma.isActive()));
            }

            medicamento.append("schedules", cronogramas);
            medicamentos.add(medicamento);
        }

        json.append("medications", medicamentos);

        return json;
    }
}
