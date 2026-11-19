// Autores: Cauã Bianchi Ferroni, Samuel Campovilla
package backend.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Representa um tratamento médico associado a um usuário específico (titular).
 * Agrega os medicamentos em uso (MedicamentoEmUso) e suas respectivas versões
 * de cronograma (CronogramaDose).
 *
 * Alteração importante: Uso de um novo ENUM para o status: TreatmentStatus
 *  * DRAFT  - Tratamento em elaboração, podendo estar incompleto.
 *  * ACTIVE - Tratamento validado e em andamento.
 *  * CLOSED - Tratamento encerrado, com histórico preservado.
 *
 * As transições seguem o fluxo DRAFT -> ACTIVE -> CLOSED.
 * Tratamentos encerrados não podem ser reativados.
 *
 *
 */
public class Treatment {
    private final String id;
    private final String userId;

    private String name;
    private String description;
    // startDate e endDate representam o período planejado.
    private LocalDate startDate;
    private LocalDate endDate;

    private TreatmentStatus status;
    private Instant closedAt; // closedAt registra o instante efetivo de encerramento.

    private final List<MedicamentoEmUso> medications;



    public Treatment(
            String id,
            String userId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            TreatmentStatus status,
            Instant closedAt,
            List<MedicamentoEmUso> medications) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Usuário inválido");
        }

        if (status == null) {
            throw new IllegalArgumentException("Status obrigatório");
        }

        if (startDate != null && endDate != null
                && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Período inválido");
        }

        if (status != TreatmentStatus.DRAFT
                && (name == null || name.isBlank())) {
            throw new IllegalArgumentException(
                    "Tratamento ativo ou encerrado deve possuir nome");
        }

        if (status == TreatmentStatus.CLOSED && closedAt == null) {
            throw new IllegalArgumentException(
                    "Tratamento encerrado deve possuir closedAt");
        }

        if (status != TreatmentStatus.CLOSED && closedAt != null) {
            throw new IllegalArgumentException(
                    "Somente tratamentos encerrados possuem closedAt");
        }

        this.id = (id == null || id.isBlank())
                ? UUID.randomUUID().toString()
                : id.trim();

        this.userId = userId.trim();
        this.name = name == null ? null : name.trim();
        this.description = description == null ? null : description.trim();

        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.closedAt = closedAt;

        this.medications = new ArrayList<>();

        if (medications != null) {
            for (MedicamentoEmUso medication : medications) {
                if (medication != null) {
                    this.medications.add(medication);
                }
            }
        }
    }


    // Construtor secundário para criar novos rascunhos
    public Treatment(
            String userId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate) {

        this(
                null,
                userId,
                name,
                description,
                startDate,
                endDate,
                TreatmentStatus.DRAFT,
                null,
                Collections.emptyList()
        );
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }


    public void setName(String name) {
        validarAlteracao();

        if (status == TreatmentStatus.ACTIVE && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Tratamento ativo deve possuir nome");
        }

        this.name = name == null ? null : name.trim();
    }

    // Verifica se o tratamento ainda permite alterações.
    // Tratamentos encerrados devem preservar seus dados e histórico.
    private void validarAlteracao() {
        if (status == TreatmentStatus.CLOSED) {
            throw new IllegalStateException("Tratamento encerrado não pode ser alterado");
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        // Verifica se o tratamento permite alterações
        validarAlteracao();

        this.description = description != null ? description.trim() : null;
    }


    public List<MedicamentoEmUso> getMedications() {
        return Collections.unmodifiableList(medications);
    }

    public void addMedication(MedicamentoEmUso medication) {
        // Não permite incluir ou atualizar medicamentos em tratamentos encerrados
        validarAlteracao();

        Objects.requireNonNull(medication, "medication não pode ser null");
        for (int i = 0; i < medications.size(); i++) {
            if (medications.get(i).getId().equals(medication.getId())) {
                medications.set(i, medication);
                return;
            }
        }
        medications.add(medication);
    }

    public Optional<MedicamentoEmUso> findMedicationById(String medicationId) {
        if (medicationId == null) {
            return Optional.empty();
        }
        for (MedicamentoEmUso medication : medications) {
            if (medicationId.equals(medication.getId())) {
                return Optional.of(medication);
            }
        }
        return Optional.empty();
    }



     // Encerra um tratamento que está ativo.
     // O encerramento não apaga os medicamentos nem seus cronogramas.
     // Apenas altera o status e registra quando ocorreu.
    public void close(Instant closedAt) {

        // Somente tratamentos ativos podem ser encerrados
        if (status != TreatmentStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Somente tratamentos ativos podem ser encerrados");
        }

        // O instante do encerramento é obrigatório
        if (closedAt == null) {
            throw new IllegalArgumentException(
                    "Data de encerramento inválida");
        }

        this.status = TreatmentStatus.CLOSED;
        this.closedAt = closedAt;
    }




     // Ativa um tratamento que está em DRAFT.
     // Antes da ativação, verifica se o tratamento possui
     // as informações necessárias para iniciar.
    public void activate() {

        // Somente tratamentos em DRAFT podem ser ativados
        if (status != TreatmentStatus.DRAFT) {
            throw new IllegalStateException("Somente tratamentos em DRAFT podem ser ativados");
        }

        // O nome é obrigatório para ativar o tratamento
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O tratamento deve possuir um nome");
        }

        // O tratamento precisa possuir uma data de início
        if (startDate == null) {
            throw new IllegalArgumentException("A data de início é obrigatória");
        }

        // A data final não pode anteceder a inicial
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("A data final não pode anteceder a inicial");
        }

        // Verifica se existe pelo menos um medicamento com cronograma ativo
        boolean possuiCronogramaAtivo = false;

        for (MedicamentoEmUso medication : medications) {

            // Verifica se o medicamento possui um cronograma ativo
            if (medication.getActiveSchedule().isPresent()) {
                possuiCronogramaAtivo = true;
                break;
            }
        }

        if (!possuiCronogramaAtivo) {
            throw new IllegalArgumentException("O tratamento precisa de um medicamento com cronograma ativo");
        }

        // Todas as verificações passaram, então o tratamento é ativado
        this.status = TreatmentStatus.ACTIVE;
    }




    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Treatment)) return false;

        Treatment treatment = (Treatment) o;

        return Objects.equals(id, treatment.id)
                && Objects.equals(userId, treatment.userId)
                && Objects.equals(name, treatment.name)
                && Objects.equals(description, treatment.description)
                && Objects.equals(startDate, treatment.startDate)
                && Objects.equals(endDate, treatment.endDate)
                && status == treatment.status
                && Objects.equals(closedAt, treatment.closedAt)
                && Objects.equals(medications, treatment.medications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, userId, name, description,
                startDate, endDate, status, closedAt, medications
        );
    }


    @Override
    public String toString() {
        return "Treatment{"
                + "id='" + id + '\''
                + ", userId='" + userId + '\''
                + ", name='" + name + '\''
                + ", description='" + description + '\''
                + ", startDate=" + startDate
                + ", endDate=" + endDate
                + ", status=" + status
                + ", closedAt=" + closedAt
                + ", medications=" + medications
                + '}';
    }

    public TreatmentStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

}

