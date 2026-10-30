package backend.src.models;

/**
 * Representa um medicamento com identificador e nome.
 */
public class Medication {
    private final String id;
    private final String name;

    public Medication(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
