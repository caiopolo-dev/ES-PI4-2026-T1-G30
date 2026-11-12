package backend.services;

import java.util.Objects;
import backend.repositories.UserRepository;
import backend.dto.RegisterRequest;
import java.util.Locale;
import java.util.Base64;
import java.util.UUID;
import backend.models.User;
import backend.repositories.UserPersistenceException;


public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }


    public User register(RegisterRequest request) {
        Objects.requireNonNull(request);

        String name = request.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome não pode ser vazio.");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Nome muito longo.");
        }
        


        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-mail não pode ser vazio.");
        }
        String emailNormalized = email.trim().toLowerCase(Locale.ROOT);
        if (!emailNormalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Formato de e-mail inválido.");
        }
        if (emailNormalized.length() > 254) {
            throw new IllegalArgumentException("E-mail muito longo.");
        }



        String password = request.getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Senha não pode ser vazia.");
        }

        if (password.length() < 8 || password.length() > 128) {
           throw new IllegalArgumentException("Senha deve ter entre 8 e 128 caracteres.");
        }

        if (userRepository.findByEmail(emailNormalized).isPresent()) {
            throw new UserPersistenceException(UserPersistenceException.Reason.DUPLICATE_USER);
        }


        PasswordHasher passwordHasher = new PasswordHasher(); //objeto da classe PasswordHasher
        byte[] salt = passwordHasher.generateSalt(); // Gera um salt aleatório de 16 bytes
        byte[] hashedPassword = passwordHasher.hashPassword(password.toCharArray(), salt); // Criptografa usando o salt gerado + senha 
        String saltBase64 = Base64.getEncoder().encodeToString(salt); // Converte o salt para string
        String hashedPasswordBase64 = Base64.getEncoder().encodeToString(hashedPassword); // Converte o hash senha criptografada para string

        String id = UUID.randomUUID().toString();

        User user = new User(
            id,
            name,
            email,
            emailNormalized,
            hashedPasswordBase64,
            saltBase64,
            "PBKDF2WithHmacSHA256",
            PasswordHasher.ITERATIONS
        );

        userRepository.save(user);
        return user;


    }


}