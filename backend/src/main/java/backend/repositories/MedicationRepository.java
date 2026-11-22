// Desenvolvido por Miguel Gallinucci.
package backend.repositories;

import backend.models.Medication;
import java.util.List;

/**
 * Define as operações de persistência dos medicamentos.
 */
public interface MedicationRepository {
    // Repository e a camada de acesso aos dados; usei interface como contrato para poder trocar a implementacao.
    // Defini a consulta na interface para o servico nao depender de onde os dados ficam guardados.
    List<Medication> findAll();
}
