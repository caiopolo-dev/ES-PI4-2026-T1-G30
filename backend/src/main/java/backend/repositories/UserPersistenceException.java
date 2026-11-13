package backend.repositories;

/** Representa falhas de persistência sem expor detalhes do MongoDB. */
public final class UserPersistenceException extends RuntimeException {
    public enum Reason {
        DUPLICATE_USER,
        INVALID_DOCUMENT,
        DATABASE_UNAVAILABLE,
        DATABASE_ERROR
    }

    private final Reason reason;

    public UserPersistenceException(Reason reason) {
        super(switch (reason) {
            case DUPLICATE_USER -> "Já existe um usuário com o ID ou e-mail informado.";
            case INVALID_DOCUMENT -> "Documento de usuário armazenado é inválido.";
            case DATABASE_UNAVAILABLE -> "Banco de usuários indisponível.";
            case DATABASE_ERROR -> "Não foi possível executar a operação no banco de usuários.";
        });
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
