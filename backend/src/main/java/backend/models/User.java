package backend.models;

/** Representa os dados do usuário que serão persistidos no banco. */
public final class User {
    private final String id;
    private final String name;
    private final String email;
    private final String emailNormalized;

    // Guarda somente os dados da senha já processada, nunca a senha original.
    private final String passwordHash;
    private final String passwordSalt;
    private final String passwordAlgorithm;
    private final int passwordIterations;

    public User(String id, String name, String email, String emailNormalized,
                String passwordHash, String passwordSalt, String passwordAlgorithm,
                int passwordIterations) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.passwordAlgorithm = passwordAlgorithm;
        this.passwordIterations = passwordIterations;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public String getPasswordAlgorithm() {
        return passwordAlgorithm;
    }

    public int getPasswordIterations() {
        return passwordIterations;
    }
}
