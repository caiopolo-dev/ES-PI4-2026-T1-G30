//Autores: Cauã Bianchi Ferroni, Samuel Campovilla
package backend.tests;

import backend.config.MongoConnection;
import backend.handlers.TreatmentHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.*;
import backend.repositories.MongoTreatmentRepository;
import backend.repositories.TreatmentRepository;
import backend.router.Router;
import backend.services.TreatmentService;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TreatmentHttpTest {

    @Test
    void consultaPreservaCamposEInformaUnidadeSituacaoERevisao() {
        String id = criarRascunho();
        Treatment treatment = repository.findById(id).orElseThrow();
        var schedule = new CronogramaDose(List.of("08:00"), "2026-11-19", "America/Sao_Paulo");
        treatment.addMedication(new MedicamentoEmUso("med-active", "Demo", "50 mg", 1,
                "comprimido", "Observacao", true, List.of(schedule), 3));
        treatment.addMedication(new MedicamentoEmUso("med-inactive", "Outro", "10 mg", 2,
                "mL", null, false, List.of(), 5));
        repository.update(treatment);

        HttpResponse response = requisitar("GET", "/treatments/" + id, null);
        assertEquals(200, response.getStatusCode());
        var medications = Document.parse(response.getBody()).getList("medications", Document.class);
        assertEquals(2, medications.size());
        Document active = medications.stream().filter(m -> "med-active".equals(m.getString("id"))).findFirst().orElseThrow();
        assertEquals("comprimido", active.getString("unit"));
        assertTrue(active.getBoolean("active"));
        assertEquals(3L, ((Number) active.get("revision")).longValue());
        // Os campos existentes e os cronogramas continuam disponiveis.
        assertEquals("Demo", active.getString("name"));
        assertEquals("50 mg", active.getString("dose"));
        assertEquals(1, active.getInteger("quantity"));
        assertEquals("Observacao", active.getString("observations"));
        assertEquals(List.of("08:00"), active.getList("schedules", Document.class).get(0).getList("times", String.class));
        Document inactive = medications.stream().filter(m -> "med-inactive".equals(m.getString("id"))).findFirst().orElseThrow();
        assertEquals("mL", inactive.getString("unit"));
        assertFalse(inactive.getBoolean("active"));
        assertEquals(5L, ((Number) inactive.get("revision")).longValue());
        usuarioAutenticado = "usuario-B";
        assertEquals(404, requisitar("GET", "/treatments/" + id, null).getStatusCode());
    }

    private String mongoUri;
    private String databaseName;
    private MongoConnection connection;
    private TreatmentRepository repository;
    private Router router;
    private String usuarioAutenticado;

    @BeforeEach
    void preparar() {
        mongoUri = resolveMongoUri();

        // Executa os testes HTTP integrados ao MongoDB somente quando o banco estiver configurado
        assumeTrue(mongoUri != null && !mongoUri.isBlank(),
                "MONGODB_URI não configurada para testes de integração com MongoDB");

        // Banco temporário e exclusivo para cada execução de teste
        databaseName = "dosecerta_test_http_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        connection = new MongoConnection(mongoUri, databaseName);
        repository = new MongoTreatmentRepository(connection.getDatabase());

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

    @AfterEach
    void limpar() {
        if (connection != null) {
            try {
                // Remove apenas o banco temporário utilizado pelo teste
                if (connection.getDatabase() != null
                        && connection.getDatabase().getName().startsWith("dosecerta_test_")) {
                    connection.getDatabase().drop();
                }
            } finally {
                connection.close();
            }
        }
    }

    private String resolveMongoUri() {
        String uri = System.getenv("MONGODB_URI");
        if (uri == null || uri.isBlank()) {
            uri = System.getProperty("MONGODB_URI");
        }
        if ((uri == null || uri.isBlank())
                && "true".equalsIgnoreCase(System.getProperty("mongo.integration"))) {
            uri = "mongodb://127.0.0.1:27017";
        }
        return uri;
    }

    private MongoCollection<Document> treatmentsCollection() {
        return connection.getCollection(MongoTreatmentRepository.DEFAULT_COLLECTION);
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

    // Simula a inclusão feita pela outra sub-issue, persistindo diretamente no MongoDB.
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

        Treatment treatment = repository.findById(id).orElseThrow();
        treatment.addMedication(medicamento);
        repository.update(treatment);
    }

    @Test
    void deveCriarRascunhoIncompletoEPersistirNoMongoDB() {

        HttpResponse response = requisitar(
                "POST", "/treatments", "{}"
        );

        assertEquals(201, response.getStatusCode());

        Document json = Document.parse(response.getBody());
        String id = json.getString("id");

        assertEquals("DRAFT", json.getString("status"));
        assertEquals("usuario-A", json.getString("userId"));
        assertNotNull(id);

        // Confirma que o documento foi gravado fisicamente na collection treatments do MongoDB
        Document persistedDoc = treatmentsCollection().find(Filters.eq("_id", id)).first();
        assertNotNull(persistedDoc, "Rascunho criado via HTTP deve existir no MongoDB");
        assertEquals("DRAFT", persistedDoc.getString("status"));
        assertEquals("usuario-A", persistedDoc.getString("userId"));
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
        assertEquals(2L, treatmentsCollection().countDocuments());
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
    void deveAtualizarMetadadosDoRascunhoNoMongoDB() {

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

        // Confirma que as alterações foram persistidas na collection treatments
        Document persistedDoc = treatmentsCollection().find(Filters.eq("_id", id)).first();
        assertNotNull(persistedDoc);
        assertEquals("Novo nome", persistedDoc.getString("name"));
        assertEquals("Observações atualizadas", persistedDoc.getString("description"));
        assertEquals("2026-11-20", persistedDoc.getString("startDate"));
        assertNull(persistedDoc.get("endDate"));
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

        // Nenhuma requisição inválida deve persistir documentos no banco
        assertEquals(0L, treatmentsCollection().countDocuments());
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
        assertEquals(TreatmentStatus.DRAFT, repository.findById(id).orElseThrow().getStatus());
        assertEquals("DRAFT", treatmentsCollection().find(Filters.eq("_id", id)).first().getString("status"));
    }

    @Test
    void deveAtivarEEncerrarTratamentoValidoNoMongoDB() {

        String id = criarRascunho();
        adicionarMedicamento(id);

        HttpResponse ativacao = requisitar(
                "POST", "/treatments/" + id + "/activate", null
        );

        assertEquals(200, ativacao.getStatusCode());
        assertEquals("ACTIVE",
                Document.parse(ativacao.getBody()).getString("status"));
        assertEquals("ACTIVE",
                treatmentsCollection().find(Filters.eq("_id", id)).first().getString("status"));

        HttpResponse encerramento = requisitar(
                "POST", "/treatments/" + id + "/close", null
        );

        assertEquals(200, encerramento.getStatusCode());

        Document json = Document.parse(encerramento.getBody());

        assertEquals("CLOSED", json.getString("status"));
        assertEquals("2026-11-19T15:00:00Z",
                json.getString("closedAt"));

        // Confirma no MongoDB o estado encerrado, o timestamp closedAt e a preservação do medicamento
        Document closedDoc = treatmentsCollection().find(Filters.eq("_id", id)).first();
        assertNotNull(closedDoc);
        assertEquals("CLOSED", closedDoc.getString("status"));
        assertEquals(Date.from(Instant.parse("2026-11-19T15:00:00Z")), closedDoc.getDate("closedAt"));
        assertEquals(1, repository.findById(id).orElseThrow().getMedications().size());
    }

    @Test
    void deveConfirmarConexaoEPersistenciaRealEntreInstanciasNoMongoDB() {

        String id = criarRascunho();
        adicionarMedicamento(id);

        assertEquals(200, requisitar(
                "POST", "/treatments/" + id + "/activate", null
        ).getStatusCode());

        // Abre uma nova conexão MongoDB e um novo Router/Handler para provar que os dados vêm do banco
        try (MongoConnection novaConexao = new MongoConnection(mongoUri, databaseName)) {
            Document ping = novaConexao.getDatabase().runCommand(new Document("ping", 1));
            assertEquals(1.0, ((Number) ping.get("ok")).doubleValue(), 0.001);

            TreatmentRepository novoRepository = new MongoTreatmentRepository(novaConexao.getDatabase());
            TreatmentService novoService = new TreatmentService(novoRepository);
            TreatmentHandler novoHandler = new TreatmentHandler(novoService);

            Router novoRouter = new Router(request -> Optional.of("usuario-A"));
            novoRouter.register("GET", "/treatments/{id}", novoHandler);

            HttpResponse consulta = novoRouter.route(
                    new HttpRequest("GET", "/treatments/" + id, "HTTP/1.1", Collections.emptyMap(), "")
            );

            assertEquals(200, consulta.getStatusCode());
            Document body = Document.parse(consulta.getBody());
            assertEquals(id, body.getString("id"));
            assertEquals("ACTIVE", body.getString("status"));
            assertEquals("Tratamento A", body.getString("name"));
            assertEquals(1, body.getList("medications", Document.class).size());
        }
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
