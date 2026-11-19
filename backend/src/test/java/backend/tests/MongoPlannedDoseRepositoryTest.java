
package backend.tests;

import backend.models.DosePrevista;
import backend.models.ScheduleStatus;
import backend.repositories.MongoPlannedDoseRepository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.bson.types.Decimal128;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class MongoPlannedDoseRepositoryTest {

    private MongoClient client;
    private MongoDatabase database;
    private MongoPlannedDoseRepository repository;

    @BeforeEach
    void preparar() {
        String uri = System.getenv("MONGODB_URI");

        // Executa os testes de integração somente quando o MongoDB estiver configurado
        assumeTrue(uri != null && !uri.isBlank(),
                "MONGODB_URI não configurada");

        client = MongoClients.create(uri);

        // Cria um banco temporário e exclusivo para cada teste
        String nomeBanco = "dosecerta_test_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        database = client.getDatabase(nomeBanco);
        repository = new MongoPlannedDoseRepository(database);
    }

    @AfterEach
    void limpar() {
        // Remove apenas o banco temporário criado para o teste
        try {
            if (database != null) {
                database.drop();
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    // Cria uma dose padrão para reutilizar nos testes
    private DosePrevista criarDose(
            String id,
            String userId,
            String treatmentId,
            String medicationInUseId,
            String horario) {

        return new DosePrevista(
                id,
                userId,
                treatmentId,
                medicationInUseId,
                "Losartana 50 mg",
                1,
                Instant.parse(horario),
                "America/Sao_Paulo",
                BigDecimal.ONE,
                ScheduleStatus.SCHEDULED
        );
    }

    @Test
    void testSave() {
        DosePrevista dose = criarDose(
                "dose-001", "user-001", "trat-001", "med-001",
                "2026-11-18T11:00:00Z"
        );

        DosePrevista salva = repository.save(dose);

        // Verifica o objeto retornado
        assertEquals(dose.getId(), salva.getId());
        assertEquals(dose.getScheduledAt(), salva.getScheduledAt());
        assertEquals(dose.getMedicationName(), salva.getMedicationName());
        assertEquals(dose.getPlannedQuantity(), salva.getPlannedQuantity());
        assertEquals(ScheduleStatus.SCHEDULED, salva.getStatus());

        // Confirma que o documento existe realmente no MongoDB
        Document documento = database.getCollection("planned_doses")
                .find(new Document("_id", dose.getId()))
                .first();

        assertNotNull(documento);
        assertEquals("user-001", documento.getString("userId"));
        assertEquals("Losartana 50 mg", documento.getString("medicationName"));
        assertEquals("SCHEDULED", documento.getString("status"));
        assertNotNull(documento.getDate("scheduledAt"));

        // Confirma que a quantidade foi armazenada como Decimal128
        Decimal128 quantidade = documento.get("plannedQuantity", Decimal128.class);
        assertEquals(BigDecimal.ONE, quantidade.bigDecimalValue());
    }

    @Test
    void testSaveNaoDuplicaDoses() {
        DosePrevista dose1 = criarDose(
                "dose-001", "user-001", "trat-001", "med-001",
                "2026-11-18T11:00:00Z"
        );

        // Mesmo medicamento, versão e horário, mas ID diferente
        DosePrevista dose2 = criarDose(
                "dose-002", "user-001", "trat-001", "med-001",
                "2026-11-18T11:00:00Z"
        );

        repository.save(dose1);
        DosePrevista resultado = repository.save(dose2);

        // Deve retornar a primeira ocorrência, sem criar outra
        assertEquals("dose-001", resultado.getId());

        long quantidade = database.getCollection("planned_doses")
                .countDocuments();

        assertEquals(1, quantidade);
    }

    @Test
    void testSaveAll() {
        DosePrevista dose1 = criarDose(
                "dose-001", "user-001", "trat-001", "med-001",
                "2026-11-18T11:00:00Z"
        );

        DosePrevista dose2 = criarDose(
                "dose-002", "user-001", "trat-001", "med-001",
                "2026-11-18T23:00:00Z"
        );

        List<DosePrevista> doses = List.of(dose1, dose2);
        List<DosePrevista> resultado = repository.saveAll(doses);

        // Verifica se as duas doses foram salvas
        assertEquals(2, resultado.size());

        long quantidade = database.getCollection("planned_doses")
                .countDocuments();

        assertEquals(2, quantidade);
    }

    @Test
    void testFindById() {
        DosePrevista dose = criarDose(
                "dose-001", "user-001", "trat-001", "med-001",
                "2026-11-18T11:00:00Z"
        );

        repository.save(dose);

        // Recupera a dose pelo ID
        var resultado = repository.findById("dose-001");

        assertTrue(resultado.isPresent());
        assertEquals("dose-001", resultado.get().getId());
        assertEquals("user-001", resultado.get().getUserId());
        assertEquals("Losartana 50 mg", resultado.get().getMedicationName());
        assertEquals(BigDecimal.ONE, resultado.get().getPlannedQuantity());
    }

    @Test
    void testFindByIdInexistente() {
        // Não existe nenhuma dose com esse ID
        var resultado = repository.findById("dose-inexistente");

        assertTrue(resultado.isEmpty());
    }

    @Test
    void testFindByUserAndDate() {
        // Duas doses do usuário em 18/11 no horário de São Paulo
        DosePrevista dose1 = criarDose(
                "dose-001", "user-001", "trat-001", "med-001",
                "2026-11-18T11:00:00Z"
        );

        DosePrevista dose2 = criarDose(
                "dose-002", "user-001", "trat-001", "med-001",
                "2026-11-19T02:00:00Z"
        );

        // Dose do dia seguinte em São Paulo
        DosePrevista dose3 = criarDose(
                "dose-003", "user-001", "trat-001", "med-001",
                "2026-11-19T03:00:00Z"
        );

        // Dose de outro usuário
        DosePrevista dose4 = criarDose(
                "dose-004", "user-002", "trat-002", "med-002",
                "2026-11-18T12:00:00Z"
        );

        repository.saveAll(List.of(dose1, dose2, dose3, dose4));

        LocalDate data = LocalDate.of(2026, 11, 18);

        List<DosePrevista> resultado =
                repository.findByUserAndDate("user-001", data);

        // Somente as duas doses do usuário no dia solicitado
        assertEquals(2, resultado.size());

        assertTrue(resultado.stream()
                .anyMatch(d -> d.getId().equals("dose-001")));

        assertTrue(resultado.stream()
                .anyMatch(d -> d.getId().equals("dose-002")));
    }

    @Test
    void testFindByTreatmentAndPeriod() {
        DosePrevista dose1 = criarDose(
                "dose-001", "user-001", "trat-001", "med-001",
                "2026-11-18T00:00:00Z"
        );

        DosePrevista dose2 = criarDose(
                "dose-002", "user-001", "trat-001", "med-002",
                "2026-11-18T12:00:00Z"
        );

        // Fora do período: exatamente no limite final
        DosePrevista dose3 = criarDose(
                "dose-003", "user-001", "trat-001", "med-001",
                "2026-11-19T00:00:00Z"
        );

        // Outro tratamento
        DosePrevista dose4 = criarDose(
                "dose-004", "user-001", "trat-002", "med-003",
                "2026-11-18T14:00:00Z"
        );

        repository.saveAll(List.of(dose1, dose2, dose3, dose4));

        Instant inicio = Instant.parse("2026-11-18T00:00:00Z");
        Instant fim = Instant.parse("2026-11-19T00:00:00Z");

        List<DosePrevista> resultado =
                repository.findByTreatmentAndPeriod("trat-001", inicio, fim);

        // Inclui o início, exclui o fim e ignora outro tratamento
        assertEquals(2, resultado.size());

        assertTrue(resultado.stream()
                .anyMatch(d -> d.getId().equals("dose-001")));

        assertTrue(resultado.stream()
                .anyMatch(d -> d.getId().equals("dose-002")));
    }

    @Test
    void testPeriodoInvalido() {
        Instant inicio = Instant.parse("2026-11-20T00:00:00Z");
        Instant fim = Instant.parse("2026-11-18T00:00:00Z");

        // O método deve rejeitar um intervalo invertido
        assertThrows(IllegalArgumentException.class, () ->
                repository.findByTreatmentAndPeriod("trat-001", inicio, fim)
        );
    }

    @Test
    void testQuantidadeFracionaria() {
        DosePrevista dose = new DosePrevista(
                "dose-fracionaria",
                "user-001",
                "trat-001",
                "med-001",
                "Losartana 50 mg",
                1,
                Instant.parse("2026-11-18T11:00:00Z"),
                "America/Sao_Paulo",
                new BigDecimal("0.5"),
                ScheduleStatus.SCHEDULED
        );

        repository.save(dose);

        // Verifica se a quantidade fracionária foi preservada
        DosePrevista recuperada = repository.findById("dose-fracionaria")
                .orElseThrow();

        assertEquals(new BigDecimal("0.5"), recuperada.getPlannedQuantity());
    }

    @Test
    void testCancelamentoAtualizaStatus() {
        DosePrevista dose = criarDose(
                "dose-001", "user-001", "trat-001", "med-001",
                "2026-11-18T11:00:00Z"
        );

        repository.save(dose);

        // Cancela a dose e salva novamente
        DosePrevista cancelada = dose.cancel();
        repository.save(cancelada);

        // Verifica se o status foi atualizado no MongoDB
        DosePrevista recuperada = repository.findById("dose-001")
                .orElseThrow();

        assertEquals(ScheduleStatus.CANCELLED, recuperada.getStatus());

        // O cancelamento não deve criar uma segunda ocorrência
        assertEquals(1, repository.countAll());

        // O objeto original continua inalterado
        assertEquals(ScheduleStatus.SCHEDULED, dose.getStatus());
    }

    @Test
    void testFindByUserAndPeriod() {
        DosePrevista dose1 = criarDose(
                "dose-001", "user-001", "trat-001", "med-001",
                "2026-11-18T10:00:00Z"
        );

        // Exatamente no limite final: não deve ser incluída
        DosePrevista dose2 = criarDose(
                "dose-002", "user-001", "trat-001", "med-001",
                "2026-11-19T00:00:00Z"
        );

        // Dentro do período, mas de outro usuário
        DosePrevista dose3 = criarDose(
                "dose-003", "user-002", "trat-002", "med-002",
                "2026-11-18T12:00:00Z"
        );

        repository.saveAll(List.of(dose1, dose2, dose3));

        Instant inicio = Instant.parse("2026-11-18T00:00:00Z");
        Instant fim = Instant.parse("2026-11-19T00:00:00Z");

        List<DosePrevista> resultado =
                repository.findByUserAndPeriod("user-001", inicio, fim);

        // Deve retornar somente a primeira dose
        assertEquals(1, resultado.size());
        assertEquals("dose-001", resultado.get(0).getId());
    }
}
