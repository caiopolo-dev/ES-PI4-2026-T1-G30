
package backend.tests;

import backend.repositories.TreatmentRepository;
import backend.services.DoseGenerationService;
import backend.services.DoseReconciliationJob;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class DoseReconciliationJobTest {

    @Test
    void deveContinuarReconciliaçãoAposFalha() {

        TreatmentRepository repository = mock(TreatmentRepository.class);

        DoseGenerationService service = mock(DoseGenerationService.class);

        when(repository.findDoseReconciliationTargets())
                .thenReturn(List.of(
                        new TreatmentRepository.ReconciliationTarget(
                                "user-1", "trat-1"),
                        new TreatmentRepository.ReconciliationTarget(
                                "user-2", "trat-2")
                ));

        // Simula uma falha na primeira tentativa e sucesso na segunda.
        when(service.reconcile("user-1", "trat-1"))
                .thenThrow(new RuntimeException("Falha simulada"))
                .thenReturn(List.of());

        try (DoseReconciliationJob job =
                     new DoseReconciliationJob(repository, service)) {

            job.runOnce();
            job.runOnce();

            // O primeiro tratamento deve receber uma nova tentativa.
            verify(service, times(2)).reconcile("user-1", "trat-1");

            // A falha do primeiro não deve impedir o processamento do segundo.
            verify(service, times(2)).reconcile("user-2", "trat-2");
        }
    }
}
