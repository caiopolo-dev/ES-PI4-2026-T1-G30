package backend.src.models;

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
 */
public class Treatment {
    private final String id;
    private final String userId;
    private String name;
    private String description;
    private boolean active;
    private final List<MedicamentoEmUso> medications;

    public Treatment(
            String id,
            String userId,
            String name,
            String description,
            boolean active,
            List<MedicamentoEmUso> medications) {
        Objects.requireNonNull(userId, "userId não pode ser null");
        if (userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId do titular do tratamento não pode ser vazio");
        }
        Objects.requireNonNull(name, "name não pode ser null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("name do tratamento não pode ser vazio");
        }

        this.id = (id == null || id.trim().isEmpty())
                ? UUID.randomUUID().toString()
                : id.trim();
        this.userId = userId.trim();
        this.name = name.trim();
        this.description = description != null ? description.trim() : null;
        this.active = active;
        this.medications = new ArrayList<>();

        if (medications != null) {
            for (MedicamentoEmUso medication : medications) {
                if (medication != null) {
                    this.medications.add(medication);
                }
            }
        }
    }

    public Treatment(
            String id,
            String userId,
            String name,
            List<MedicamentoEmUso> medications) {
        this(id, userId, name, null, true, medications);
    }

    public Treatment(String id, String userId, String name) {
        this(id, userId, name, null, true, Collections.emptyList());
    }

    public Treatment(String userId, String name) {
        this(null, userId, name, null, true, Collections.emptyList());
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
        Objects.requireNonNull(name, "name não pode ser null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("name do tratamento não pode ser vazio");
        }
        this.name = name.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : null;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<MedicamentoEmUso> getMedications() {
        return Collections.unmodifiableList(medications);
    }

    public void addMedication(MedicamentoEmUso medication) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Treatment)) {
            return false;
        }
        Treatment treatment = (Treatment) o;
        return active == treatment.active
                && Objects.equals(id, treatment.id)
                && Objects.equals(userId, treatment.userId)
                && Objects.equals(name, treatment.name)
                && Objects.equals(description, treatment.description)
                && Objects.equals(medications, treatment.medications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, name, description, active, medications);
    }

    @Override
    public String toString() {
        return "Treatment{"
                + "id='" + id + '\''
                + ", userId='" + userId + '\''
                + ", name='" + name + '\''
                + ", description='" + description + '\''
                + ", active=" + active
                + ", medications=" + medications
                + '}';
    }
}

