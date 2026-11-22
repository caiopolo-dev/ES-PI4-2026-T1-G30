package backend.models;

/**
 * Representa uma apresentação experimental disponível para consulta.
 */
public class MedicationPresentation {
    private final String id;
    private final String name;
    private final String activeIngredient;
    private final String concentration;
    private final String dosageForm;
    private final String manufacturer;
    private final String packageType;
    private final Integer unitCount;

    public MedicationPresentation(
            String id,
            String name,
            String activeIngredient,
            String concentration,
            String dosageForm,
            String manufacturer,
            String packageType,
            Integer unitCount) {

        this.id = id;
        this.name = name;
        this.activeIngredient = activeIngredient;
        this.concentration = concentration;
        this.dosageForm = dosageForm;
        this.manufacturer = manufacturer;
        this.packageType = packageType;
        this.unitCount = unitCount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getActiveIngredient() {
        return activeIngredient;
    }

    public String getConcentration() {
        return concentration;
    }

    public String getDosageForm() {
        return dosageForm;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getPackageType() {
        return packageType;
    }

    public Integer getUnitCount() {
        return unitCount;
    }
}
