// Caio Ferreira Polo
package backend.services;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.TreatmentRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;

// issue 28
// Responsável por consultar cronogramas e preparar novas versões.
// A gravação no MongoDB será integrada em uma próxima etapa.
public class ScheduleService {

    private final TreatmentAccessService accessService;

    // issue 28
    // Reutiliza o controle de acesso existente para verificar o titular.
    public ScheduleService(TreatmentRepository repository) {
        Objects.requireNonNull(repository, "repository não pode ser null");
        this.accessService = new TreatmentAccessService(repository);
    }

    // issue 28
    // Consulta todas as versões do cronograma de um medicamento.
    public List<CronogramaDose> consultarHistorico(AuthContext context,String treatmentId,String medicationId) {

        // Verifica se o usuário pode acessar o medicamento.
        MedicamentoEmUso medicamento = accessService.findMedication(context, treatmentId, medicationId);

        // Retorna as versões existentes, incluindo as antigas.
        return medicamento.getSchedules();
    }

    // issue 28
    // Consulta a versão atualmente ativa do cronograma.
    public Optional<CronogramaDose> consultarAtual(AuthContext context,String treatmentId,String medicationId) {

        // Busca o medicamento verificando o usuário autenticado.
        MedicamentoEmUso medicamento = accessService.findMedication(context, treatmentId, medicationId);

        // Optional representa um resultado que pode não existir.
        return medicamento.getActiveSchedule();
    }

    // issue 28
    // Valida uma alteração e prepara a próxima versão do cronograma.
    // Não modifica o medicamento original nem grava no banco.
    public CronogramaDose prepararNovaVersao(AuthContext context, String treatmentId,String medicationId,CronogramaDose configuracao,Instant inicioVigencia) {

        // Verifica se os dados necessários foram informados.
        Objects.requireNonNull(configuracao,"configuracao não pode ser null");

        Objects.requireNonNull(inicioVigencia,"inicioVigencia não pode ser null");

        // Busca o tratamento e verifica se pertence ao usuário.
        Treatment tratamento = accessService.find(context, treatmentId);

        // Impede alterações em tratamentos encerrados.
        if (tratamento.getStatus() == TreatmentStatus.CLOSED) {
            throw new IllegalStateException("Tratamento encerrado não pode alterar cronogramas");
        }

        // Localiza o medicamento dentro do tratamento.
        Optional<MedicamentoEmUso> resultado = tratamento.findMedicationById(medicationId);

        if (resultado.isEmpty()) {
            throw new ResourceNotFoundException();
        }

        MedicamentoEmUso medicamento = resultado.get();

        // Não permite configurar medicamentos desativados.
        if (!medicamento.isActive()) {
            throw new IllegalStateException("Medicamento inativo não pode alterar cronogramas");
        }

        // Verifica se o início planejado está dentro do tratamento.
        LocalDate inicio = LocalDate.parse(configuracao.getStartDate());

        if (tratamento.getStartDate() != null && inicio.isBefore(tratamento.getStartDate())) {
            throw new IllegalArgumentException("Cronograma não pode começar antes do tratamento");
        }

        if (tratamento.getEndDate() != null && inicio.isAfter(tratamento.getEndDate())) {
            throw new IllegalArgumentException("Cronograma não pode começar após o tratamento");
        }

        // Percorre o histórico para identificar a última versão.
        int maiorVersao = 0;
        int quantidadeAtivas = 0;
        CronogramaDose cronogramaAtual = null;

        for (CronogramaDose cronograma : medicamento.getSchedules()) {

            if (cronograma.getVersion() > maiorVersao) {
                maiorVersao = cronograma.getVersion();
            }

            // Identifica o cronograma atualmente ativo.
            if (cronograma.isActive()) {
                quantidadeAtivas++;
                cronogramaAtual = cronograma;
            }
        }

        // Impede continuar se o histórico já estiver inconsistente.
        if (quantidadeAtivas > 1) {
            throw new IllegalStateException("Medicamento possui mais de um cronograma ativo");
        }

        // Evita criar outra versão quando a configuração é igual.
        if (cronogramaAtual != null && cronogramaAtual.hasSameConfiguration(configuracao)) {

            return cronogramaAtual;
        }

        // Impede iniciar uma nova versão antes da versão atual.
        if (cronogramaAtual != null && cronogramaAtual.getValidFrom() != null&& !inicioVigencia.isAfter(cronogramaAtual.getValidFrom())) {
            throw new IllegalArgumentException("Nova versão deve começar após a versão atual");
        }

        // Evita ultrapassar o limite numérico das versões.
        if (maiorVersao == Integer.MAX_VALUE) {
            throw new IllegalStateException("Limite de versões do cronograma atingido");
        }

        // Prepara uma nova versão ativa com início de vigência.
        // O término fica indefinido até a substituição ou encerramento.
        return configuracao.withVersion(maiorVersao + 1).withActive(true).withValidity(inicioVigencia, null);
    }


    // Prepara o histórico atualizado quando um cronograma é substituído.
    // Encerra a vigência anterior e adiciona a nova versão.
    // Não altera o histórico original nem grava no MongoDB.
    public List<CronogramaDose> prepararTransicao(
            AuthContext context,
            String treatmentId,
            String medicationId,
            CronogramaDose configuracao,
            Instant inicioVigencia) {

        // Reaproveita as validações já existentes no serviço.
        CronogramaDose novaVersao = prepararNovaVersao(
                context,
                treatmentId,
                medicationId,
                configuracao,
                inicioVigencia
        );

        // Recupera as versões atuais do medicamento.
        MedicamentoEmUso medicamento = accessService.findMedication(
                context, treatmentId, medicationId
        );

        List<CronogramaDose> historico = medicamento.getSchedules();

        // Se a configuração já existe como versão atual,
        // devolve o histórico sem criar uma versão duplicada.
        if (medicamento.getScheduleByVersion(
                novaVersao.getVersion()).isPresent()) {

            return List.copyOf(historico);
        }

        // Cria uma nova lista para preservar o histórico original.
        List<CronogramaDose> atualizado = new ArrayList<>();

        // Percorre as versões existentes.
        for (CronogramaDose anterior : historico) {

            if (anterior.isActive()) {

                // Não é seguro encerrar uma versão sem saber
                // o instante em que sua vigência começou.
                if (anterior.getValidFrom() == null) {
                    throw new IllegalStateException(
                            "Versão anterior não possui início de vigência"
                    );
                }

                // Uma versão com término definido exige
                // tratamento específico antes da substituição.
                if (anterior.getValidTo() != null) {
                    throw new IllegalStateException(
                            "Versão ativa já possui término de vigência"
                    );
                }

                // Encerra a versão antiga exatamente no instante
                // em que a nova versão começa a valer.
                CronogramaDose encerrado = anterior
                        .withValidity(
                                anterior.getValidFrom(),
                                inicioVigencia
                        )
                        .deactivate();

                atualizado.add(encerrado);

            } else {

                // Uma versão histórica não pode ter uma vigência
                // aberta ou avançar sobre o início da nova versão.
                if (anterior.getValidTo() == null
                        || anterior.getValidTo().isAfter(inicioVigencia)) {

                    throw new IllegalStateException(
                            "Histórico possui vigência indefinida ou sobreposta"
                    );
                }

                // Mantém as versões históricas anteriores.
                atualizado.add(anterior);
            }
        }

        // Adiciona a nova versão ao final do histórico.
        atualizado.add(novaVersao);

        // Retorna uma lista imutável para impedir alterações externas.
        return List.copyOf(atualizado);
    }




}
