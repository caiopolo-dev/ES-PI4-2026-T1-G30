package backend.repositories;

import backend.models.User;
import java.util.Optional;

/** Define as operações de persistência dos usuários, sem depender do MongoDB. */
public interface UserRepository {
    // Insere um novo usuário, sem substituir um já existente.
    void save(User user);

    // Retorna vazio quando não existe usuário com o ID informado.
    Optional<User> findById(String id);

    // Busca pelo e-mail; retorna vazio quando o usuário não é encontrado.
    Optional<User> findByEmail(String email);
}
