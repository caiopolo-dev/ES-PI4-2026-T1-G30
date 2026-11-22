// Desenvolvido por Miguel Gallinucci.
package backend.services;

import backend.models.User;
import backend.repositories.UserRepository;
import java.util.Objects;
import java.util.Optional;

/** Acessa os usuários pelo contrato de persistência. */
public final class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        // Recebi a interface para usar o mesmo servico com MongoDB ou com um repository de teste.
        this.repository = Objects.requireNonNull(repository, "Repository de usuários deve ser informado.");
    }

    // Recebe os dados já preparados, sem gerar ou verificar senhas.
    // A persistencia fica no repository; este servico nao precisa conhecer Document ou comandos MongoDB.
    public void save(User user) {
        repository.save(user);
    }

    public Optional<User> findById(String id) {
        // O ID identifica o cadastro; mantenho o Optional vazio quando o repository nao encontra esse usuario.
        return repository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        // Deleguei a busca para o repository aplicar a mesma normalizacao usada na persistencia.
        return repository.findByEmail(email);
    }
}
