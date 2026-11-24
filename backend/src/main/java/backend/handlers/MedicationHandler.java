// Desenvolvido por Miguel Gallinucci.
package backend.handlers;

import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.Medication;
import backend.router.HttpHandler;
import backend.services.MedicationService;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Handler responsável pelo endpoint GET /medications.
 * Delega a busca ao serviço e converte os medicamentos em resposta HTTP.
 */
public class MedicationHandler implements HttpHandler {
    // Handler e quem atende a requisicao HTTP; aqui ele transforma a consulta em uma resposta.

    private final MedicationService medicationService;

    public MedicationHandler(MedicationService medicationService) {
        // Recebi o servico pronto para o handler cuidar apenas da resposta HTTP.
        this.medicationService = Objects.requireNonNull(
            medicationService, "medicationService não pode ser null"
        );
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        // Usei StringJoiner para separar os itens por virgula e retornar [] quando nao houver medicamentos.
        StringJoiner medicationsJson = new StringJoiner(",", "[", "]");
        // JSON e um formato de troca de dados; usei para o cliente conseguir ler a lista de medicamentos.
        // Montei o JSON a partir da consulta ao servico, sem deixar a resposta fixa no handler.
        for (Medication medication : medicationService.listMedications()) {
            medicationsJson.add(
                "{\"id\":" + toJsonString(medication.getId())
                + ",\"name\":" + toJsonString(medication.getName()) + "}"
            );
        }
        // Mesmo uma lista vazia e uma consulta bem-sucedida, por isso retorno 200 com [].
        return HttpResponse.ok(medicationsJson.toString());
    }

    private static String toJsonString(String value) {
        if (value == null) {
            // null sem aspas representa um valor ausente; com aspas seria o texto "null".
            return "null";
        }

        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            // Fiz o escape para aspas e barras presentes no nome nao quebrarem o JSON.
            switch (character) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                default:
                    // Quebras de linha e outros caracteres de controle precisam ser escritos como escape.
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
            }
        }
        return json.append('"').toString();
    }
}

