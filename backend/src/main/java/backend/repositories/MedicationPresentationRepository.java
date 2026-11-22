package backend.repositories;

import backend.models.MedicationPresentation;
import java.util.List;

/**
 * Consulta apresentações do catálogo experimental sem alterar seus documentos.
 */
public interface MedicationPresentationRepository {
    List<MedicationPresentation> search(String query, int limit);
}
