package backend.models;

import java.math.BigDecimal;
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
    private BigDecimal quantity;
    private String unit;
    private boolean active;
    private String observations;
    private final List<CronogramaDose> schedules;

    public MedicamentoEmUso(
            String id,
            String name,
            String dose,
            Integer quantity,
            String observations,
            List<CronogramaDose> schedules) {
        this(id, name, dose, quantity == null ? null : BigDecimal.valueOf(quantity),
            null, observations, true, schedules);
    }

    public MedicamentoEmUso(String id, String name, String dose, BigDecimal quantity,
            String unit, String observations, boolean active, List<CronogramaDose> schedules) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        setName(name);
        setDose(dose);
        setQuantity(quantity);
        setUnit(unit);
        setObservations(observations);
        this.active = active;
        this.schedules = new ArrayList<>();
        if (schedules != null) {
            for (CronogramaDose schedule : schedules) {
                if (schedule != null) { this.schedules.add(schedule); }
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
        this.name = optionalText(name, 200);
    }

    public String getDose() { return dose; }

    public void setDose(String dose) {
        this.dose = optionalText(dose, 200);
    }

    public BigDecimal getQuantity() { return quantity; }

    public void setQuantity(BigDecimal quantity) {
        if (quantity != null && quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva.");
        }
        this.quantity = quantity == null ? null : quantity.stripTrailingZeros();
    }

    // Mantem chamadas antigas com quantidades inteiras.
    public void setQuantity(int quantity) { setQuantity(BigDecimal.valueOf(quantity)); }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = optionalText(unit, 50); }
    public boolean isActive() { return active; }

    // Nao apaga cronogramas nem muda a identidade.
    public void deactivate() { this.active = false; }

    public String getObservations() { return observations; }
    public void setObservations(String observations) {
        this.observations = optionalText(observations, 2000);
    }

    // O servico chama esta validacao antes de ativar um medicamento no tratamento.
    public void validateForActivation() {
        if (name == null || dose == null || quantity == null || unit == null) {
            throw new IllegalStateException("Medicamento incompleto para ativacao.");
        }
    }

    private static String optionalText(String text, int maxLength) {
        if (text == null || text.isBlank()) { return null; }
        String normalized = text.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Texto excede o tamanho permitido.");
        }
        return normalized;
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
                && Objects.equals(unit, that.unit)
                && active == that.active
                && Objects.equals(observations, that.observations)
                && Objects.equals(schedules, that.schedules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, dose, quantity, unit, active, observations, schedules);
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

