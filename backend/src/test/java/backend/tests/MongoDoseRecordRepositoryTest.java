
package backend.tests;

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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class MongoDoseRecordRepositoryTest {

    private MongoClient client;
    private MongoDatabase database;
    private MongoDoseRecordRepository repository;

    @BeforeEach
    void preparar() {
        String uri = System.getenv("MONGODB_URI");

        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("MONGODB_URI não configurada");
        }

        client = MongoClients.create(uri);

        // Cria um banco temporário e independente para cada teste
        // O nome é limitado para respeitar o limite do MongoDB Atlas
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
            String action,
            String actualTime,
            String recordedAt) {

        return new RegistroDeDose(
                id,
                plannedDoseId,
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
                RegistroDeDose.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        RegistroDeDose salvo = repository.save(registro);

        // Confirma que o método retornou o registro corretamente
        assertEquals(registro.getId(), salvo.getId());
        assertEquals(RegistroDeDose.TAKEN, salvo.getAction());
        assertEquals(registro.getActualTime(), salvo.getActualTime());

        // Confirma que o documento foi persistido no MongoDB
        Document documento = database.getCollection("dose_records")
                .find(new Document("_id", "registro-001"))
                .first();

        assertNotNull(documento);
        assertEquals("dose-001", documento.getString("plannedDoseId"));
        assertNotNull(documento.getDate("actualTime"));
        assertNotNull(documento.getDate("recordedAt"));
    }

    @Test
    void testSaveSkipped() {
        RegistroDeDose registro = criarRegistro(
                "registro-002",
                "dose-002",
                RegistroDeDose.SKIPPED,
                null,
                "2026-11-18T09:00:00Z"
        );

        RegistroDeDose salvo = repository.save(registro);

        // SKIPPED não pode possuir horário de ingestão
        assertEquals(RegistroDeDose.SKIPPED, salvo.getAction());
        assertNull(salvo.getActualTime());

        // Confirma que o valor null foi preservado no banco
        Document documento = database.getCollection("dose_records")
                .find(new Document("_id", "registro-002"))
                .first();

        assertNotNull(documento);
        assertNull(documento.getDate("actualTime"));
    }

    @Test
    void testSaveNaoDuplicaRegistros() {
        RegistroDeDose primeiro = criarRegistro(
                "registro-001",
                "dose-001",
                RegistroDeDose.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        RegistroDeDose segundo = criarRegistro(
                "registro-999",
                "dose-001",
                RegistroDeDose.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:15:00Z"
        );

        repository.save(primeiro);
        RegistroDeDose salvo = repository.save(segundo);

        // A mesma dose prevista não deve gerar dois registros
        assertEquals("registro-001", salvo.getId());
        assertEquals(1L, database.getCollection("dose_records").countDocuments());
    }

    @Test
    void testSaveNaoPermiteAcoesContraditorias() {
        RegistroDeDose tomado = criarRegistro(
                "registro-001",
                "dose-001",
                RegistroDeDose.TAKEN,
                "2026-11-18T08:05:00Z",
                "2026-11-18T08:10:00Z"
        );

        RegistroDeDose ignorado = criarRegistro(
                "registro-002",
                "dose-001",
                RegistroDeDose.SKIPPED,
                null,
                "2026-11-18T08:20:00Z"
        );

        repository.save(tomado);

        // Não permite registrar SKIPPED para uma dose já registrada como TAKEN
        assertThrows(IllegalStateException.class, () -> repository.save(ignorado));

        // Confirma que a ação original foi preservada
        RegistroDeDose existente = repository.findByPlannedDoseId("dose-001").orElseThrow();

        assertEquals(RegistroDeDose.TAKEN, existente.getAction());
        assertEquals(1L, database.getCollection("dose_records").countDocuments());
    }

    @Test
    void testFindByPlannedDoseId() {
        RegistroDeDose registro = criarRegistro(
                "registro-001",
                "dose-001",
                RegistroDeDose.TAKEN,
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
    }

    @Test
    void testFindByPlannedDoseIdInexistente() {
        // Uma dose sem registro deve retornar Optional vazio
        Optional<RegistroDeDose> encontrado =
                repository.findByPlannedDoseId("dose-inexistente");

        assertTrue(encontrado.isEmpty());

        // A consulta não deve criar um registro SKIPPED automaticamente
        assertEquals(0L, database.getCollection("dose_records").countDocuments());
    }

    @Test
    void testFindByPeriod() {
        // Registro anterior ao período
        repository.save(criarRegistro(
                "registro-001", "dose-001", RegistroDeDose.TAKEN,
                "2026-11-18T07:50:00Z", "2026-11-18T07:59:59Z"
        ));

        // Registro exatamente no início do período
        repository.save(criarRegistro(
                "registro-002", "dose-002", RegistroDeDose.TAKEN,
                "2026-11-18T07:55:00Z", "2026-11-18T08:00:00Z"
        ));

        // Registro SKIPPED dentro do período
        repository.save(criarRegistro(
                "registro-003", "dose-003", RegistroDeDose.SKIPPED,
                null, "2026-11-18T10:30:00Z"
        ));

        // Registro exatamente no fim do período
        repository.save(criarRegistro(
                "registro-004", "dose-004", RegistroDeDose.TAKEN,
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