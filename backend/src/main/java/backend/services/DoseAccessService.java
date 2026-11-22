// Desenvolvido por Miguel Gallinucci.
package backend.services;

import backend.auth.AuthContext;
import backend.models.DoseAction;
import backend.models.DosePrevista;
import backend.models.RegistroDeDose;
import backend.models.ScheduleStatus;
import backend.repositories.DoseRecordRepository;
import backend.repositories.PlannedDoseRepository;
import java.time.Clock;
import java.time.Instant;
import backend.auth.ResourceNotFoundException;
import java.util.Objects;
import java.util.Optional;

/** Confere a titularidade antes de consultar ou registrar uma dose. */
public final class DoseAccessService {
    private final PlannedDoseRepository doses;
    private final DoseRecordRepository records;
    private final Clock clock;

    public DoseAccessService(PlannedDoseRepository doses, DoseRecordRepository records) {
        this(doses, records, Clock.systemUTC());
    }

    public DoseAccessService(PlannedDoseRepository doses, DoseRecordRepository records, Clock clock) {
        // Recebi Clock para os testes controlarem o horario de registro sem depender do relogio real.
        this.doses = Objects.requireNonNull(doses);
        this.records = Objects.requireNonNull(records);
        this.clock = Objects.requireNonNull(clock);
    }

    public DosePrevista find(AuthContext context, String doseId) {
        String userId = Objects.requireNonNull(context).userId();
        if (doseId == null || doseId.isBlank()) {
            throw notFound();
        }
        // Conhecer o ID da dose nao basta: ela tambem precisa pertencer ao titular da sessao.
        return doses.findById(doseId).filter(dose -> userId.equals(dose.getUserId()))
            .orElseThrow(DoseAccessService::notFound);
    }

    public Optional<RegistroDeDose> findRecord(AuthContext context, String doseId) {
        // Autorizei a dose antes de consultar o registro e conferi tambem o titular do registro encontrado.
        DosePrevista dose = find(context, doseId);
        return records.findByPlannedDoseId(dose.getId())
            .filter(record -> context.userId().equals(record.getUserId()));
    }

    public RegistroDeDose record(AuthContext context, String doseId, DoseAction action, Instant actualTime) {
        synchronized (TreatmentMutationLock.MONITOR) {
            DosePrevista dose = find(context, doseId);
            if (dose.getStatus() == ScheduleStatus.CANCELLED) {
                throw new IllegalStateException("Dose cancelada.");
            }
            // Nao altera registros anteriores nem aceita um ID de registro do cliente.
            if (records.findByPlannedDoseId(dose.getId()).isPresent()) {
                throw new IllegalStateException("Dose ja possui registro.");
            }
            RegistroDeDose record = new RegistroDeDose(null, dose.getId(), context.userId(),
                    Objects.requireNonNull(action), actualTime, clock.instant());
            return records.save(record);
        }
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException();
    }
}
