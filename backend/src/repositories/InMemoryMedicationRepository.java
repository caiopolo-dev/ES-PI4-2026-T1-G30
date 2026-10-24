package backend.src.repositories;

import backend.src.models.Medication;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Mantém os medicamentos de exemplo em memória.
 */
public class InMemoryMedicationRepository implements MedicationRepository {
    private final List<Medication> medications = Collections.unmodifiableList(
        Arrays.asList(
            new Medication("1", "Losartana 50 mg"),
            new Medication("2", "Sinvastatina 20 mg")
        )
    );

    @Override
    public List<Medication> findAll() {
        return medications;
    }
}
