// Desenvolvido por Caio Ferreira Polo.
package backend.services;

import backend.auth.AuthContext;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.repositories.DoseRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Registra a ação realizada sobre uma dose prevista.
 * O service confirma a titularidade, evita registros duplicados e utiliza
 * o repository para consultar e persistir o resultado.
 */
public final class DoseService {
    private final DoseRecordRepository records;
    private final DoseAccessService accessService;
    private final Clock clock;

    public DoseService(DoseRecordRepository records, DoseAccessService accessService) {
        this(records, accessService, Clock.systemUTC());
    }

    public DoseService(DoseRecordRepository records, DoseAccessService accessService, Clock clock) {

        this.records = Objects.requireNonNull(records);
        this.accessService = Objects.requireNonNull(accessService);
        this.clock = Objects.requireNonNull(clock);
    }

    public RegistroDeDose recordDose(AuthContext context, String plannedDoseId, DoseAction action) {

        // O mesmo monitor das alterações de tratamento mantém a decisão e a gravação
        // protegidas contra operações concorrentes dentro deste processo.
        synchronized (TreatmentMutationLock.MONITOR) {
            validateAction(action);

            // DoseAccessService localiza a dose e confirma que ela pertence
            // ao usuário identificado pela sessão.
            DosePrevista plannedDose = accessService.find(context, plannedDoseId);
            Optional<RegistroDeDose> existing = records.findByPlannedDoseId(plannedDose.getId());
            if (existing.isPresent()) {
                // Repetir a mesma ação devolve o registro existente;
                // tentar uma ação diferente produz conflito.
                return applyExistingRecordPolicy(existing.get(), context.userId(), action);
            }

            // Uma dose cancelada continua no histórico, mas não pode receber registro.
            if (plannedDose.getStatus() == ScheduleStatus.CANCELLED) {
                throw new DoseRecordConflictException("Dose cancelada nao pode receber registro.");
            }

            Instant recordedAt = clock.instant();
            // Somente TAKEN possui horário real de administração; SKIPPED indica que não ocorreu.
            Instant actualTime = action == DoseAction.TAKEN ? recordedAt : null;
            RegistroDeDose candidate = new RegistroDeDose(null, plannedDose.getId(), context.userId(), action, actualTime, recordedAt);

            RegistroDeDose persisted;
            try {
                persisted = records.save(candidate);

            } catch (IllegalStateException error) {
                // Outra requisição pode ter gravado primeiro. Releio o repository
                // para identificar se houve repetição ou uma ação incompatível.
                Optional<RegistroDeDose> winner = records.findByPlannedDoseId(plannedDose.getId());

                if (winner.isPresent() && context.userId().equals(winner.get().getUserId()) && winner.get().getAction() != action) {
                    throw new DoseRecordConflictException("Dose ja possui registro com outra acao.");
                }
                throw error;
            }
            return applyExistingRecordPolicy(persisted, context.userId(), action);
        }
    }

    public DosePrevista findPlannedDose(AuthContext context, String plannedDoseId) {
        return accessService.find(context, plannedDoseId);
    }

    private void validateAction(DoseAction action) {
        if (action != DoseAction.TAKEN && action != DoseAction.SKIPPED) {
            throw new IllegalArgumentException("Acao de dose invalida.");
        }
    }

    private RegistroDeDose applyExistingRecordPolicy(RegistroDeDose record,String userId,DoseAction requestedAction) {
        // Mesmo um registro já existente precisa continuar associado ao titular correto.
        if (!userId.equals(record.getUserId())) {
            throw new IllegalStateException("Registro de dose com titularidade inconsistente.");
        }
        if (record.getAction() != requestedAction) {
            throw new DoseRecordConflictException("Dose ja possui registro com outra acao.");
        }
        return record;
    }
}
