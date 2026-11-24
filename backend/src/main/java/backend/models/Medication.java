// Desenvolvido por Miguel Gallinucci.
package backend.models;

/**
 * Representa um medicamento com identificador e nome.
 */
public class Medication {
    // Usei final porque os dados deste modelo so precisam ser consultados depois da criacao.
    private final String id;
    // final impede reatribuir o campo; aqui mantem o nome definido no construtor.
    private final String name;

    public Medication(String id, String name) {
        // O modelo guarda os dados; a montagem da resposta HTTP fica no handler.
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
