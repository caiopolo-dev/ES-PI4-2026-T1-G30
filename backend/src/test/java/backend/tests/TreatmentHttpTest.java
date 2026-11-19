
package backend.tests;

import backend.handlers.TreatmentHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.*;
import backend.repositories.TreatmentRepository;
import backend.router.Router;
import backend.services.TreatmentService;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TreatmentHttpTest {

    private Router router;
    private Map<String, Treatment> dados;
    private String usuarioAutenticado;

    @BeforeEach
    void preparar() {

        // HashMap armazena os tratamentos durante cada teste.
        // Simula a persistência, mas não utiliza o MongoDB.
        dados = new HashMap<>();

        // Implementação de teste da interface TreatmentRepository.
        // Permite testar o Service sem depender da implementação Mongo.
        TreatmentRepository repository = new TreatmentRepository() {

            @Override
            public Treatment save(Treatment treatment) {
                dados.put(treatment.getId(), treatment);
                return treatment;
            }

            @Override
            public Optional<Treatment> findById(String id) {
                return Optional.ofNullable(dados.get(id));
            }

            @Override
            public List<Treatment> findByUserId(String userId) {

                List<Treatment> resultado = new ArrayList<>();

                for (Treatment treatment : dados.values()) {
                    if (userId.equals(treatment.getUserId())) {
                        resultado.add(treatment);
                    }
                }

                return resultado;
            }

            @Override
            public Treatment update(Treatment treatment) {
                if (!dados.containsKey(treatment.getId())) {
                    throw new NoSuchElementException();
                }

                dados.put(treatment.getId(), treatment);
                return treatment;
            }
        };

        // Clock.fixed simula um horário conhecido.
        // Assim, o closedAt pode ser testado de forma previsível.
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-19T15:00:00Z"),
                ZoneOffset.UTC
        );

        TreatmentService service = new TreatmentService(repository, clock);
        TreatmentHandler handler = new TreatmentHandler(service);

        usuarioAutenticado = "usuario-A";

        // Simula a autenticação apenas nos testes.
        // Não é um mecanismo de autenticação para produção.
        router = new Router(
                request -> Optional.ofNullable(usuarioAutenticado)
        );

        // Registra os mesmos seis endpoints do Main.
        router.register("POST", "/treatments", handler);
        router.register("GET", "/treatments", handler);
        router.register("GET", "/treatments/{id}", handler);
        router.register("PUT", "/treatments/{id}", handler);
        router.register("POST", "/treatments/{id}/activate", handler);
        router.register("POST", "/treatments/{id}/close", handler);
    }

    // Simula uma requisição HTTP passando pelo Router.
    // É o ponto de entrada dos testes de endpoints.
    private HttpResponse requisitar(
            String metodo, String caminho, String corpo) {

        Map<String, String> headers = new HashMap<>();

        if (corpo != null) {
            headers.put("Content-Type", "application/json");
        }

        HttpRequest request = new HttpRequest(
                metodo, caminho, "HTTP/1.1",
                headers, corpo == null ? "" : corpo
        );

        return router.route(request);
    }

    // Cria um rascunho e retorna seu ID.
    // Evita repetir a mesma requisição em todos os testes.
    private String criarRascunho() {

        String json = new Document()
                .append("name", "Tratamento A")
                .append("startDate", "2026-11-19")
                .toJson();

        HttpResponse response = requisitar(
                "POST", "/treatments", json
        );

        assertEquals(201, response.getStatusCode());

        return Document.parse(response.getBody()).getString("id");
    }

    // Simula a inclusão feita pela outra sub-issue.
    // Aqui não estamos testando o endpoint de medicamentos.
    private void adicionarMedicamento(String id) {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00"),
                "2026-11-19",
                "America/Sao_Paulo"
        );

        MedicamentoEmUso medicamento = new MedicamentoEmUso(
                "med-1", "Medicamento A", "1 comprimido", cronograma
        );

        dados.get(id).addMedication(medicamento);
    }

    @Test
    void deveCriarRascunhoIncompleto() {

        HttpResponse response = requisitar(
                "POST", "/treatments", "{}"
        );

        assertEquals(201, response.getStatusCode());

        Document json = Document.parse(response.getBody());

        assertEquals("DRAFT", json.getString("status"));
        assertEquals("usuario-A", json.getString("userId"));
        assertNotNull(json.getString("id"));
    }

    @Test
    void deveListarSomenteTratamentosDoTitular() {

        String idA = criarRascunho();

        usuarioAutenticado = "usuario-B";
        String idB = criarRascunho();

        usuarioAutenticado = "usuario-A";

        HttpResponse response = requisitar(
                "GET", "/treatments", null
        );

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains(idA));
        assertFalse(response.getBody().contains(idB));
    }

    @Test
    void deveConsultarTratamentoPeloId() {

        String id = criarRascunho();

        HttpResponse response = requisitar(
                "GET", "/treatments/" + id, null
        );

        assertEquals(200, response.getStatusCode());

        Document json = Document.parse(response.getBody());

        assertEquals(id, json.getString("id"));
        assertEquals("Tratamento A", json.getString("name"));
    }

    @Test
    void deveAtualizarMetadadosDoRascunho() {

        String id = criarRascunho();

        String corpo = new Document()
                .append("name", "Novo nome")
                .append("description", "Observações atualizadas")
                .append("startDate", "2026-11-20")
                .append("endDate", null)
                .toJson();

        HttpResponse response = requisitar(
                "PUT", "/treatments/" + id, corpo
        );

        assertEquals(200, response.getStatusCode());

        Document json = Document.parse(response.getBody());

        assertEquals("Novo nome", json.getString("name"));
        assertEquals("2026-11-20", json.getString("startDate"));
    }

    @Test
    void deveRejeitarDadosInvalidos() {

        // Uma data inexistente deve gerar HTTP 400.
        String dataInvalida = new Document()
                .append("name", "Tratamento")
                .append("startDate", "2026-02-30")
                .toJson();

        assertEquals(400, requisitar(
                "POST", "/treatments", dataInvalida
        ).getStatusCode());

        // O cliente não pode informar seu próprio userId.
        String titularForjado = new Document()
                .append("name", "Tratamento")
                .append("userId", "outro-usuario")
                .toJson();

        assertEquals(400, requisitar(
                "POST", "/treatments", titularForjado
        ).getStatusCode());
    }

    @Test
    void deveExigirAutenticacao() {

        usuarioAutenticado = null;

        HttpResponse response = requisitar(
                "GET", "/treatments", null
        );

        assertEquals(401, response.getStatusCode());
    }

    @Test
    void naoDevePermitirAcessoDeOutroTitular() {

        String id = criarRascunho();

        usuarioAutenticado = "usuario-B";

        // Para outro usuário, o tratamento deve parecer inexistente.
        assertEquals(404, requisitar(
                "GET", "/treatments/" + id, null
        ).getStatusCode());

        assertEquals(404, requisitar(
                "POST", "/treatments/" + id + "/activate", null
        ).getStatusCode());

        assertEquals(404, requisitar(
                "POST", "/treatments/" + id + "/close", null
        ).getStatusCode());
    }

    @Test
    void naoDeveAtivarTratamentoIncompleto() {

        String id = criarRascunho();

        HttpResponse response = requisitar(
                "POST", "/treatments/" + id + "/activate", null
        );

        assertEquals(400, response.getStatusCode());
        assertEquals(TreatmentStatus.DRAFT, dados.get(id).getStatus());
    }

    @Test
    void deveAtivarEEncerrarTratamentoValido() {

        String id = criarRascunho();
        adicionarMedicamento(id);

        HttpResponse ativacao = requisitar(
                "POST", "/treatments/" + id + "/activate", null
        );

        assertEquals(200, ativacao.getStatusCode());
        assertEquals("ACTIVE",
                Document.parse(ativacao.getBody()).getString("status"));

        HttpResponse encerramento = requisitar(
                "POST", "/treatments/" + id + "/close", null
        );

        assertEquals(200, encerramento.getStatusCode());

        Document json = Document.parse(encerramento.getBody());

        assertEquals("CLOSED", json.getString("status"));
        assertEquals("2026-11-19T15:00:00Z",
                json.getString("closedAt"));

        // O encerramento não pode apagar o medicamento.
        assertEquals(1, dados.get(id).getMedications().size());
    }

    @Test
    void deveRejeitarTransicoesInvalidas() {

        String id = criarRascunho();

        // Não é permitido encerrar um DRAFT.
        assertEquals(409, requisitar(
                "POST", "/treatments/" + id + "/close", null
        ).getStatusCode());

        adicionarMedicamento(id);

        assertEquals(200, requisitar(
                "POST", "/treatments/" + id + "/activate", null
        ).getStatusCode());

        // Não é permitido ativar duas vezes.
        assertEquals(409, requisitar(
                "POST", "/treatments/" + id + "/activate", null
        ).getStatusCode());

        assertEquals(200, requisitar(
                "POST", "/treatments/" + id + "/close", null
        ).getStatusCode());

        // Tratamentos encerrados não podem ser reativados.
        assertEquals(409, requisitar(
                "POST", "/treatments/" + id + "/activate", null
        ).getStatusCode());
    }

    @Test
    void deveResponderRotaInexistenteEMetodoInvalido() {

        assertEquals(404, requisitar(
                "GET", "/rota-inexistente", null
        ).getStatusCode());

        assertEquals(405, requisitar(
                "DELETE", "/treatments/123", null
        ).getStatusCode());
    }
}
