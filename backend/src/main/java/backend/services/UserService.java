package backend.services;

import backend.models.User;
import backend.repositories.UserRepository;
import java.util.Objects;
import java.util.Optional;

/** Acessa os usuários pelo contrato de persistência. */
public final class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Repository de usuários deve ser informado.");
    }

    // Recebe os dados já preparados, sem gerar ou verificar senhas.
    public void save(User user) {
        repository.save(user);
    }

    public Optional<User> findById(String id) {
        return repository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }
}
