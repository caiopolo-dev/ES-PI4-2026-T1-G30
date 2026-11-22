//Caio Ferreira Polo issue 40
package backend.models;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Representa uma apresentacao comercial exata, sem substituir Medication. */
public final class MedicationPresentation {
    private final String id;
    private final String activeIngredient;
    private final String concentration;
    private final String dosageForm;
    private final int unitCount;
    private final String packageType;
    private final String manufacturer;
    private final Map<String, String> regulatoryIdentifiers;

    public MedicationPresentation(
            String id,
            String activeIngredient,
            String concentration,
            String dosageForm,
            int unitCount,
            String packageType,
            String manufacturer,
            Map<String, String> regulatoryIdentifiers) {
        this.id = required(id, "id");
        this.activeIngredient = required(activeIngredient, "activeIngredient");
        this.concentration = required(concentration, "concentration");
        this.dosageForm = required(dosageForm, "dosageForm");
        if (unitCount <= 0) {
            throw new IllegalArgumentException("unitCount deve ser positivo");
        }
        this.unitCount = unitCount;
        this.packageType = required(packageType, "packageType");
        this.manufacturer = required(manufacturer, "manufacturer");
        this.regulatoryIdentifiers = copyIdentifiers(regulatoryIdentifiers);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " nao pode ser null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " nao pode ser vazio");
        }
        return normalized;
    }

    private static Map<String, String> copyIdentifiers(Map<String, String> identifiers) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (identifiers != null) {
            for (Map.Entry<String, String> entry : identifiers.entrySet()) {
                String key = required(entry.getKey(), "identifier key");
                String value = required(entry.getValue(), "identifier value");
                copy.put(key, value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public String getId() {
        return id;
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

    public int getUnitCount() {
        return unitCount;
    }

    public String getPackageType() {
        return packageType;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public Map<String, String> getRegulatoryIdentifiers() {
        return regulatoryIdentifiers;
    }

    public String getDisplayName() {
        return activeIngredient + " " + concentration + " " + manufacturer
                + " - " + unitCount + " " + dosageForm;
    }
}
