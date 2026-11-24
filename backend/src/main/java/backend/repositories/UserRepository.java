// Desenvolvido por Miguel Gallinucci.
package backend.repositories;

import backend.models.User;
import java.util.Optional;

/** Define as operações de persistência dos usuários, sem depender do MongoDB. */
public interface UserRepository {
    // O servico usa este contrato para nao depender do driver nem do formato dos documentos.
    // Insere um novo usuário, sem substituir um já existente.
    void save(User user);

    // Retorna vazio quando não existe usuário com o ID informado.
    // Optional e um recipiente que pode estar vazio; usei para representar usuario nao encontrado sem retornar null.
    // Usei Optional para diferenciar uma busca sem resultado de uma falha no banco.
    Optional<User> findById(String id);

    // Busca pelo e-mail; retorna vazio quando o usuário não é encontrado.
    Optional<User> findByEmail(String email);
}
