package backend.repositories;

import backend.models.MedicationPresentation;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MedicationPresentationDocumentMappingTest {

    @Test
    void mapsExperimentalPresentationWithoutReusingPackageCountAsDose() {
        ObjectId id = new ObjectId();
        MedicationPresentation presentation = MongoMedicationPresentationRepository.fromDocument(
                new Document("_id", id)
                        .append("activeIngredient", "Losartana potássica")
                        .append("concentration", "50 mg")
                        .append("dosageForm", "comprimido revestido")
                        .append("manufacturer", "Fabricante experimental")
                        .append("packageType", "caixa")
                        .append("unitCount", 30)
        );

        assertEquals(id.toHexString(), presentation.getId());
        assertEquals("Losartana potássica", presentation.getName());
        assertEquals("50 mg", presentation.getConcentration());
        assertEquals("comprimido revestido", presentation.getDosageForm());
        assertEquals(30, presentation.getUnitCount());
    }

    @Test
    void keepsMissingFractionalAndNonPositivePackageCountsOutOfTheMappedValue() {
        MedicationPresentation missing = MongoMedicationPresentationRepository.fromDocument(
                new Document("_id", "a").append("name", "Medicamento A"));
        MedicationPresentation fractional = MongoMedicationPresentationRepository.fromDocument(
                new Document("_id", "b")
                        .append("name", "Medicamento B")
                        .append("unitCount", 1.5));
        MedicationPresentation zero = MongoMedicationPresentationRepository.fromDocument(
                new Document("_id", "c")
                        .append("name", "Medicamento C")
                        .append("unitCount", 0));

        assertNull(missing.getUnitCount());
        assertNull(fractional.getUnitCount());
        assertNull(zero.getUnitCount());
    }
}
