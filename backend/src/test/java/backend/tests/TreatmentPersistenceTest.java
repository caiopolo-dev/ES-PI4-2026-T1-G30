//Autor: Cauã Bianchi Ferroni
package backend.tests;

import backend.config.MongoConnection;
import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.models.ScheduleFrequency;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.MongoTreatmentRepository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Testes de integração da persistência de tratamentos, medicamentos em uso,
 * ciclo de vida (DRAFT -> ACTIVE -> CLOSED) e versionamento de cronogramas no MongoDB.
 */
public class TreatmentPersistenceTest {

    private String mongoUri;
    private String databaseName;
    private MongoConnection connection;
    private MongoTreatmentRepository repository;

    @BeforeEach
    void preparar() {
        mongoUri = resolveMongoUri();

        // Executa os testes de integração somente quando o MongoDB estiver configurado
        assumeTrue(mongoUri != null && !mongoUri.isBlank(),
                "MONGODB_URI não configurada para testes de integração com MongoDB");

        // Nome curto para respeitar o limite do MongoDB Atlas
        databaseName = "dosecerta_test_t_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        connection = new MongoConnection(mongoUri, databaseName);
        repository = new MongoTreatmentRepository(connection.getDatabase());
    }

    @AfterEach
    void limpar() {
        if (connection != null) {
            try {
                // Remove apenas o banco temporário criado para este teste
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

    @Test
    void deveConectarNoMongoDBECriarIndiceDeUserId() {
        Document ping = connection.getDatabase().runCommand(new Document("ping", 1));
        assertEquals(1.0, ((Number) ping.get("ok")).doubleValue(), 0.001);

        assertEquals(databaseName, repository.getDatabaseName());
        assertEquals(MongoTreatmentRepository.DEFAULT_COLLECTION, repository.getCollectionName());

        MongoCollection<Document> collection = connection.getCollection(MongoTreatmentRepository.DEFAULT_COLLECTION);
        List<Document> indexes = new ArrayList<>();
        collection.listIndexes().into(indexes);

        boolean possuiIndiceUserId = indexes.stream().anyMatch(index -> {
            Document key = index.get("key", Document.class);
            return key != null && Integer.valueOf(1).equals(key.getInteger("userId"));
        });

        assertTrue(possuiIndiceUserId, "Collection treatments deve possuir índice em userId");
    }

    /**
     * Valida o cenário mínimo (Tratamento A -> Medicamento X -> 08:00 / 20:00)
     * comprovando a gravação direta na collection MongoDB e a recuperação por uma nova conexão.
     */
    @Test
    void devePersistirERecuperarCenarioMinimoNoMongoDB() {
        CronogramaDose scheduleV1 = new CronogramaDose(
                1,
                Arrays.asList("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                Arrays.asList("08:00", "20:00"),
                "2026-10-29",
                null,
                "America/Sao_Paulo",
                true
        );

        MedicamentoEmUso medicamentoX = new MedicamentoEmUso(
                "med-x",
                "Medicamento X",
                "1 unidade",
                1,
                "Tomar após as refeições",
                scheduleV1
        );

        Treatment tratamentoA = new Treatment(
                "trat-a",
                "usuario-01",
                "Tratamento A",
                "Tratamento inicial de uso contínuo",
                LocalDate.of(2026, 10, 29),
                null,
                TreatmentStatus.ACTIVE,
                null,
                Collections.singletonList(medicamentoX)
        );

        repository.save(tratamentoA);

        // Confirma a persistência diretamente na collection treatments do MongoDB
        MongoCollection<Document> collection = connection.getCollection(MongoTreatmentRepository.DEFAULT_COLLECTION);
        Document rawDoc = collection.find(Filters.eq("_id", "trat-a")).first();
        assertNotNull(rawDoc, "Documento deve existir fisicamente na collection treatments");
        assertEquals("trat-a", rawDoc.getString("_id"));
        assertEquals("usuario-01", rawDoc.getString("userId"));
        assertEquals("Tratamento A", rawDoc.getString("name"));
        assertEquals("2026-10-29", rawDoc.getString("startDate"));
        assertNull(rawDoc.get("endDate"));
        assertEquals("ACTIVE", rawDoc.getString("status"));
        assertNull(rawDoc.get("closedAt"));

        // Abre uma segunda conexão independente para comprovar que os dados vêm do banco e não de memória
        try (MongoConnection novaConexao = new MongoConnection(mongoUri, databaseName)) {
            MongoTreatmentRepository novoRepositorio = new MongoTreatmentRepository(novaConexao.getDatabase());
            Optional<Treatment> recoveredOpt = novoRepositorio.findById("trat-a");
            assertTrue(recoveredOpt.isPresent(), "Tratamento A deve ser recuperado por outra conexão MongoDB");

            Treatment recovered = recoveredOpt.get();
            assertEquals("trat-a", recovered.getId());
            assertEquals("usuario-01", recovered.getUserId());
            assertEquals("Tratamento A", recovered.getName());
            assertEquals("Tratamento inicial de uso contínuo", recovered.getDescription());
            assertEquals(LocalDate.of(2026, 10, 29), recovered.getStartDate());
            assertNull(recovered.getEndDate());
            assertEquals(TreatmentStatus.ACTIVE, recovered.getStatus());
            assertNull(recovered.getClosedAt());

            assertEquals(1, recovered.getMedications().size());
            MedicamentoEmUso recoveredMed = recovered.getMedications().get(0);
            assertEquals("med-x", recoveredMed.getId());
            assertEquals("Medicamento X", recoveredMed.getName());
            assertEquals("1 unidade", recoveredMed.getDose());
            assertEquals(Integer.valueOf(1), recoveredMed.getQuantity());
            assertEquals("Tomar após as refeições", recoveredMed.getObservations());

            assertEquals(1, recoveredMed.getSchedules().size());
            CronogramaDose recoveredSchedule = recoveredMed.getActiveSchedule().orElseThrow();
            assertEquals(1, recoveredSchedule.getVersion());
            assertEquals(Arrays.asList("08:00", "20:00"), recoveredSchedule.getTimes());
            assertEquals("2026-10-29", recoveredSchedule.getStartDate());
            assertEquals("America/Sao_Paulo", recoveredSchedule.getTimezone());
            assertTrue(recoveredSchedule.isActive());
        }
    }

    /**
     * Valida a persistência completa do ciclo de vida (DRAFT incompleto -> ACTIVE -> CLOSED)
     * e a imutabilidade de tratamentos encerrados no MongoDB.
     */
    @Test
    void devePersistirRascunhoIncompletoECicloDeVidaCompletoNoMongoDB() {
        Treatment rascunho = new Treatment("usuario-ciclo", null, null, null, null);
        Treatment salvo = repository.save(rascunho);

        MongoCollection<Document> collection = connection.getCollection(MongoTreatmentRepository.DEFAULT_COLLECTION);
        Document draftDoc = collection.find(Filters.eq("_id", salvo.getId())).first();
        assertNotNull(draftDoc);
        assertEquals("DRAFT", draftDoc.getString("status"));
        assertNull(draftDoc.getString("name"));
        assertNull(draftDoc.get("startDate"));

        // Atualiza metadados, período e adiciona medicamento com cronograma válido
        Treatment paraAtivar = repository.findById(salvo.getId()).orElseThrow();
        paraAtivar.setName("Tratamento Hipertensão");
        paraAtivar.setDescription("Uso diário");
        paraAtivar.updatePeriod(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 12, 19));

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00", "20:00"),
                "2026-11-19",
                "America/Sao_Paulo"
        );
        paraAtivar.addMedication(new MedicamentoEmUso("med-1", "Losartana", "50 mg", 1, null, cronograma));
        paraAtivar.activate();

        repository.update(paraAtivar);

        Document activeDoc = collection.find(Filters.eq("_id", salvo.getId())).first();
        assertNotNull(activeDoc);
        assertEquals("ACTIVE", activeDoc.getString("status"));
        assertEquals("Tratamento Hipertensão", activeDoc.getString("name"));
        assertEquals("2026-11-19", activeDoc.getString("startDate"));
        assertEquals("2026-12-19", activeDoc.getString("endDate"));

        // Encerra o tratamento e confirma persistência de closedAt como Date BSON
        Instant closedAt = Instant.parse("2026-11-20T15:30:00Z");
        Treatment paraEncerrar = repository.findById(salvo.getId()).orElseThrow();
        paraEncerrar.close(closedAt);
        repository.update(paraEncerrar);

        Document closedDoc = collection.find(Filters.eq("_id", salvo.getId())).first();
        assertNotNull(closedDoc);
        assertEquals("CLOSED", closedDoc.getString("status"));
        assertEquals(Date.from(closedAt), closedDoc.getDate("closedAt"));

        Treatment encerradoRecuperado = repository.findById(salvo.getId()).orElseThrow();
        assertEquals(TreatmentStatus.CLOSED, encerradoRecuperado.getStatus());
        assertEquals(closedAt, encerradoRecuperado.getClosedAt());
        assertEquals(1, encerradoRecuperado.getMedications().size());

        // Não permite atualizar um tratamento já encerrado no banco
        Treatment tentativaAlteracao = new Treatment(
                salvo.getId(),
                "usuario-ciclo",
                "Tentativa de reabrir",
                "Uso diário",
                LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 12, 19),
                TreatmentStatus.ACTIVE,
                null,
                encerradoRecuperado.getMedications()
        );
        assertThrows(IllegalStateException.class, () -> repository.update(tentativaAlteracao));
        assertEquals(TreatmentStatus.CLOSED, repository.findById(salvo.getId()).orElseThrow().getStatus());
    }

    /**
     * Valida a evolução de versões de cronograma no MongoDB:
     * Schedule v1 (08:00 / 20:00) -> Schedule v2 (08:00 / 21:00) -> Schedule v3.
     */
    @Test
    void devePreservarHistoricoDeVersoesDeCronogramaNoMongoDB() {
        CronogramaDose v1 = new CronogramaDose(
                Arrays.asList("08:00", "20:00"),
                "2026-10-01",
                "America/Sao_Paulo"
        );
        MedicamentoEmUso med = new MedicamentoEmUso("med-1", "Losartana 50mg", "1 comprimido", v1);
        Treatment treatment = new Treatment(
                "trat-hist",
                "user-10",
                "Controle Pressão",
                null,
                LocalDate.of(2026, 10, 1),
                null,
                TreatmentStatus.ACTIVE,
                null,
                Collections.singletonList(med)
        );

        repository.save(treatment);

        // Alteração 1: mudar horário de 08:00 / 20:00 para 08:00 / 21:00 (gera Schedule v2)
        Treatment toUpdate = repository.findById("trat-hist").orElseThrow();
        MedicamentoEmUso medToUpdate = toUpdate.getMedications().get(0);
        medToUpdate.updateScheduleTimes(Arrays.asList("08:00", "21:00"), "2026-10-15");
        toUpdate.addMedication(medToUpdate);
        repository.update(toUpdate);

        Treatment afterV2 = repository.findById("trat-hist").orElseThrow();
        MedicamentoEmUso medAfterV2 = afterV2.getMedications().get(0);
        assertEquals(2, medAfterV2.getSchedules().size());

        CronogramaDose storedV1 = medAfterV2.getScheduleByVersion(1).orElseThrow();
        CronogramaDose storedV2 = medAfterV2.getScheduleByVersion(2).orElseThrow();
        assertFalse(storedV1.isActive());
        assertEquals(Arrays.asList("08:00", "20:00"), storedV1.getTimes());
        assertTrue(storedV2.isActive());
        assertEquals(Arrays.asList("08:00", "21:00"), storedV2.getTimes());

        // Alteração 2: mudar frequência (daysOfWeek), quantidade e vigência (gera Schedule v3)
        medAfterV2.setQuantity(2);
        medAfterV2.updateSchedule(
                Arrays.asList("MONDAY", "WEDNESDAY", "FRIDAY"),
                Collections.singletonList("09:00"),
                "2026-11-01",
                "2026-12-31",
                "America/Sao_Paulo"
        );
        afterV2.addMedication(medAfterV2);
        repository.update(afterV2);

        Treatment afterV3 = repository.findById("trat-hist").orElseThrow();
        MedicamentoEmUso medAfterV3 = afterV3.getMedications().get(0);
        assertEquals(3, medAfterV3.getSchedules().size());
        assertEquals(Integer.valueOf(2), medAfterV3.getQuantity());
        assertFalse(medAfterV3.getScheduleByVersion(1).orElseThrow().isActive());
        assertFalse(medAfterV3.getScheduleByVersion(2).orElseThrow().isActive());

        CronogramaDose storedV3 = medAfterV3.getActiveSchedule().orElseThrow();
        assertEquals(3, storedV3.getVersion());
        assertEquals(Arrays.asList("MONDAY", "WEDNESDAY", "FRIDAY"), storedV3.getDaysOfWeek());
        assertEquals("2026-12-31", storedV3.getEndDate());

        // Confirma que as 3 versões estão fisicamente salvas no documento BSON do MongoDB
        Document rawDoc = connection.getCollection(MongoTreatmentRepository.DEFAULT_COLLECTION)
                .find(Filters.eq("_id", "trat-hist"))
                .first();
        assertNotNull(rawDoc);
        List<Document> rawMeds = rawDoc.getList("medications", Document.class);
        assertEquals(1, rawMeds.size());
        List<Document> rawSchedules = rawMeds.get(0).getList("schedules", Document.class);
        assertEquals(3, rawSchedules.size());
    }

    /**
     * Garante que mesmo quando o chamador instancia um novo objeto Treatment/MedicamentoEmUso
     * contendo apenas o novo cronograma, o MongoTreatmentRepository.update() preserva as versões
     * anteriores já salvas no banco.
     */
    @Test
    void devePreservarHistoricoAutomaticamenteNoUpdateMesmoEnviandoApenasNovoCronograma() {
        CronogramaDose initialSchedule = new CronogramaDose(
                Arrays.asList("08:00", "20:00"),
                "2026-10-01",
                "America/Sao_Paulo"
        );
        MedicamentoEmUso initialMed = new MedicamentoEmUso("med-auto", "Vitamina D", "1 cápsula", initialSchedule);
        Treatment initialTreatment = new Treatment(
                "trat-auto",
                "user-99",
                "Suplementação",
                null,
                LocalDate.of(2026, 10, 1),
                null,
                TreatmentStatus.ACTIVE,
                null,
                Collections.singletonList(initialMed)
        );
        repository.save(initialTreatment);

        // Chamador envia objeto atualizado apenas com o novo horário (08:00 / 21:00)
        CronogramaDose modifiedSchedule = new CronogramaDose(
                Arrays.asList("08:00", "21:00"),
                "2026-10-20",
                "America/Sao_Paulo"
        );
        MedicamentoEmUso updatedMed = new MedicamentoEmUso("med-auto", "Vitamina D", "1 cápsula", modifiedSchedule);
        Treatment updatedTreatment = new Treatment(
                "trat-auto",
                "user-99",
                "Suplementação",
                null,
                LocalDate.of(2026, 10, 1),
                null,
                TreatmentStatus.ACTIVE,
                null,
                Collections.singletonList(updatedMed)
        );
        repository.update(updatedTreatment);

        Treatment persisted = repository.findById("trat-auto").orElseThrow();
        List<CronogramaDose> schedules = persisted.getMedications().get(0).getSchedules();
        assertEquals(2, schedules.size());
        assertEquals(1, schedules.get(0).getVersion());
        assertFalse(schedules.get(0).isActive());
        assertEquals(Arrays.asList("08:00", "20:00"), schedules.get(0).getTimes());
        assertEquals(2, schedules.get(1).getVersion());
        assertTrue(schedules.get(1).isActive());
        assertEquals(Arrays.asList("08:00", "21:00"), schedules.get(1).getTimes());
    }

    /**
     * Valida a associação ao usuário titular, consultas filtradas por findByUserId()
     * e findByIdAndUserId(), e bloqueio de alteração de titular no MongoDB.
     */
    @Test
    void deveFiltrarPorUsuarioEImpedirTrocaDeTitularNoMongoDB() {
        Treatment t1UserA = new Treatment("t1", "user-A", "Tratamento 1 do Usuário A", null,
                null, null, TreatmentStatus.DRAFT, null, Collections.emptyList());
        Treatment t2UserA = new Treatment("t2", "user-A", "Tratamento 2 do Usuário A", null,
                null, null, TreatmentStatus.DRAFT, null, Collections.emptyList());
        Treatment t1UserB = new Treatment("t3", "user-B", "Tratamento 1 do Usuário B", null,
                null, null, TreatmentStatus.DRAFT, null, Collections.emptyList());

        repository.save(t1UserA);
        repository.save(t2UserA);
        repository.save(t1UserB);

        List<Treatment> userATreatments = repository.findByUserId("user-A");
        assertEquals(2, userATreatments.size());
        assertTrue(userATreatments.stream().anyMatch(t -> "t1".equals(t.getId())));
        assertTrue(userATreatments.stream().anyMatch(t -> "t2".equals(t.getId())));

        List<Treatment> userBTreatments = repository.findByUserId("user-B");
        assertEquals(1, userBTreatments.size());
        assertEquals("t3", userBTreatments.get(0).getId());

        assertTrue(repository.findByUserId("user-inexistente").isEmpty());
        assertTrue(repository.findByIdAndUserId("t1", "user-A").isPresent());
        assertTrue(repository.findByIdAndUserId("t1", "user-B").isEmpty());

        // Impedir troca de titular (userId) na atualização
        Treatment wrongOwnerUpdate = new Treatment("t1", "user-B", "Tentativa de troca de titular", null,
                null, null, TreatmentStatus.DRAFT, null, Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> repository.update(wrongOwnerUpdate));

        // Confirma no banco que o titular original não foi alterado
        assertEquals("user-A", repository.findById("t1").orElseThrow().getUserId());
    }

    /**
     * Verifica o mapeamento de documento MongoDB, o isolamento de mutações em memória,
     * a rejeição de IDs duplicados no save() e tratamento inexistente no update().
     */
    @Test
    void deveIsolarMutacoesEmMemoriaERejeitarIdDuplicadoOuInexistente() {
        CronogramaDose schedule = new CronogramaDose(
                Arrays.asList("07:00", "19:00"),
                "2026-10-29",
                "America/Sao_Paulo"
        );
        MedicamentoEmUso med = new MedicamentoEmUso("med-iso", "Amoxicilina", "500 mg", 21, "De 12 em 12h", schedule);
        Treatment treatment = new Treatment(
                "trat-iso",
                "user-iso",
                "Infecção",
                null,
                LocalDate.of(2026, 10, 29),
                null,
                TreatmentStatus.ACTIVE,
                null,
                Collections.singletonList(med)
        );

        repository.save(treatment);

        // Alterar o objeto em memória não deve alterar o documento salvo no MongoDB sem chamar update()
        treatment.setName("Nome Modificado Apenas Em Memória");
        med.setDose("Dose Modificada Apenas Em Memória");

        Treatment fromDb = repository.findById("trat-iso").orElseThrow();
        assertEquals("Infecção", fromDb.getName());
        assertEquals("500 mg", fromDb.getMedications().get(0).getDose());

        // ID duplicado deve ser rejeitado pelo MongoDB
        assertThrows(IllegalArgumentException.class, () -> repository.save(fromDb));
        assertEquals(1L, connection.getCollection(MongoTreatmentRepository.DEFAULT_COLLECTION).countDocuments());

        // Atualização de tratamento inexistente deve lançar NoSuchElementException
        Treatment inexistente = new Treatment(
                "trat-inexistente",
                "user-iso",
                "Não salvo",
                null,
                null,
                null,
                TreatmentStatus.DRAFT,
                null,
                Collections.emptyList()
        );
        assertThrows(NoSuchElementException.class, () -> repository.update(inexistente));

        Document rawDoc = repository.toDocument(fromDb);
        assertEquals("trat-iso", rawDoc.getString("_id"));
        assertEquals("user-iso", rawDoc.getString("userId"));
        assertEquals("ACTIVE", rawDoc.getString("status"));

        String json = repository.toJsonDocument(fromDb);
        assertTrue(json.contains("\"_id\": \"trat-iso\"") || json.contains("\"_id\":\"trat-iso\""));
        assertTrue(json.contains("\"status\": \"ACTIVE\"") || json.contains("\"status\":\"ACTIVE\""));
        assertTrue(json.contains("\"schedules\""));
    }

    @Test
    void devePersistirCronogramaVersionadoERecuperarEmNovoRepositorio() {
        MedicamentoEmUso medication = new MedicamentoEmUso(
                "med-schedule", "Medicamento", "1 comprimido", 1,
                "comprimido", null, true, List.of(), 0L);
        Treatment treatment = new Treatment(
                "trat-schedule", "user-schedule", "Tratamento", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
                TreatmentStatus.DRAFT, null, List.of(medication));
        repository.save(treatment);

        Instant firstStart = Instant.parse("2026-11-10T12:00:00.123456Z");
        CronogramaDose first = new CronogramaDose(
                1, ScheduleFrequency.EVERY_DAY, null, List.of("08:00"),
                "2026-11-01", "2026-12-31", "America/Sao_Paulo", true,
                firstStart, null);

        assertTrue(repository.updateMedicationSchedules(
                "trat-schedule", "user-schedule", "med-schedule", 0L, List.of(first)));
        assertFalse(repository.updateMedicationSchedules(
                "trat-schedule", "user-schedule", "med-schedule", 0L, List.of(first)));

        MongoTreatmentRepository reopened = new MongoTreatmentRepository(connection.getDatabase());
        MedicamentoEmUso recovered = reopened
                .findByIdAndUserId("trat-schedule", "user-schedule")
                .orElseThrow().findMedicationById("med-schedule").orElseThrow();

        assertEquals(1L, recovered.getRevision());
        assertEquals(1, recovered.getSchedules().size());
        assertEquals(first, recovered.getSchedules().get(0));
        assertEquals(firstStart, recovered.getSchedules().get(0).getValidFrom());
        assertEquals(ScheduleFrequency.EVERY_DAY, recovered.getSchedules().get(0).getFrequency());
    }

    @Test
    void updateGeralNaoDeveAceitarMedicamentoAnteriorAoCronogramaPersistido() {
        MedicamentoEmUso medication = new MedicamentoEmUso(
                "med-concurrency", "Medicamento", "1 comprimido", 1,
                "comprimido", null, true, List.of(), 0L);
        Treatment treatment = new Treatment(
                "trat-concurrency", "user-concurrency", "Tratamento", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
                TreatmentStatus.DRAFT, null, List.of(medication));
        Treatment stale = repository.save(treatment);

        CronogramaDose schedule = new CronogramaDose(
                1, ScheduleFrequency.EVERY_DAY, null, List.of("08:00"),
                "2026-11-01", "2026-12-31", "America/Sao_Paulo", true,
                Instant.parse("2026-11-10T12:00:00Z"), null);
        assertTrue(repository.updateMedicationSchedules(
                "trat-concurrency", "user-concurrency", "med-concurrency", 0L,
                List.of(schedule)));

        assertThrows(IllegalStateException.class, () -> repository.update(stale));
        MedicamentoEmUso recovered = repository.findById("trat-concurrency")
                .orElseThrow().findMedicationById("med-concurrency").orElseThrow();
        assertEquals(1L, recovered.getRevision());
        assertEquals(List.of(schedule), recovered.getSchedules());

        recovered.updateScheduleTimes(List.of("09:00"), "2026-11-20");
        Treatment invalidGeneralUpdate = new Treatment(
                "trat-concurrency", "user-concurrency", "Tratamento alterado", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
                TreatmentStatus.DRAFT, null, List.of(recovered));
        assertThrows(IllegalStateException.class,
                () -> repository.update(invalidGeneralUpdate));

        MedicamentoEmUso unchanged = repository.findById("trat-concurrency")
                .orElseThrow().findMedicationById("med-concurrency").orElseThrow();
        assertEquals(1, unchanged.getSchedules().size());
        assertEquals(List.of(schedule), unchanged.getSchedules());
    }


    @Test
    void devePersistirSnapshotDaPosologia() {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00"),
                "2026-11-21",
                "America/Sao_Paulo"
        ).withPosologySnapshot(2, "comprimido");

        MedicamentoEmUso medicamento = new MedicamentoEmUso(
                "med-1",
                "Medicamento de teste",
                "50 mg",
                2,
                "comprimido",
                null,
                true,
                List.of(cronograma)
        );

        Treatment tratamento = new Treatment(
                "user-1",
                "Tratamento de teste",
                null,
                LocalDate.of(2026, 11, 21),
                null
        );

        tratamento.addMedication(medicamento);

        repository.save(tratamento);

        // Recupera o tratamento persistido no MongoDB.
        Treatment recuperado = repository.findById(tratamento.getId())
                .orElseThrow();

        CronogramaDose cronogramaRecuperado = recuperado
                .findMedicationById("med-1").orElseThrow()
                .getActiveSchedule().orElseThrow();

        assertTrue(cronogramaRecuperado.hasPosologySnapshot());
        assertEquals(Integer.valueOf(2), cronogramaRecuperado.getQuantitySnapshot());
        assertEquals("comprimido", cronogramaRecuperado.getUnitSnapshot());
    }

}
