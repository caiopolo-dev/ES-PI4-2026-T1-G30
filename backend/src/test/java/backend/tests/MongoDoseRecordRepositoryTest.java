
package backend.tests;

import backend.models.DoseAction;
import backend.models.RegistroDeDose;
import backend.repositories.MongoDoseRecordRepository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class MongoDoseRecordRepositoryTest {

    private MongoClient client;
    private MongoDatabase database;
    private MongoDoseRecordRepository repository;

    @BeforeEach
    void preparar() {
        String uri = System.getenv("MONGODB_URI");

        // Executa os testes de integração somente quando o MongoDB estiver configurado
        assumeTrue(uri != null && !uri.isBlank(),
                "MONGODB_URI não configurada");

        client = MongoClients.create(uri);

        // Cria um banco temporário e independente para cada teste
        String nomeBanco = "dosecerta_test_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        database = client.getDatabase(nomeBanco);
        repository = new MongoDoseRecordRepository(database);
    }

    @AfterEach
    void limpar() {
        try {
            // Remove somente o banco temporário utilizado no teste
            if (database != null &&
                    database.getName().startsWith("dosecerta_test_")) {
                database.drop();
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    // Método auxiliar para criar registros utilizados nos testes
    private RegistroDeDose criarRegistro(
            String id,
            String plannedDoseId,
            String userId,
            DoseAction action,
            String actualTime,
            String recordedAt) {

        return new RegistroDeDose(
                id,
                plannedDoseId,
                userId,
                action,
                actualTime != null ? Instant.parse(actualTime) : null,
                Instant.parse(recordedAt)
        );
    }

    @Test
    void testSaveTaken() {
        RegistroDeDose registro = criarRegistro(
                "registro-001",
                "dose-001",
                "user-001",
                DoseAction.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        RegistroDeDose salvo = repository.save(registro);

        // Confirma que o método retornou o registro corretamente
        assertEquals(registro.getId(), salvo.getId());
        assertEquals("user-001", salvo.getUserId());
        assertEquals(DoseAction.TAKEN, salvo.getAction());
        assertEquals(registro.getActualTime(), salvo.getActualTime());

        // Confirma que o documento foi persistido no MongoDB
        Document documento = database.getCollection("dose_records")
                .find(new Document("_id", "registro-001"))
                .first();

        assertNotNull(documento);
        assertEquals("dose-001", documento.getString("plannedDoseId"));
        assertEquals("user-001", documento.getString("userId"));
        assertEquals("TAKEN", documento.getString("action"));
        assertNotNull(documento.getDate("actualTime"));
        assertNotNull(documento.getDate("recordedAt"));
    }

    @Test
    void testSaveSkipped() {
        RegistroDeDose registro = criarRegistro(
                "registro-002",
                "dose-002",
                "user-001",
                DoseAction.SKIPPED,
                null,
                "2026-11-18T09:00:00Z"
        );

        RegistroDeDose salvo = repository.save(registro);

        // SKIPPED não pode possuir horário de ingestão
        assertEquals(DoseAction.SKIPPED, salvo.getAction());
        assertNull(salvo.getActualTime());

        // Confirma que o valor null foi preservado no banco
        Document documento = database.getCollection("dose_records")
                .find(new Document("_id", "registro-002"))
                .first();

        assertNotNull(documento);
        assertEquals("SKIPPED", documento.getString("action"));
        assertEquals("user-001", documento.getString("userId"));
        assertNull(documento.getDate("actualTime"));
    }

    @Test
    void testSaveNaoDuplicaRegistros() {
        RegistroDeDose primeiro = criarRegistro(
                "registro-001",
                "dose-001",
                "user-001",
                DoseAction.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        RegistroDeDose segundo = criarRegistro(
                "registro-999",
                "dose-001",
                "user-001",
                DoseAction.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:15:00Z"
        );

        repository.save(primeiro);
        RegistroDeDose salvo = repository.save(segundo);

        // A mesma dose prevista não deve gerar dois registros
        assertEquals("registro-001", salvo.getId());
        assertEquals(1, repository.countAll());

        // O horário do primeiro registro deve ser preservado
        assertEquals(primeiro.getRecordedAt(), salvo.getRecordedAt());
    }

    @Test
    void testSaveNaoPermiteAcoesContraditorias() {
        RegistroDeDose tomado = criarRegistro(
                "registro-001",
                "dose-001",
                "user-001",
                DoseAction.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        RegistroDeDose ignorado = criarRegistro(
                "registro-002",
                "dose-001",
                "user-001",
                DoseAction.SKIPPED,
                null,
                "2026-11-18T08:20:00Z"
        );

        repository.save(tomado);

        // Não permite registrar SKIPPED para uma dose já registrada como TAKEN
        assertThrows(IllegalStateException.class, () ->
                repository.save(ignorado));

        // Confirma que a ação original foi preservada
        RegistroDeDose existente =
                repository.findByPlannedDoseId("dose-001").orElseThrow();

        assertEquals(DoseAction.TAKEN, existente.getAction());
        assertEquals(1, repository.countAll());
    }

    @Test
    void testSaveNaoPermiteOutroUsuario() {
        RegistroDeDose primeiro = criarRegistro(
                "registro-001",
                "dose-001",
                "user-001",
                DoseAction.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        RegistroDeDose outroUsuario = criarRegistro(
                "registro-002",
                "dose-001",
                "user-002",
                DoseAction.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        repository.save(primeiro);

        // Não permite salvar a mesma dose como se pertencesse a outro usuário
        assertThrows(IllegalStateException.class, () ->
                repository.save(outroUsuario));

        // Confirma que a titularidade original foi preservada
        RegistroDeDose existente =
                repository.findByPlannedDoseId("dose-001").orElseThrow();

        assertEquals("user-001", existente.getUserId());
        assertEquals(1, repository.countAll());
    }

    @Test
    void testFindByPlannedDoseId() {
        RegistroDeDose registro = criarRegistro(
                "registro-001",
                "dose-001",
                "user-001",
                DoseAction.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        repository.save(registro);

        // Busca um registro pelo identificador da dose prevista
        Optional<RegistroDeDose> encontrado =
                repository.findByPlannedDoseId("dose-001");

        assertTrue(encontrado.isPresent());
        assertEquals("registro-001", encontrado.get().getId());
        assertEquals("dose-001", encontrado.get().getPlannedDoseId());
        assertEquals("user-001", encontrado.get().getUserId());
    }

    @Test
    void testFindByPlannedDoseIdInexistente() {
        // Uma dose sem registro deve retornar Optional vazio
        Optional<RegistroDeDose> encontrado =
                repository.findByPlannedDoseId("dose-inexistente");

        assertTrue(encontrado.isEmpty());

        // A consulta não deve criar um registro SKIPPED automaticamente
        assertEquals(0, repository.countAll());
    }

    @Test
    void testFindByPeriod() {
        // Registro anterior ao período
        repository.save(criarRegistro(
                "registro-001", "dose-001", "user-001", DoseAction.TAKEN,
                "2026-11-18T07:50:00Z", "2026-11-18T07:59:59Z"
        ));

        // Registro exatamente no início do período
        repository.save(criarRegistro(
                "registro-002", "dose-002", "user-001", DoseAction.TAKEN,
                "2026-11-18T07:55:00Z", "2026-11-18T08:00:00Z"
        ));

        // Registro SKIPPED dentro do período
        repository.save(criarRegistro(
                "registro-003", "dose-003", "user-001", DoseAction.SKIPPED,
                null, "2026-11-18T10:30:00Z"
        ));

        // Registro exatamente no fim do período
        repository.save(criarRegistro(
                "registro-004", "dose-004", "user-001", DoseAction.TAKEN,
                "2026-11-18T11:55:00Z", "2026-11-18T12:00:00Z"
        ));

        Instant inicio = Instant.parse("2026-11-18T08:00:00Z");
        Instant fim = Instant.parse("2026-11-18T12:00:00Z");

        List<RegistroDeDose> registros = repository.findByPeriod(inicio, fim);

        // O início é inclusivo e o fim é exclusivo
        assertEquals(2, registros.size());

        Set<String> ids = registros.stream()
                .map(RegistroDeDose::getId)
                .collect(Collectors.toSet());

        assertEquals(Set.of("registro-002", "registro-003"), ids);
    }

    @Test
    void testFindByPlannedDoseIds() {
        repository.save(criarRegistro(
                "registro-001", "dose-001", "user-001", DoseAction.TAKEN,
                "2026-11-18T08:05:00Z", "2026-11-18T08:10:00Z"
        ));

        repository.save(criarRegistro(
                "registro-002", "dose-002", "user-001", DoseAction.SKIPPED,
                null, "2026-11-18T09:00:00Z"
        ));

        repository.save(criarRegistro(
                "registro-003", "dose-003", "user-002", DoseAction.TAKEN,
                "2026-11-18T10:05:00Z", "2026-11-18T10:10:00Z"
        ));

        // Busca em lote apenas os registros das doses informadas
        Map<String, RegistroDeDose> registros =
                repository.findByPlannedDoseIds(
                        List.of("dose-001", "dose-002", "dose-inexistente")
                );

        assertEquals(2, registros.size());

        // Os registros são encontrados pelo ID da dose prevista
        assertEquals("registro-001", registros.get("dose-001").getId());
        assertEquals("registro-002", registros.get("dose-002").getId());

        // Uma dose sem registro não deve aparecer no Map
        assertNull(registros.get("dose-inexistente"));

        // A dose não solicitada também não deve aparecer
        assertNull(registros.get("dose-003"));
    }

    @Test
    void testFindByPlannedDoseIdsAndUserId() {
        repository.save(criarRegistro(
                "registro-001", "dose-001", "user-001", DoseAction.TAKEN,
                "2026-11-18T08:05:00Z", "2026-11-18T08:10:00Z"
        ));

        repository.save(criarRegistro(
                "registro-002", "dose-002", "user-002", DoseAction.SKIPPED,
                null, "2026-11-18T09:00:00Z"
        ));

        // Busca registros das duas doses, mas somente do usuário 001
        Map<String, RegistroDeDose> registros =
                repository.findByPlannedDoseIdsAndUserId(
                        List.of("dose-001", "dose-002"),
                        "user-001"
                );

        // Não deve retornar registros pertencentes a outro usuário
        assertEquals(1, registros.size());
        assertTrue(registros.containsKey("dose-001"));
        assertFalse(registros.containsKey("dose-002"));

        assertEquals("user-001", registros.get("dose-001").getUserId());
    }

    @Test
    void testConsultaEmLoteVazia() {
        // Uma coleção vazia não deve retornar registros
        Map<String, RegistroDeDose> registros =
                repository.findByPlannedDoseIds(List.of());

        assertTrue(registros.isEmpty());

        // Uma consulta sem usuário válido também não deve retornar registros
        Map<String, RegistroDeDose> semUsuario =
                repository.findByPlannedDoseIdsAndUserId(
                        List.of("dose-001"), null
                );

        assertTrue(semUsuario.isEmpty());
    }

    @Test
    void testCountAll() {
        // Inicialmente, não existem registros
        assertEquals(0, repository.countAll());

        repository.save(criarRegistro(
                "registro-001", "dose-001", "user-001", DoseAction.TAKEN,
                "2026-11-18T08:05:00Z", "2026-11-18T08:10:00Z"
        ));

        repository.save(criarRegistro(
                "registro-002", "dose-002", "user-001", DoseAction.SKIPPED,
                null, "2026-11-18T09:00:00Z"
        ));

        // Confirma que existem dois documentos armazenados
        assertEquals(2, repository.countAll());
    }

    @Test
    void testIdGeradoAutomaticamente() {
        RegistroDeDose registro = criarRegistro(
                null,
                "dose-001",
                "user-001",
                DoseAction.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        // O modelo deve gerar um UUID quando o ID não for informado
        assertNotNull(registro.getId());
        assertFalse(registro.getId().isBlank());

        RegistroDeDose salvo = repository.save(registro);

        // O ID gerado deve ser preservado no MongoDB
        assertEquals(registro.getId(), salvo.getId());
    }

    @Test
    void testPeriodoInvalido() {
        Instant inicio = Instant.parse("2026-11-18T12:00:00Z");
        Instant fim = Instant.parse("2026-11-18T08:00:00Z");

        // O início do período não pode ser igual ou posterior ao fim
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByPeriod(inicio, fim));

        assertThrows(IllegalArgumentException.class,
                () -> repository.findByPeriod(inicio, inicio));
    }

    @Test
    void testSaveNulo() {
        // Não permite salvar um registro null
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(null));
    }

    @Test
    void testPlannedDoseIdInvalido() {
        // Não permite consultar utilizando um identificador inválido
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByPlannedDoseId(null));

        assertThrows(IllegalArgumentException.class,
                () -> repository.findByPlannedDoseId(""));
    }
}
