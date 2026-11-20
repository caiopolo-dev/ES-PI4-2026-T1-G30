
package backend.tests;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.models.ScheduleFrequency;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.TreatmentRepository;
import backend.services.ScheduleService;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleServiceTest {

    private final AuthContext usuario = new AuthContext("usuario-1");

    private CronogramaDose criarCronograma(String horario) {
        return new CronogramaDose(
                List.of(horario),
                "2026-11-01",
                "America/Sao_Paulo"
        );
    }

    private Treatment criarTratamento(
            TreatmentStatus status,
            boolean medicamentoAtivo,
            List<CronogramaDose> cronogramas) {

        MedicamentoEmUso medicamento = new MedicamentoEmUso(
                "med-1", "Medicamento de teste", "1 comprimido",
                1, "comprimido", null, medicamentoAtivo, cronogramas, 0L
        );

        Instant encerradoEm = null;
        if (status == TreatmentStatus.CLOSED) {
            encerradoEm = Instant.parse("2026-11-26T12:00:00Z");
        }

        return new Treatment(
                "tratamento-1", "usuario-1", "Tratamento de teste", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
                status, encerradoEm, List.of(medicamento)
        );
    }

    private ScheduleService criarServico(Treatment tratamento) {
        return new ScheduleService(new RepositorioDeTeste(tratamento));
    }

    @Test
    void deveConsultarAtualEHistorico() {
        CronogramaDose atual = criarCronograma("08:00");
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true, List.of(atual))
        );

        Optional<CronogramaDose> encontrado = servico.consultarAtual(
                usuario, "tratamento-1", "med-1"
        );
        List<CronogramaDose> historico = servico.consultarHistorico(
                usuario, "tratamento-1", "med-1"
        );

        assertTrue(encontrado.isPresent());
        assertEquals(1, encontrado.get().getVersion());
        assertEquals(1, historico.size());
        assertEquals("08:00", historico.get(0).getTimes().get(0));
    }

    @Test
    void deveRetornarVazioQuandoNaoExisteCronograma() {
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true, List.of())
        );

        assertTrue(servico.consultarAtual(
                usuario, "tratamento-1", "med-1").isEmpty());

        assertTrue(servico.consultarHistorico(
                usuario, "tratamento-1", "med-1").isEmpty());
    }

    @Test
    void devePrepararNovaVersaoSemAlterarHistorico() {
        Instant inicioAnterior = Instant.parse("2026-11-10T12:00:00Z");
        Instant inicioNovo = Instant.parse("2026-11-20T12:00:00Z");

        CronogramaDose anterior = criarCronograma("08:00")
                .withValidity(inicioAnterior, null);

        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true, List.of(anterior))
        );

        CronogramaDose nova = servico.prepararNovaVersao(
                usuario, "tratamento-1", "med-1",
                criarCronograma("09:00"), inicioNovo
        );

        assertEquals(2, nova.getVersion());
        assertEquals(List.of("09:00"), nova.getTimes());
        assertEquals(inicioNovo, nova.getValidFrom());
        assertNull(nova.getValidTo());
        assertTrue(nova.isActive());

        // O serviço apenas prepara a versão: não grava nem altera a anterior.
        assertEquals(1, servico.consultarHistorico(
                usuario, "tratamento-1", "med-1").size());

        assertEquals(List.of("08:00"), anterior.getTimes());
        assertNull(anterior.getValidTo());
    }

    @Test
    void naoDeveCriarNovaVersaoComConfiguracaoIgual() {
        Instant inicioAnterior = Instant.parse("2026-11-10T12:00:00Z");

        CronogramaDose anterior = criarCronograma("08:00")
                .withValidity(inicioAnterior, null);

        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true, List.of(anterior))
        );

        CronogramaDose resultado = servico.prepararNovaVersao(
                usuario, "tratamento-1", "med-1",
                criarCronograma("08:00"),
                inicioAnterior
        );

        assertEquals(1, resultado.getVersion());
        assertEquals(inicioAnterior, resultado.getValidFrom());
    }

    @Test
    void deveRejeitarTratamentoEncerrado() {
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.CLOSED, true, List.of())
        );

        try {
            servico.prepararNovaVersao(
                    usuario, "tratamento-1", "med-1",
                    criarCronograma("08:00"),
                    Instant.parse("2026-11-20T12:00:00Z")
            );

            fail("Tratamento encerrado deveria rejeitar a alteração");

        } catch (IllegalStateException esperado) {
            assertTrue(esperado.getMessage().contains("encerrado"));
        }
    }

    @Test
    void deveRejeitarMedicamentoInativo() {
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, false, List.of())
        );

        try {
            servico.prepararNovaVersao(
                    usuario, "tratamento-1", "med-1",
                    criarCronograma("08:00"),
                    Instant.parse("2026-11-20T12:00:00Z")
            );

            fail("Medicamento inativo deveria rejeitar a alteração");

        } catch (IllegalStateException esperado) {
            assertTrue(esperado.getMessage().contains("inativo"));
        }
    }

    @Test
    void deveRejeitarInicioForaDoTratamento() {
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true, List.of())
        );

        CronogramaDose configuracao = new CronogramaDose(
                List.of("08:00"),
                "2027-01-01",
                "America/Sao_Paulo"
        );

        try {
            servico.prepararNovaVersao(
                    usuario, "tratamento-1", "med-1",
                    configuracao,
                    Instant.parse("2026-11-20T12:00:00Z")
            );

            fail("Início fora do tratamento deveria ser rejeitado");

        } catch (IllegalArgumentException esperado) {
            assertTrue(esperado.getMessage().contains("após o tratamento"));
        }
    }

    @Test
    void deveRejeitarInicioEfetivoAposTratamentoSemFimNoCronograma() {
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true, List.of()));
        CronogramaDose configuracao = new CronogramaDose(
                1, ScheduleFrequency.EVERY_DAY, null, List.of("08:00"),
                "2026-11-01", null, "America/Sao_Paulo", true);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> servico.prepararNovaVersao(
                        usuario, "tratamento-1", "med-1", configuracao,
                        Instant.parse("2027-01-01T03:00:00Z")));

        assertTrue(erro.getMessage().contains("término do tratamento"));
    }

    @Test
    void deveNegarConsultaParaOutroUsuario() {
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true, List.of())
        );

        AuthContext outroUsuario = new AuthContext("usuario-2");

        try {
            servico.consultarHistorico(
                    outroUsuario, "tratamento-1", "med-1"
            );

            fail("Outro usuário não deveria consultar o tratamento");

        } catch (ResourceNotFoundException esperado) {
            assertNotNull(esperado);
        }
    }

    // Repositório simples para testar sem abrir conexão com o MongoDB.
    private static class RepositorioDeTeste implements TreatmentRepository {

        private final Treatment tratamento;

        public RepositorioDeTeste(Treatment tratamento) {
            this.tratamento = tratamento;
        }

        @Override
        public Treatment save(Treatment novo) {
            throw new UnsupportedOperationException(
                    "Gravação não usada neste teste"
            );
        }

        @Override
        public Optional<Treatment> findById(String id) {
            if (tratamento.getId().equals(id)) {
                return Optional.of(tratamento);
            }

            return Optional.empty();
        }

        @Override
        public List<Treatment> findByUserId(String userId) {
            if (tratamento.getUserId().equals(userId)) {
                return List.of(tratamento);
            }

            return List.of();
        }

        @Override
        public Treatment update(Treatment atualizado) {
            throw new UnsupportedOperationException(
                    "Gravação não usada neste teste"
            );
        }

        
    }


    @Test
    void deveEncerrarVigenciaAnteriorAoCriarNovaVersao() {
        Instant inicioAnterior = Instant.parse("2026-11-10T12:00:00Z");
        Instant inicioNovo = Instant.parse("2026-11-20T12:00:00Z");

        CronogramaDose anterior = criarCronograma("08:00")
                .withValidity(inicioAnterior, null);

        ScheduleService servico = criarServico(
                criarTratamento(
                        TreatmentStatus.DRAFT,
                        true,
                        List.of(anterior)
                )
        );

        List<CronogramaDose> resultado = servico.prepararTransicao(
                usuario,
                "tratamento-1",
                "med-1",
                criarCronograma("09:00"),
                inicioNovo
        );

        assertEquals(2, resultado.size());

        CronogramaDose versao1 = resultado.get(0);
        CronogramaDose versao2 = resultado.get(1);

        assertEquals(1, versao1.getVersion());
        assertEquals(inicioAnterior, versao1.getValidFrom());
        assertEquals(inicioNovo, versao1.getValidTo());
        assertFalse(versao1.isActive());

        assertEquals(2, versao2.getVersion());
        assertEquals(inicioNovo, versao2.getValidFrom());
        assertNull(versao2.getValidTo());
        assertTrue(versao2.isActive());

        assertEquals(List.of("08:00"), versao1.getTimes());
        assertEquals(List.of("09:00"), versao2.getTimes());

        // Confirma que o histórico original não foi modificado.
        assertNull(anterior.getValidTo());
        assertTrue(anterior.isActive());
    }

    @Test
    void deveManterHistoricoQuandoConfiguracaoForIdentica() {
        Instant inicioAnterior = Instant.parse("2026-11-10T12:00:00Z");

        CronogramaDose anterior = criarCronograma("08:00")
                .withValidity(inicioAnterior, null);

        ScheduleService servico = criarServico(
                criarTratamento(
                        TreatmentStatus.DRAFT,
                        true,
                        List.of(anterior)
                )
        );

        List<CronogramaDose> resultado = servico.prepararTransicao(
                usuario,
                "tratamento-1",
                "med-1",
                criarCronograma("08:00"),
                inicioAnterior
        );

        assertEquals(1, resultado.size());
        assertEquals(1, resultado.get(0).getVersion());
        assertEquals(inicioAnterior, resultado.get(0).getValidFrom());
        assertNull(resultado.get(0).getValidTo());
    }

    @Test
    void deveVersionarQuandoSomenteADataEfetivaMuda() {
        Instant inicioAnterior = Instant.parse("2026-11-10T12:00:00Z");
        Instant inicioNovo = Instant.parse("2026-11-20T12:00:00Z");

        CronogramaDose anterior = criarCronograma("08:00")
                .withValidity(inicioAnterior, null);
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true, List.of(anterior)));

        List<CronogramaDose> resultado = servico.prepararTransicao(
                usuario, "tratamento-1", "med-1",
                criarCronograma("08:00"), inicioNovo);

        assertEquals(2, resultado.size());
        assertEquals(inicioNovo, resultado.get(0).getValidTo());
        assertEquals(2, resultado.get(1).getVersion());
        assertEquals(inicioNovo, resultado.get(1).getValidFrom());
    }

    @Test
    void deveRejeitarHistoricoComVigenciaSobreposta() {
        Instant inicioPrimeiro = Instant.parse("2026-11-01T12:00:00Z");
        Instant inicioSegundo = Instant.parse("2026-11-05T12:00:00Z");
        Instant inicioAtual = Instant.parse("2026-11-10T12:00:00Z");
        Instant inicioNovo = Instant.parse("2026-11-20T12:00:00Z");
        CronogramaDose primeiro = criarCronograma("07:00")
                .withValidity(inicioPrimeiro, inicioAtual)
                .deactivate();
        CronogramaDose segundoSobreposto = criarCronograma("07:30")
                .withVersion(2)
                .withValidity(inicioSegundo, Instant.parse("2026-11-08T12:00:00Z"))
                .deactivate();
        CronogramaDose atual = criarCronograma("08:00")
                .withVersion(3)
                .withValidity(inicioAtual, null);
        ScheduleService servico = criarServico(
                criarTratamento(TreatmentStatus.DRAFT, true,
                        List.of(primeiro, segundoSobreposto, atual)));

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> servico.prepararTransicao(
                        usuario, "tratamento-1", "med-1",
                        criarCronograma("09:00"), inicioNovo));

        assertTrue(erro.getMessage().contains("sobreposta"));
    }

    @Test
    void deveRejeitarTransicaoSemVigenciaAnterior() {
        CronogramaDose anterior = criarCronograma("08:00");

        ScheduleService servico = criarServico(
                criarTratamento(
                        TreatmentStatus.DRAFT,
                        true,
                        List.of(anterior)
                )
        );

        try {
            servico.prepararTransicao(
                    usuario,
                    "tratamento-1",
                    "med-1",
                    criarCronograma("09:00"),
                    Instant.parse("2026-11-20T12:00:00Z")
            );

            fail("Deveria rejeitar versão sem início de vigência");

        } catch (IllegalStateException esperado) {
            assertTrue(
                    esperado.getMessage().contains("início de vigência")
            );
        }
    }

}
