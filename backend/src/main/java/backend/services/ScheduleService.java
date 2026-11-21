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
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;

// issue 28
// Responsável por consultar, versionar e persistir cronogramas.
public class ScheduleService {

    private final TreatmentAccessService accessService;
    // issue 28 - Mantém a gravação condicional no mesmo repositório do tratamento.
    private final TreatmentRepository repository;

    private final DoseGenerationService doseGenerationService;

    public ScheduleService(TreatmentRepository repository) {
        this(repository, null);
    }

    public ScheduleService(
            TreatmentRepository repository,
            DoseGenerationService doseGenerationService) {

        this.repository = Objects.requireNonNull(repository);
        this.accessService = new TreatmentAccessService(repository);
        this.doseGenerationService = doseGenerationService;
    }


    // issue 28
    // Consulta todas as versões do cronograma de um medicamento.
    public List<CronogramaDose> consultarHistorico(AuthContext context,String treatmentId,String medicationId) {

        // Verifica se o usuário pode acessar o medicamento.
        MedicamentoEmUso medicamento = accessService.findMedication(context, treatmentId, medicationId);

        // Retorna as versões existentes, incluindo as antigas.
        return medicamento.getSchedules();
    }

    // issue 28 - Expõe o medicamento validado para informar a revisão no contrato HTTP.
    public MedicamentoEmUso consultarMedicamento(
            AuthContext context, String treatmentId, String medicationId) {
        return accessService.findMedication(context, treatmentId, medicationId);
    }

    // issue 28
    // Consulta a versão atualmente ativa do cronograma.
    public Optional<CronogramaDose> consultarAtual(AuthContext context,String treatmentId,String medicationId) {

        return consultarNoInstante(context, treatmentId, medicationId, Instant.now());
    }

    // issue 28 - Localiza a versão pela vigência [validFrom, validTo), não apenas por active.
    public Optional<CronogramaDose> consultarNoInstante(
            AuthContext context,
            String treatmentId,
            String medicationId,
            Instant instante) {

        Objects.requireNonNull(instante, "instante não pode ser null");

        // Busca o medicamento verificando o usuário autenticado.
        MedicamentoEmUso medicamento = accessService.findMedication(context, treatmentId, medicationId);

        CronogramaDose encontrado = null;
        for (CronogramaDose cronograma : medicamento.getSchedules()) {
            if (cronograma.coversInstant(instante)) {
                if (encontrado != null) {
                    throw new IllegalStateException("Cronogramas possuem vigências sobrepostas");
                }
                encontrado = cronograma;
            }
        }

        if (encontrado != null) {
            return Optional.of(encontrado);
        }

        // Compatibilidade de leitura: versões legadas não possuem vigência conhecida.
        Optional<CronogramaDose> legado = medicamento.getActiveSchedule();
        if (legado.isPresent()
                && legado.get().getValidFrom() == null
                && legado.get().getValidTo() == null) {
            return legado;
        }

        return Optional.empty();
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

        // issue 28 - Confere datas planejadas e o instante efetivo contra a vigência do tratamento.
        validarVigenciaPlanejada(tratamento, configuracao, inicioVigencia);

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

        // issue 28 - Só considera repetição quando configuração e vigência são exatamente iguais.
        if (cronogramaAtual != null
                && cronogramaAtual.hasSameConfiguration(configuracao)
                && Objects.equals(cronogramaAtual.getValidFrom(), inicioVigencia)
                && cronogramaAtual.getValidTo() == null) {

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

        CronogramaDose novaVersao = configuracao
                .withVersion(maiorVersao + 1)
                .withActive(true)
                .withValidity(inicioVigencia, null);

        // Captura a posologia do medicamento no momento
        // em que a nova versão do cronograma é preparada.
        if (medicamento.getQuantity() != null && medicamento.getUnit() != null) {

            novaVersao = novaVersao.withPosologySnapshot(
                    medicamento.getQuantity(),
                    medicamento.getUnit()
            );
        }

        return novaVersao;
    }

    // issue 28 - Persiste a transição com revisão otimista e trata retransmissões idênticas.
    public MedicamentoEmUso salvar(
            AuthContext context,
            String treatmentId,
            String medicationId,
            CronogramaDose configuracao,
            Instant inicioVigencia,
            long expectedRevision) {

        synchronized (TreatmentMutationLock.MONITOR) {
            if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("Revisão inválida");
            }

            Treatment tratamento = accessService.find(context, treatmentId);
            if (tratamento.getStatus() == TreatmentStatus.CLOSED) {
                throw new IllegalStateException("Tratamento encerrado não pode alterar cronogramas");
            }
            MedicamentoEmUso atual = tratamento.findMedicationById(medicationId)
                    .orElseThrow(ResourceNotFoundException::new);
            if (!atual.isActive()) {
                throw new IllegalStateException("Medicamento inativo não pode alterar cronogramas");
            }
            // issue 28 - A idempotência só pode aceitar um histórico previamente consistente.
            validarIntegridadeHistorico(atual.getSchedules());
            Optional<CronogramaDose> cronogramaAtual = atual.getActiveSchedule();

            if (cronogramaAtual.isPresent()
                    && cronogramaAtual.get().hasSameConfiguration(configuracao)
                    && Objects.equals(cronogramaAtual.get().getValidFrom(), inicioVigencia)
                    && cronogramaAtual.get().getValidTo() == null) {
                return reconciliarDoses(context, treatmentId, atual);
            }

            if (atual.getRevision() != expectedRevision) {
                throw new MedicationOperationConflictException(
                        "Cronograma alterado. Consulte os dados novamente.");
            }

            List<CronogramaDose> atualizado = prepararTransicao(
                    context, treatmentId, medicationId, configuracao, inicioVigencia);

            boolean gravado = repository.updateMedicationSchedules(
                    treatmentId,
                    context.userId(),
                    medicationId,
                    expectedRevision,
                    atualizado
            );

            if (!gravado) {
                MedicamentoEmUso concorrente = accessService.findMedication(
                        context, treatmentId, medicationId);
                // issue 28 - Não converte um histórico concorrente inconsistente em sucesso idempotente.
                validarIntegridadeHistorico(concorrente.getSchedules());
                Optional<CronogramaDose> concorrenteAtual = concorrente.getActiveSchedule();

                if (concorrenteAtual.isPresent()
                        && concorrenteAtual.get().hasSameConfiguration(configuracao)
                        && Objects.equals(concorrenteAtual.get().getValidFrom(), inicioVigencia)
                        && concorrenteAtual.get().getValidTo() == null) {
                    return reconciliarDoses(context, treatmentId, concorrente);
                }

                throw new MedicationOperationConflictException(
                        "Cronograma alterado durante a gravação.");
            }

            MedicamentoEmUso medicamentoAtualizado = accessService.findMedication(
                    context, treatmentId, medicationId
            );

            return reconciliarDoses(context, treatmentId, medicamentoAtualizado);
        }
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

        // issue 28 - Valida todos os intervalos existentes antes de preparar qualquer alteração.
        validarIntegridadeHistorico(historico);

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

                // Preserva a posologia da versão anterior antes de sua substituição.
                // Para versões antigas sem snapshot, utiliza os dados da versão
                // ainda aberta, antes de qualquer atualização do medicamento.
                if (!encerrado.hasPosologySnapshot()
                        && medicamento.getQuantity() != null
                        && medicamento.getUnit() != null) {

                    encerrado = encerrado.withPosologySnapshot(
                            medicamento.getQuantity(),
                            medicamento.getUnit()
                    );
                }

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

    // issue 28 - Garante que quaisquer duas versões respeitem intervalos [validFrom, validTo).
    private void validarIntegridadeHistorico(List<CronogramaDose> historico) {
        int quantidadeAtivas = 0;
        for (int i = 0; i < historico.size(); i++) {
            CronogramaDose primeiro = historico.get(i);
            if (primeiro.getValidFrom() == null) {
                throw new IllegalStateException("Histórico possui versão sem início de vigência");
            }
            if (primeiro.isActive()) {
                quantidadeAtivas++;
                if (primeiro.getValidTo() != null) {
                    throw new IllegalStateException("Versão ativa possui término de vigência");
                }
            } else if (primeiro.getValidTo() == null) {
                throw new IllegalStateException("Versão histórica possui vigência aberta");
            }

            for (int j = i + 1; j < historico.size(); j++) {
                CronogramaDose segundo = historico.get(j);
                if (segundo.getValidFrom() == null) {
                    throw new IllegalStateException("Histórico possui versão sem início de vigência");
                }

                boolean primeiroAntesDoFimDoSegundo = segundo.getValidTo() == null
                        || primeiro.getValidFrom().isBefore(segundo.getValidTo());
                boolean segundoAntesDoFimDoPrimeiro = primeiro.getValidTo() == null
                        || segundo.getValidFrom().isBefore(primeiro.getValidTo());
                if (primeiroAntesDoFimDoSegundo && segundoAntesDoFimDoPrimeiro) {
                    throw new IllegalStateException("Histórico possui vigências sobrepostas");
                }
            }
        }
        if (quantidadeAtivas > 1) {
            throw new IllegalStateException("Medicamento possui mais de um cronograma ativo");
        }
    }

    // issue 28 - Mantém cronograma e início efetivo dentro do período planejado.
    private void validarVigenciaPlanejada(
            Treatment tratamento,
            CronogramaDose configuracao,
            Instant inicioVigencia) {

        LocalDate inicio = LocalDate.parse(configuracao.getStartDate());
        LocalDate fim = configuracao.getEndDate() == null
                ? null
                : LocalDate.parse(configuracao.getEndDate());

        if (tratamento.getStartDate() != null && inicio.isBefore(tratamento.getStartDate())) {
            throw new IllegalArgumentException("Cronograma não pode começar antes do tratamento");
        }
        if (tratamento.getEndDate() != null && inicio.isAfter(tratamento.getEndDate())) {
            throw new IllegalArgumentException("Cronograma não pode começar após o tratamento");
        }
        if (fim != null && tratamento.getStartDate() != null
                && fim.isBefore(tratamento.getStartDate())) {
            throw new IllegalArgumentException("Cronograma termina antes do tratamento");
        }
        if (fim != null && tratamento.getEndDate() != null
                && fim.isAfter(tratamento.getEndDate())) {
            throw new IllegalArgumentException("Cronograma termina após o tratamento");
        }

        LocalDate inicioEfetivo = inicioVigencia
                .atZone(ZoneId.of(configuracao.getTimezone()))
                .toLocalDate();
        if (tratamento.getEndDate() != null
                && inicioEfetivo.isAfter(tratamento.getEndDate())) {
            throw new IllegalArgumentException("Início da vigência após o término do tratamento");
        }
        if (inicioEfetivo.isBefore(inicio) || (fim != null && inicioEfetivo.isAfter(fim))) {
            throw new IllegalArgumentException("Início da vigência fora do período do cronograma");
        }
    }


    // Reconcilia as doses após a persistência do cronograma.
    private MedicamentoEmUso reconciliarDoses(
            AuthContext context,
            String treatmentId,
            MedicamentoEmUso medicamento) {

        if (doseGenerationService != null) {
            doseGenerationService.reconcile(context.userId(), treatmentId);
        }

        return medicamento;
    }

}
