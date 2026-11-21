// Autor: Samuel Campovilla
package backend.services;

import backend.auth.ResourceNotFoundException;
import backend.models.DosePrevista;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.PlannedDoseRepository;
import backend.repositories.TreatmentRepository;

import java.time.*;
import java.util.List;
import java.util.Objects;

import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;

// Responsável por coordenar a geração e persistência das doses previstas.
// Utiliza o DoseOccurrencePlanner para calcular as ocorrências
// e o PlannedDoseRepository para armazená-las de forma idempotente.

// Envolvemos todo o corpo do método com synchronized para garantir
// que nenhuma outra operação protegida altere o tratamento entre
// sua leitura, processamento e persistência.
// Ao finalizar o bloco, a thread libera automaticamente o monitor,
// permitindo que outra thread prossiga com sua execução.

public class DoseGenerationService {

    private static final int WINDOW_DAYS = 30;
    private static final ZoneId REFERENCE_ZONE = ZoneId.of("America/Sao_Paulo");

    private final TreatmentRepository treatmentRepository;
    private final PlannedDoseRepository plannedDoseRepository;
    private final DoseOccurrencePlanner planner;
    private final Clock clock;

    // Construtor secundário
    // O this(...), nesse contexto, chama outro construtor da própria classe, passando três argumentos:
    public DoseGenerationService(TreatmentRepository treatmentRepository, PlannedDoseRepository plannedDoseRepository) {
        this(treatmentRepository, plannedDoseRepository, Clock.systemUTC());
    }

    //Construtor principal
    public DoseGenerationService(
            TreatmentRepository treatmentRepository,
            PlannedDoseRepository plannedDoseRepository,
            Clock clock) {

        this.treatmentRepository = Objects.requireNonNull(treatmentRepository);
        this.plannedDoseRepository = Objects.requireNonNull(plannedDoseRepository);
        this.clock = Objects.requireNonNull(clock);
        this.planner = new DoseOccurrencePlanner();
    }

    // Gera e persiste as ocorrências de um tratamento
    // dentro de um intervalo explicitamente informado.
    public List<DosePrevista> generate(
            String userId,
            String treatmentId,
            Instant startInclusive,
            Instant endExclusive) {
        // synchronized permite que apenas uma thread por vez execute
        // um bloco de código protegido pelo mesmo monitor.
        synchronized (TreatmentMutationLock.MONITOR) {
            if (startInclusive == null || endExclusive == null
                    || !startInclusive.isBefore(endExclusive)) {
                throw new IllegalArgumentException("Intervalo de geração inválido");
            }

            // Uma regra atual não deve ser utilizada para reconstruir doses passadas.
            if (startInclusive.isBefore(clock.instant())) {
                throw new IllegalArgumentException("Não é permitido gerar doses retroativamente");
            }

            Treatment treatment = buscarTratamento(userId, treatmentId);

            return gerarEPersistir(treatment, startInclusive, endExclusive);
        }
    }

    // Gera as ocorrências restantes do dia atual
    // e dos próximos 29 dias.
    public List<DosePrevista> refreshRollingWindow(
            String userId,
            String treatmentId) {

        synchronized (TreatmentMutationLock.MONITOR) {
            Instant agora = clock.instant();

            LocalDate hoje = agora.atZone(REFERENCE_ZONE).toLocalDate();

            // Obtém o início do dia seguinte à janela de geração.
            Instant fim = hoje.plusDays(WINDOW_DAYS)
                    .atStartOfDay(REFERENCE_ZONE).toInstant();

            Treatment treatment = buscarTratamento(userId, treatmentId);

            return gerarEPersistir(treatment, agora, fim);
        }
    }

    // Cancela as ocorrências futuras de um tratamento encerrado,
    // preservando as ocorrências anteriores ao encerramento.
    public long cancelFutureForClosedTreatment(
            String userId,
            String treatmentId) {
        // synchronized permite que apenas uma thread por vez execute
        // um bloco de código protegido pelo mesmo monitor.
        synchronized (TreatmentMutationLock.MONITOR) {
            Treatment treatment = buscarTratamento(userId, treatmentId);

            if (treatment.getStatus() != TreatmentStatus.CLOSED) {
                throw new IllegalStateException("O tratamento precisa estar encerrado");
            }

            // Utiliza o instante original de encerramento,
            // mesmo quando o cancelamento é executado novamente.
            return plannedDoseRepository.cancelScheduledForTreatmentFrom(
                    treatment.getUserId(),
                    treatment.getId(),
                    treatment.getClosedAt()
            );
        }
    }

    // Recupera o tratamento verificando sua titularidade.
    private Treatment buscarTratamento(String userId, String treatmentId) {

        if (userId == null || userId.isBlank()
                || treatmentId == null || treatmentId.isBlank()) {
            throw new IllegalArgumentException("Usuário e tratamento devem ser válidos");
        }

        return treatmentRepository.findByIdAndUserId(treatmentId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    // Calcula as ocorrências e delega a persistência ao repository.
    private List<DosePrevista> gerarEPersistir(
            Treatment treatment,
            Instant inicio,
            Instant fim) {

        List<DosePrevista> doses = planner.planOccurrences(treatment, inicio, fim);

        // Evita acessar o banco quando não existem doses para gerar.
        if (doses.isEmpty()) {
            return doses;
        }

        // Retorna as ocorrências efetivamente armazenadas no banco.
        return plannedDoseRepository.saveAll(doses);
    }


    // Reconcilia as doses previstas com o estado atual do tratamento.
// Pode ser executado novamente sem duplicar ocorrências.
    public List<DosePrevista> reconcile(String userId, String treatmentId) {
        // synchronized permite que apenas uma thread por vez execute
        // um bloco de código protegido pelo mesmo monitor.
        synchronized (TreatmentMutationLock.MONITOR) {

            Treatment treatment = buscarTratamento(userId, treatmentId);

            // Tratamentos encerrados não devem possuir doses
            // agendadas a partir do instante de encerramento.
            if (treatment.getStatus() == TreatmentStatus.CLOSED) {

                plannedDoseRepository.cancelScheduledForTreatmentFrom(
                        userId, treatmentId, treatment.getClosedAt()
                );

                return List.of();
            }

            // Tratamentos em rascunho não geram doses.
            if (treatment.getStatus() != TreatmentStatus.ACTIVE) {
                return List.of();
            }


            // Recupera o instante atual para tratamento de documentos legados.
            Instant agora = clock.instant();

            for (MedicamentoEmUso medicamento : treatment.getMedications()) {

                for (CronogramaDose cronograma : medicamento.getSchedules()) {

                    Instant limite = cronograma.getValidTo();

                    if (!medicamento.isActive()) {

                        Instant desativacao = medicamento.getDeactivatedAt();

                        // Documentos antigos podem não possuir esse instante.
                        // Nesse caso, preservamos o passado e cancelamos a partir de agora.
                        if (desativacao == null) {
                            desativacao = agora;
                        }

                        // Considera o primeiro instante em que a regra deixou de valer.
                        if (limite == null || desativacao.isBefore(limite)) {
                            limite = desativacao;
                        }
                    }

                    if (limite != null) {

                        plannedDoseRepository.cancelScheduledForMedicationVersionFrom(
                                userId,
                                treatmentId,
                                medicamento.getId(),
                                cronograma.getVersion(),
                                limite
                        );
                    }
                }
            }


            // Completa a janela móvel utilizando as versões válidas.
            return refreshRollingWindow(userId, treatmentId);
        }
    }

}
