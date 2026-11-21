
package backend.tests;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.repositories.DoseRecordRepository;
import backend.repositories.PlannedDoseRepository;
import backend.services.DoseAccessService;
import backend.services.DoseRecordConflictException;
import backend.services.DoseService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DoseServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-11-21T12:00:00Z");

    private static final AuthContext USER_A =
            new AuthContext("user-a");

    private static final AuthContext USER_B =
            new AuthContext("user-b");

    private MemoryPlannedRepository planned;
    private MemoryRecordRepository records;
    private DoseService service;

    @BeforeEach
    void setup() {
        planned = new MemoryPlannedRepository();
        records = new MemoryRecordRepository();

        planned.save(createDose(
                "dose-a", "user-a", ScheduleStatus.SCHEDULED));

        planned.save(createDose(
                "dose-b", "user-b", ScheduleStatus.SCHEDULED));

        planned.save(createDose(
                "cancelled", "user-a", ScheduleStatus.CANCELLED));

        DoseAccessService access =
                new DoseAccessService(planned, records);

        service = new DoseService(
                records,
                access,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
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
    void deveRegistrarTakenComHorarioDoServidor() {
        RegistroDeDose result = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        assertEquals("dose-a", result.getPlannedDoseId());
        assertEquals("user-a", result.getUserId());
        assertEquals(DoseAction.TAKEN, result.getAction());
        assertEquals(NOW, result.getActualTime());
        assertEquals(NOW, result.getRecordedAt());
        assertNotNull(result.getId());
        assertEquals(1, records.countAll());
    }

    @Test
    void deveRegistrarSkippedSemHorarioDeIngestao() {
        RegistroDeDose result = service.recordDose(
                USER_A, "dose-a", DoseAction.SKIPPED);

        assertEquals(DoseAction.SKIPPED, result.getAction());
        assertNull(result.getActualTime());
        assertEquals(NOW, result.getRecordedAt());
        assertEquals(1, records.countAll());
    }

    @Test
    void retryIdenticoDevePreservarIdEHorarios() {
        RegistroDeDose original = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        DoseAccessService access =
                new DoseAccessService(planned, records);

        DoseService laterService = new DoseService(
                records,
                access,
                Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC)
        );

        RegistroDeDose retry = laterService.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        assertEquals(original.getId(), retry.getId());
        assertEquals(original.getActualTime(), retry.getActualTime());
        assertEquals(original.getRecordedAt(), retry.getRecordedAt());
        assertEquals(1, records.countAll());
    }

    @Test
    void takenAposSkippedDeveGerarConflito() {
        RegistroDeDose original = service.recordDose(
                USER_A, "dose-a", DoseAction.SKIPPED);

        try {
            service.recordDose(
                    USER_A, "dose-a", DoseAction.TAKEN);

            fail("Era esperado conflito.");
        } catch (DoseRecordConflictException expected) {
            assertEquals(1, records.countAll());
        }

        RegistroDeDose saved =
                records.findByPlannedDoseId("dose-a").orElseThrow();

        assertEquals(original.getId(), saved.getId());
        assertEquals(DoseAction.SKIPPED, saved.getAction());
        assertNull(saved.getActualTime());
    }

    @Test
    void skippedAposTakenDeveGerarConflito() {
        RegistroDeDose original = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        try {
            service.recordDose(
                    USER_A, "dose-a", DoseAction.SKIPPED);

            fail("Era esperado conflito.");
        } catch (DoseRecordConflictException expected) {
            assertEquals(1, records.countAll());
        }

        RegistroDeDose saved =
                records.findByPlannedDoseId("dose-a").orElseThrow();

        assertEquals(original.getId(), saved.getId());
        assertEquals(DoseAction.TAKEN, saved.getAction());
    }

    @Test
    void doseCanceladaNaoAceitaNovoRegistro() {
        try {
            service.recordDose(
                    USER_A, "cancelled", DoseAction.TAKEN);

            fail("Dose cancelada deveria retornar conflito.");
        } catch (DoseRecordConflictException expected) {
            assertEquals(0, records.countAll());
        }
    }

    @Test
    void retryAposCancelamentoPreservaRegistroAnterior() {
        RegistroDeDose original = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        planned.save(createDose(
                "dose-a", "user-a", ScheduleStatus.CANCELLED));

        RegistroDeDose retry = service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        assertEquals(original.getId(), retry.getId());
        assertEquals(original.getRecordedAt(), retry.getRecordedAt());
        assertEquals(1, records.countAll());
    }

    @Test
    void usuarioNaoPodeRegistrarDoseDeOutroTitular() {
        try {
            service.recordDose(
                    USER_B, "dose-a", DoseAction.TAKEN);

            fail("Acesso indevido deveria ser negado.");
        } catch (ResourceNotFoundException expected) {
            assertEquals(0, records.countAll());
        }
    }

    @Test
    void doseInexistenteDeveSerRejeitada() {
        try {
            service.recordDose(
                    USER_A, "missing", DoseAction.TAKEN);

            fail("Dose inexistente deveria ser rejeitada.");
        } catch (ResourceNotFoundException expected) {
            assertEquals(0, records.countAll());
        }
    }

    @Test
    void acaoNulaDeveSerRejeitada() {
        try {
            service.recordDose(USER_A, "dose-a", null);

            fail("Acao nula deveria ser rejeitada.");
        } catch (IllegalArgumentException expected) {
            assertEquals(0, records.countAll());
        }
    }

    @Test
    void falhaInesperadaNaoPodeVirarSucesso() {
        MemoryRecordRepository failingRecords =
                new MemoryRecordRepository() {
                    @Override
                    public synchronized RegistroDeDose save(
                            RegistroDeDose record) {

                        super.save(record);

                        throw new IllegalStateException(
                                "Falha simulada apos gravacao");
                    }
                };

        DoseAccessService access =
                new DoseAccessService(planned, failingRecords);

        DoseService failingService = new DoseService(
                failingRecords,
                access,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        try {
            failingService.recordDose(
                    USER_A, "dose-a", DoseAction.TAKEN);

            fail("A falha nao deveria ser ocultada.");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "Falha simulada apos gravacao",
                    expected.getMessage()
            );
        }

        assertEquals(1, failingRecords.countAll());

        // Simula retry apos falha da primeira resposta.
        RegistroDeDose retry = failingService.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        assertEquals(NOW, retry.getRecordedAt());
        assertEquals(1, failingRecords.countAll());
    }

    @Test
    void ausenciaDeRegistroNaoSignificaSkipped() {
        assertTrue(
                records.findByPlannedDoseId("dose-a").isEmpty());

        assertEquals(0, records.countAll());

        DosePrevista dose = service.findPlannedDose(
                USER_A, "dose-a");

        assertEquals(
                ScheduleStatus.SCHEDULED,
                dose.getStatus()
        );

        assertEquals(0, records.countAll());
    }

    @Test
    void registroNaoAlteraOcorrenciaOriginal() {
        DosePrevista before =
                planned.findById("dose-a").orElseThrow();

        service.recordDose(
                USER_A, "dose-a", DoseAction.TAKEN);

        DosePrevista after =
                planned.findById("dose-a").orElseThrow();

        assertEquals(before.getId(), after.getId());
        assertEquals(
                before.getScheduledAt(),
                after.getScheduledAt()
        );
        assertEquals(
                before.getScheduleVersion(),
                after.getScheduleVersion()
        );
        assertEquals(before.getStatus(), after.getStatus());
    }

    // Repositorio em memoria exclusivo dos testes.
    private static class MemoryPlannedRepository
            implements PlannedDoseRepository {

        private final Map<String, DosePrevista> data =
                new HashMap<>();

        @Override
        public DosePrevista save(DosePrevista dose) {
            data.put(dose.getId(), dose);
            return dose;
        }

        @Override
        public List<DosePrevista> saveAll(List<DosePrevista> doses) {
            List<DosePrevista> saved = new ArrayList<>();

            for (DosePrevista dose : doses) {
                saved.add(save(dose));
            }

            return saved;
        }

        @Override
        public Optional<DosePrevista> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public List<DosePrevista> findByUserAndDate(
                String userId, LocalDate date) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DosePrevista> findByTreatmentAndPeriod(
                String id, Instant start, Instant end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DosePrevista> findByUserAndPeriod(
                String id, Instant start, Instant end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countAll() {
            return data.size();
        }
    }

    private static class MemoryRecordRepository
            implements DoseRecordRepository {

        private final Map<String, RegistroDeDose> data =
                new HashMap<>();

        @Override
        public synchronized RegistroDeDose save(
                RegistroDeDose record) {

            RegistroDeDose existing =
                    data.get(record.getPlannedDoseId());

            if (existing != null) {
                if (!existing.getUserId().equals(record.getUserId())
                        || existing.getAction() != record.getAction()) {
                    throw new IllegalStateException(
                            "Registro em conflito");
                }

                return existing;
            }

            data.put(record.getPlannedDoseId(), record);
            return record;
        }

        @Override
        public synchronized Optional<RegistroDeDose>
                findByPlannedDoseId(String id) {

            return Optional.ofNullable(data.get(id));
        }

        @Override
        public List<RegistroDeDose> findByPeriod(
                Instant start, Instant end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, RegistroDeDose> findByPlannedDoseIds(
                Collection<String> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized int countAll() {
            return data.size();
        }
    }
}
