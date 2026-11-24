// Desenvolvido por Miguel Gallinucci.
package backend.models;

/** Representa os dados de um usuário, sem depender do MongoDB. */
public final class User {
    // Mantive os campos final para representar os dados recebidos sem altera-los depois da criacao.
    private final String id;
    private final String name;
    private final String email;
    // Separei o email original da chave normalizada usada para busca e unicidade no banco.
    private final String emailNormalized;

    // Recebe os dados da senha já processada, nunca a senha original.
    // Hash e o resultado do processamento da senha; armazeno esse resultado para permitir sua verificacao.
    private final String passwordHash;
    // Salt e um valor aleatorio usado no processamento para senhas iguais nao produzirem sempre o mesmo hash.
    private final String passwordSalt;
    // Guardei algoritmo e iteracoes para a autenticacao saber como verificar o hash armazenado.
    private final String passwordAlgorithm;
    // Iteracoes indicam quantas repeticoes o algoritmo aplica; guardo o valor usado para verificar a senha depois.
    private final int passwordIterations;

    public User(String id, String name, String email, String emailNormalized,
                String passwordHash, String passwordSalt, String passwordAlgorithm,
                int passwordIterations) {
        // Recebo os valores preparados: este modelo nao abre conexao nem executa a autenticacao.
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
