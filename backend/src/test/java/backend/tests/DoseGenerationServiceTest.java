
package backend.tests;

import backend.auth.ResourceNotFoundException;
import backend.models.*;
import backend.repositories.PlannedDoseRepository;
import backend.repositories.TreatmentRepository;
import backend.services.DoseGenerationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
// Mockito é uma biblioteca Java utilizada para criar objetos simulados (mocks).
// Permite testar uma classe isoladamente, sem depender das implementações reais
// de suas dependências, como repositories e conexões com o banco de dados.

// Principais métodos utilizados do Mockito:
//
// mock(): cria um objeto simulado de uma classe ou interface.
//
// when(): especifica uma chamada de método cujo comportamento queremos simular.
//
// thenReturn(): define o valor que será retornado pelo método simulado.
//
// thenAnswer(): permite definir dinamicamente o comportamento do método,
// podendo utilizar os argumentos recebidos para produzir seu retorno.
//
// verify(): verifica se determinado método do objeto simulado foi chamado.
//
// times(): determina quantas vezes um método deve ter sido chamado.
//
// verifyNoInteractions(): verifica se nenhuma interação ocorreu
// com o objeto simulado.
//
// anyList(): representa qualquer lista recebida como argumento de um método,
// independentemente dos elementos que ela contém.
public class DoseGenerationServiceTest {

    private TreatmentRepository treatmentRepository;
    private PlannedDoseRepository plannedDoseRepository;
    private DoseGenerationService service;

    private final Instant agora = Instant.parse("2026-11-21T15:00:00Z");

    @BeforeEach
    void preparar() {

        // Cria implementações simuladas dos repositories.
        treatmentRepository = mock(TreatmentRepository.class);
        plannedDoseRepository = mock(PlannedDoseRepository.class);

        // Fixa o relógio para garantir testes reproduzíveis.
        Clock clock = Clock.fixed(agora, ZoneOffset.UTC);

        service = new DoseGenerationService(
                treatmentRepository,
                plannedDoseRepository,
                clock
        );
    }

    private Treatment criarTratamento(TreatmentStatus status) {

        CronogramaDose cronograma = new CronogramaDose(
                List.of("08:00", "20:00"),
                "2026-11-21",
                "America/Sao_Paulo"
        );

        MedicamentoEmUso medicamento = new MedicamentoEmUso(
                "med-1",
                "Medicamento de teste",
                "50 mg",
                1,
                "comprimido",
                null,
                true,
                List.of(cronograma)
        );

        Instant encerramento = status == TreatmentStatus.CLOSED
                ? agora : null;

        return new Treatment(
                "trat-1",
                "user-1",
                "Tratamento de teste",
                null,
                LocalDate.of(2026, 11, 21),
                null,
                status,
                encerramento,
                List.of(medicamento)
        );
    }

    @Test
    void deveGerarEPersistirJanelaDeTrintaDias() {

        Treatment tratamento = criarTratamento(TreatmentStatus.ACTIVE);

        // Simula a recuperação do tratamento.
        when(treatmentRepository.findByIdAndUserId("trat-1", "user-1"))
                .thenReturn(Optional.of(tratamento));

        // Simula a persistência, retornando a lista recebida.
        when(plannedDoseRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<DosePrevista> doses = service.refreshRollingWindow("user-1", "trat-1");

        // A primeira dose do dia já passou.
        // Portanto, esperamos 59 ocorrências em vez de 60.
        assertEquals(59, doses.size());

        assertEquals(
                Instant.parse("2026-11-21T23:00:00Z"),
                doses.get(0).getScheduledAt()
        );

        assertEquals(
                Instant.parse("2026-12-20T23:00:00Z"),
                doses.get(doses.size() - 1).getScheduledAt()
        );

        // Verifica se o serviço delegou a persistência ao repository.
        verify(plannedDoseRepository, times(1)).saveAll(anyList());
    }

    @Test
    void tratamentoEmRascunhoNaoDevePersistirDoses() {

        Treatment tratamento = criarTratamento(TreatmentStatus.DRAFT);

        when(treatmentRepository.findByIdAndUserId("trat-1", "user-1"))
                .thenReturn(Optional.of(tratamento));

        List<DosePrevista> doses = service.refreshRollingWindow("user-1", "trat-1");

        assertTrue(doses.isEmpty());

        // Nenhuma operação deve ser realizada no repository de doses.
        verifyNoInteractions(plannedDoseRepository);
    }

    @Test
    void tratamentoEncerradoDeveCancelarDosesFuturas() {

        Treatment tratamento = criarTratamento(TreatmentStatus.CLOSED);

        when(treatmentRepository.findByIdAndUserId("trat-1", "user-1"))
                .thenReturn(Optional.of(tratamento));

        when(plannedDoseRepository.cancelScheduledForTreatmentFrom(
                "user-1", "trat-1", agora)).thenReturn(5L);

        long canceladas = service.cancelFutureForClosedTreatment("user-1", "trat-1");

        assertEquals(5L, canceladas);

        // O cancelamento deve utilizar o instante original de encerramento.
        verify(plannedDoseRepository).cancelScheduledForTreatmentFrom(
                "user-1", "trat-1", tratamento.getClosedAt()
        );
    }

    @Test
    void outroUsuarioNaoDeveGerarDosesDoTratamento() {

        when(treatmentRepository.findByIdAndUserId("trat-1", "user-2"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.refreshRollingWindow("user-2", "trat-1")
        );

        verifyNoInteractions(plannedDoseRepository);
    }

    @Test
    void naoDeveGerarDosesRetroativamente() {

        Instant inicio = agora.minusSeconds(3600);
        Instant fim = agora.plusSeconds(3600);

        assertThrows(IllegalArgumentException.class, () ->
                service.generate("user-1", "trat-1", inicio, fim)
        );

        verifyNoInteractions(plannedDoseRepository);
    }
}
