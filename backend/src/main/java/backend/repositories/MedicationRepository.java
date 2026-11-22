// Desenvolvido por Miguel Gallinucci; findById adicionado por Caio Ferreira Polo na issue 40.
package backend.repositories;

import backend.models.Medication;
import java.util.List;
import java.util.Optional;

/**
 * Define as operações de persistência dos medicamentos.
 */
public interface MedicationRepository {
    // Repository e a camada de acesso aos dados; usei interface como contrato para poder trocar a implementacao.
    // Defini a consulta na interface para o servico nao depender de onde os dados ficam guardados.
    List<Medication> findAll();

    default Optional<Medication> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (Medication medication : findAll()) {
            if (id.trim().equals(medication.getId())) {
                return Optional.of(medication);
            }
        }
        return Optional.empty();
    }
}
