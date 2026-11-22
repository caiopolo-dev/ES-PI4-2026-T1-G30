// Desenvolvido por Miguel Gallinucci.
package backend.repositories;

import backend.models.Medication;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Mantém os medicamentos de exemplo em memória.
 */
public class InMemoryMedicationRepository implements MedicationRepository {
    // Usei dados em memoria para disponibilizar a listagem inicial sem precisar de banco.
    // Protegi a lista para quem consultar nao conseguir remover ou adicionar itens nela.
    private final List<Medication> medications = Collections.unmodifiableList(
        Arrays.asList(
            new Medication("1", "Losartana 50 mg"),
            new Medication("2", "Sinvastatina 20 mg")
        )
    );

    @Override
    public List<Medication> findAll() {
        // Posso devolver a lista cadastrada porque ela ja esta protegida contra alteracoes.
        return medications;
    }
}
