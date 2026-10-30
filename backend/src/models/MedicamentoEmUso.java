package backend.src.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Representa um medicamento em uso dentro de um tratamento.
 * Possui identidade própria (id), dados informados manualmente pelo usuário
 * (sem dependência obrigatória de CMED ou MedicationPresentation) e histórico
 * versionado de cronogramas (CronogramaDose).
 */
public class MedicamentoEmUso {
    private final String id;
    private String name;
    private String dose;
    private Integer quantity;
    private String observations;
    private final List<CronogramaDose> schedules;

    public MedicamentoEmUso(
            String id,
            String name,
            String dose,
            Integer quantity,
            String observations,
            List<CronogramaDose> schedules) {
        Objects.requireNonNull(name, "name não pode ser null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("name do medicamento não pode ser vazio");
        }
        Objects.requireNonNull(dose, "dose não pode ser null");
        if (dose.trim().isEmpty()) {
            throw new IllegalArgumentException("dose do medicamento não pode ser vazia");
        }
        if (quantity != null && quantity < 0) {
            throw new IllegalArgumentException("quantity não pode ser negativa");
        }

        this.id = (id == null || id.trim().isEmpty())
                ? UUID.randomUUID().toString()
                : id.trim();
        this.name = name.trim();
        this.dose = dose.trim();
        this.quantity = quantity;
        this.observations = observations != null ? observations.trim() : null;
        this.schedules = new ArrayList<>();

        if (schedules != null) {
            for (CronogramaDose schedule : schedules) {
                if (schedule != null) {
                    this.schedules.add(schedule);
                }
            }
        }
    }

    public MedicamentoEmUso(
            String id,
            String name,
            String dose,
            Integer quantity,
            String observations,
            CronogramaDose initialSchedule) {
        this(
                id,
                name,
                dose,
                quantity,
                observations,
                initialSchedule != null ? Collections.singletonList(initialSchedule) : Collections.emptyList()
        );
    }

    public MedicamentoEmUso(
            String id,
            String name,
            String dose,
            Integer quantity,
            String observations) {
        this(id, name, dose, quantity, observations, Collections.emptyList());
    }

    public MedicamentoEmUso(String id, String name, String dose, CronogramaDose initialSchedule) {
        this(id, name, dose, 1, null, initialSchedule);
    }

    public MedicamentoEmUso(String name, String dose, CronogramaDose initialSchedule) {
        this(null, name, dose, 1, null, initialSchedule);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        Objects.requireNonNull(name, "name não pode ser null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("name do medicamento não pode ser vazio");
        }
        this.name = name.trim();
    }

    public String getDose() {
        return dose;
    }

    public void setDose(String dose) {
        Objects.requireNonNull(dose, "dose não pode ser null");
        if (dose.trim().isEmpty()) {
            throw new IllegalArgumentException("dose do medicamento não pode ser vazia");
        }
        this.dose = dose.trim();
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        if (quantity != null && quantity < 0) {
            throw new IllegalArgumentException("quantity não pode ser negativa");
        }
        this.quantity = quantity;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations != null ? observations.trim() : null;
    }

    /**
     * Retorna todas as versões do cronograma (ativas e históricas) em lista imutável.
     */
    public List<CronogramaDose> getSchedules() {
        return Collections.unmodifiableList(schedules);
    }

    /**
     * Retorna a versão atualmente ativa do cronograma de doses, se houver.
     */
    public Optional<CronogramaDose> getActiveSchedule() {
        for (int i = schedules.size() - 1; i >= 0; i--) {
            CronogramaDose schedule = schedules.get(i);
            if (schedule.isActive()) {
                return Optional.of(schedule);
            }
        }
        return Optional.empty();
    }

    /**
     * Busca uma versão específica do cronograma pelo número da versão (ex: 1, 2).
     */
    public Optional<CronogramaDose> getScheduleByVersion(int version) {
        for (CronogramaDose schedule : schedules) {
            if (schedule.getVersion() == version) {
                return Optional.of(schedule);
            }
        }
        return Optional.empty();
    }

    /**
     * Adiciona uma nova versão de cronograma preservando todas as versões anteriores.
     * Caso o novo cronograma seja ativo, as versões anteriores são marcadas como inativas
     * sem serem removidas, e a numeração de versão é garantida como sequencial.
     */
    public CronogramaDose addScheduleVersion(CronogramaDose newSchedule) {
        Objects.requireNonNull(newSchedule, "newSchedule não pode ser null");

        int maxVersion = 0;
        for (CronogramaDose existing : schedules) {
            if (existing.getVersion() > maxVersion) {
                maxVersion = existing.getVersion();
            }
        }

        if (newSchedule.isActive()) {
            for (int i = 0; i < schedules.size(); i++) {
                CronogramaDose existing = schedules.get(i);
                if (existing.isActive()) {
                    schedules.set(i, existing.deactivate());
                }
            }
        }

        int targetVersion = newSchedule.getVersion() > maxVersion
                ? newSchedule.getVersion()
                : maxVersion + 1;
        CronogramaDose versionedSchedule = newSchedule.withVersion(targetVersion);
        schedules.add(versionedSchedule);
        return versionedSchedule;
    }

    /**
     * Atualiza o cronograma ativo criando uma nova versão (ex: v1 -> v2) e mantendo
     * o cronograma anterior preservado no histórico como inativo.
     */
    public CronogramaDose updateSchedule(
            List<String> newDaysOfWeek,
            List<String> newTimes,
            String newStartDate,
            String newEndDate,
            String newTimezone) {
        Optional<CronogramaDose> currentOpt = getActiveSchedule();
        String resolvedTimezone = newTimezone;
        if ((resolvedTimezone == null || resolvedTimezone.trim().isEmpty()) && currentOpt.isPresent()) {
            resolvedTimezone = currentOpt.get().getTimezone();
        }
        if (resolvedTimezone == null || resolvedTimezone.trim().isEmpty()) {
            resolvedTimezone = "America/Sao_Paulo";
        }

        int nextVer = 1;
        for (CronogramaDose s : schedules) {
            if (s.getVersion() >= nextVer) {
                nextVer = s.getVersion() + 1;
            }
        }

        CronogramaDose next = new CronogramaDose(
                nextVer,
                newDaysOfWeek,
                newTimes,
                newStartDate,
                newEndDate,
                resolvedTimezone,
                true
        );
        return addScheduleVersion(next);
    }

    /**
     * Atualiza os horários e data de início gerando uma nova versão do cronograma
     * e preservando a versão anterior no histórico.
     */
    public CronogramaDose updateScheduleTimes(List<String> newTimes, String newStartDate) {
        Optional<CronogramaDose> currentOpt = getActiveSchedule();
        List<String> daysOfWeek = currentOpt.map(CronogramaDose::getDaysOfWeek).orElse(null);
        String endDate = currentOpt.map(CronogramaDose::getEndDate).orElse(null);
        String timezone = currentOpt.map(CronogramaDose::getTimezone).orElse("America/Sao_Paulo");
        return updateSchedule(daysOfWeek, newTimes, newStartDate, endDate, timezone);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MedicamentoEmUso)) {
            return false;
        }
        MedicamentoEmUso that = (MedicamentoEmUso) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(dose, that.dose)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(observations, that.observations)
                && Objects.equals(schedules, that.schedules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, dose, quantity, observations, schedules);
    }

    @Override
    public String toString() {
        return "MedicamentoEmUso{"
                + "id='" + id + '\''
                + ", name='" + name + '\''
                + ", dose='" + dose + '\''
                + ", quantity=" + quantity
                + ", observations='" + observations + '\''
                + ", schedules=" + schedules
                + '}';
    }
}

