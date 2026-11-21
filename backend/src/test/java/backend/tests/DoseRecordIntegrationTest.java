
package backend.tests;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.repositories.MongoDoseRecordRepository;
import backend.repositories.MongoPlannedDoseRepository;
import backend.services.DoseAccessService;
import backend.services.DoseRecordConflictException;
import backend.services.DoseService;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class DoseRecordIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-11-21T12:00:00Z");

    private static final AuthContext USER_A =
            new AuthContext("user-a");

    private static final AuthContext USER_B =
            new AuthContext("user-b");

    private MongoClient client;
    private MongoDatabase database;
    private MongoPlannedDoseRepository planned;
    private MongoDoseRecordRepository records;
    private DoseService service;

    @BeforeEach
    void setup() {
        String uri = System.getenv("MONGODB_URI");

        assumeTrue(
                uri != null && !uri.isBlank(),
                "MONGODB_URI nao configurada"
        );

        client = MongoClients.create(uri);

        String databaseName = "dosecerta_test_32_"
                + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12);

        database = client.getDatabase(databaseName);

        planned = new MongoPlannedDoseRepository(database);
        records = new MongoDoseRecordRepository(database);

        planned.save(createDose(
                "dose-a",
                "user-a",
                ScheduleStatus.SCHEDULED
        ));

        DoseAccessService access =
                new DoseAccessService(planned, records);

        service = new DoseService(
                records,
                access,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @AfterEach
    void cleanup() {
        try {
            if (database != null
                    && database.getName()
                    .startsWith("dosecerta_test_32_")) {

                database.drop();
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private DosePrevista createDose(
            String id,
            String userId,
            ScheduleStatus status) {

        return new DosePrevista(
                id,
                userId,
                "treatment-" + userId,
                "medication-" + id,
                "Medicamento ficticio",
                1,
                NOW.minusSeconds(300),
                "America/Sao_Paulo",
                BigDecimal.ONE,
                status
        );
    }

    @Test
    void devePersistirTakenNoMongoDB() {
        RegistroDeDose record = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        assertNotNull(record.getId());
        assertEquals(DoseAction.TAKEN, record.getAction());
        assertEquals(NOW, record.getActualTime());
        assertEquals(NOW, record.getRecordedAt());

        Document persisted = database
                .getCollection("dose_records")
                .find(new Document(
                        "plannedDoseId", "dose-a"))
                .first();

        assertNotNull(persisted);
        assertEquals(
                record.getId(),
                persisted.getString("_id")
        );
        assertEquals(
                "user-a",
                persisted.getString("userId")
        );
        assertEquals(
                "TAKEN",
                persisted.getString("action")
        );

        assertEquals(1, records.countAll());
    }

    @Test
    void devePersistirSkippedSemActualTime() {
        RegistroDeDose record = service.recordDose(
                USER_A, "dose-a", DoseAction.SKIPPED);

        assertEquals(DoseAction.SKIPPED, record.getAction());
        assertNull(record.getActualTime());
        assertEquals(NOW, record.getRecordedAt());

        Document persisted = database
                .getCollection("dose_records")
                .find(new Document(
                        "plannedDoseId", "dose-a"))
                .first();

        assertNotNull(persisted);
        assertEquals("SKIPPED", persisted.getString("action"));
        assertNull(persisted.get("actualTime"));
        assertEquals(1, records.countAll());
    }

    @Test
    void deveRecuperarRegistroComNovaInstanciaDoRepositorio() {
        RegistroDeDose original = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        MongoDoseRecordRepository anotherRepository =
                new MongoDoseRecordRepository(database);

        RegistroDeDose restored = anotherRepository
                .findByPlannedDoseId("dose-a")
                .orElseThrow();

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getUserId(), restored.getUserId());
        assertEquals(original.getAction(), restored.getAction());
        assertEquals(original.getActualTime(), restored.getActualTime());
        assertEquals(original.getRecordedAt(), restored.getRecordedAt());
        assertEquals(1, anotherRepository.countAll());
    }

    @Test
    void retryAposPerdaDeRespostaNaoDeveDuplicar() {
        RegistroDeDose original = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        MongoDoseRecordRepository anotherRepository =
                new MongoDoseRecordRepository(database);

        DoseAccessService anotherAccess =
                new DoseAccessService(planned, anotherRepository);

        DoseService retryService = new DoseService(
                anotherRepository,
                anotherAccess,
                Clock.fixed(
                        NOW.plusSeconds(3600),
                        ZoneOffset.UTC
                )
        );

        RegistroDeDose retry = retryService.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        assertEquals(original.getId(), retry.getId());
        assertEquals(original.getActualTime(), retry.getActualTime());
        assertEquals(original.getRecordedAt(), retry.getRecordedAt());
        assertEquals(1, anotherRepository.countAll());
    }

    @Test
    void acaoContraditoriaNaoPodeSobrescreverRegistro() {
        RegistroDeDose original = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        try {
            service.recordDose(
                    USER_A, "dose-a", DoseAction.SKIPPED);

            fail("A segunda acao deveria gerar conflito.");
        } catch (DoseRecordConflictException expected) {
            assertEquals(1, records.countAll());
        }

        RegistroDeDose persisted =
                records.findByPlannedDoseId("dose-a")
                .orElseThrow();

        assertEquals(original.getId(), persisted.getId());
        assertEquals(DoseAction.TAKEN, persisted.getAction());
        assertEquals(original.getActualTime(), persisted.getActualTime());
        assertEquals(original.getRecordedAt(), persisted.getRecordedAt());
    }

    private Future<RegistroDeDose> submitRecord(
            ExecutorService executor,
            final CountDownLatch ready,
            final CountDownLatch start,
            final DoseAction action) {

        return executor.submit(new Callable<RegistroDeDose>() {
            @Override
            public RegistroDeDose call() throws Exception {
                ready.countDown();

                if (!start.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timeout aguardando inicio simultaneo");
                }

                return service.recordDose(
                        USER_A,
                        "dose-a",
                        action
                );
            }
        });
    }

    @Test
    void duasRequisicoesIguaisSimultaneasCriamUmRegistro()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<RegistroDeDose> first = submitRecord(
                    executor,
                    ready,
                    start,
                    DoseAction.TAKEN
            );

            Future<RegistroDeDose> second = submitRecord(
                    executor,
                    ready,
                    start,
                    DoseAction.TAKEN
            );

            assertTrue(
                    ready.await(15, TimeUnit.SECONDS),
                    "As threads nao ficaram prontas"
            );

            start.countDown();

            RegistroDeDose a =
                    first.get(30, TimeUnit.SECONDS);

            RegistroDeDose b =
                    second.get(30, TimeUnit.SECONDS);

            assertEquals(a.getId(), b.getId());
            assertEquals(a.getAction(), b.getAction());
            assertEquals(a.getActualTime(), b.getActualTime());
            assertEquals(a.getRecordedAt(), b.getRecordedAt());
            assertEquals(1, records.countAll());

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void duasAcoesContraditoriasSimultaneasNaoDuplicam()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<RegistroDeDose> taken = submitRecord(
                    executor,
                    ready,
                    start,
                    DoseAction.TAKEN
            );

            Future<RegistroDeDose> skipped = submitRecord(
                    executor,
                    ready,
                    start,
                    DoseAction.SKIPPED
            );

            assertTrue(
                    ready.await(15, TimeUnit.SECONDS),
                    "As threads nao ficaram prontas"
            );

            start.countDown();

            int successful = 0;
            int conflicts = 0;

            List<Future<RegistroDeDose>> futures =
                    List.of(taken, skipped);

            for (Future<RegistroDeDose> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successful++;

                } catch (ExecutionException error) {
                    assertInstanceOf(
                            DoseRecordConflictException.class,
                            error.getCause()
                    );
                    conflicts++;
                }
            }

            assertEquals(
                    1,
                    successful,
                    "Somente uma acao deve ser aceita"
            );
            assertEquals(
                    1,
                    conflicts,
                    "A outra acao deve gerar conflito"
            );
            assertEquals(1, records.countAll());

            RegistroDeDose persisted =
                    records.findByPlannedDoseId("dose-a")
                    .orElseThrow();

            assertTrue(
                    persisted.getAction() == DoseAction.TAKEN
                    || persisted.getAction() == DoseAction.SKIPPED
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void outroUsuarioNaoPodeRegistrarDoseDeA() {
        try {
            service.recordDose(
                    USER_B, "dose-a", DoseAction.TAKEN);

            fail("Outro usuario nao pode registrar a dose.");
        } catch (ResourceNotFoundException expected) {
            assertEquals(0, records.countAll());
        }
    }

    @Test
    void doseCanceladaNaoAceitaNovoRegistro() {
        var cancelled = planned.save(createDose(
                "cancelled",
                "user-a",
                ScheduleStatus.SCHEDULED
        ));
        planned.save(cancelled.cancel());

        try {
            service.recordDose(
                    USER_A, "cancelled", DoseAction.TAKEN);

            fail("Dose cancelada deveria gerar conflito.");
        } catch (DoseRecordConflictException expected) {
            assertEquals(0, records.countAll());
        }
    }

    @Test
    void ausenciaDeRegistroNaoCriaSkippedAutomaticamente() {
        RegistroDeDose record = records
                .findByPlannedDoseId("dose-a")
                .orElse(null);

        assertNull(record);
        assertEquals(0, records.countAll());

        DosePrevista dose =
                planned.findById("dose-a").orElseThrow();

        assertEquals(
                ScheduleStatus.SCHEDULED,
                dose.getStatus()
        );
    }

    @Test
    void devePreservarHorarioPrevistoEVersaoDaDose() {
        DosePrevista before =
                planned.findById("dose-a").orElseThrow();

        service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        DosePrevista after =
                planned.findById("dose-a").orElseThrow();

        assertEquals(
                before.getScheduledAt(),
                after.getScheduledAt()
        );
        assertEquals(
                before.getScheduleVersion(),
                after.getScheduleVersion()
        );
        assertEquals(
                before.getStatus(),
                after.getStatus()
        );
    }

    @Test
    void deveExistirIndiceUnicoEmPlannedDoseId() {
        boolean found = false;

        for (Document index : database
                .getCollection("dose_records")
                .listIndexes()) {

            Document key = index.get("key", Document.class);

            if (key == null || key.size() != 1) {
                continue;
            }

            Object value = key.get("plannedDoseId");

            if (value instanceof Number
                    && ((Number) value).intValue() == 1
                    && Boolean.TRUE.equals(index.get("unique"))) {

                found = true;
                break;
            }
        }

        assertTrue(
                found,
                "Indice unico plannedDoseId nao encontrado"
        );
    }
}
