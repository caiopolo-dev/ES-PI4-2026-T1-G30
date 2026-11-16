package backend.tests;

import backend.auth.AuthContext;
import backend.auth.ResourceNotFoundException;
import backend.repositories.MedicationInUseRepository;
import backend.services.MedicationInUseService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MedicationInUseServiceTest {
    private final AuthContext context = new AuthContext("a");

    @Test
    void allowsIncompleteDraftAndGeneratesMedicationId() {
        var repository = mock(MedicationInUseRepository.class);
        when(repository.findTreatmentStatus("t", "a")).thenReturn(Optional.of("DRAFT"));
        when(repository.addInState(eq("t"), eq("a"), any(), eq("DRAFT"))).thenReturn(true);
        var result = new MedicationInUseService(repository).add(context, "t", null, null, null, null, null);
        assertNotNull(result.getId());
        assertTrue(result.isActive());
        assertNull(result.getQuantity());
    }

    @Test
    void activeTreatmentRequiresCompleteMedicationBeforeWriting() {
        var repository = mock(MedicationInUseRepository.class);
        when(repository.findTreatmentStatus("t", "a")).thenReturn(Optional.of("ACTIVE"));
        var service = new MedicationInUseService(repository);
        assertThrows(IllegalStateException.class, () -> service.add(context, "t", "Demo", null, null, null, null));
        verify(repository, never()).addInState(anyString(), anyString(), any(), anyString());
    }

    @Test
    void deniesClosedUndefinedAndForeignTreatments() {
        var repository = mock(MedicationInUseRepository.class);
        var service = new MedicationInUseService(repository);
        when(repository.findTreatmentStatus("t", "a")).thenReturn(Optional.of("CLOSED"), Optional.of("UNDEFINED"), Optional.empty());
        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalStateException.class,
                () -> service.add(context, "t", "Demo", "50 mg", BigDecimal.ONE, "mL", null));
        }
        assertThrows(ResourceNotFoundException.class,
            () -> service.add(context, "t", "Demo", "50 mg", BigDecimal.ONE, "mL", null));
        verify(repository, never()).addInState(anyString(), anyString(), any(), anyString());
    }
}
