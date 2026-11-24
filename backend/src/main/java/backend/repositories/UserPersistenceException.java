// Desenvolvido por Miguel Gallinucci.
package backend.repositories;

/** Representa falhas de persistência sem expor detalhes do MongoDB. */
public final class UserPersistenceException extends RuntimeException {
    // Usei motivos fixos para quem recebe a excecao nao precisar comparar textos de erro.
    public enum Reason {
        // enum define um conjunto fixo de valores; usei para identificar os tipos de falha sem comparar mensagens.
        DUPLICATE_USER,
        INVALID_DOCUMENT,
        DATABASE_UNAVAILABLE,
        DATABASE_ERROR
    }

    private final Reason reason;

    public UserPersistenceException(Reason reason) {
        // Cada motivo recebe uma mensagem conhecida, sem copiar o erro interno do driver.
        super(switch (reason) {
            case DUPLICATE_USER -> "Já existe um usuário com o ID ou e-mail informado.";
            case INVALID_DOCUMENT -> "Documento de usuário armazenado é inválido.";
            case DATABASE_UNAVAILABLE -> "Banco de usuários indisponível.";
            case DATABASE_ERROR -> "Não foi possível executar a operação no banco de usuários.";
        });
        this.reason = reason;
    }

    public Reason getReason() {
        // Permite identificar o tipo da falha e decidir como responder a ela.
        return reason;
    }
}
