// Desenvolvido por Caio Ferreira Polo.
package backend.services;

import java.util.Objects;
import backend.repositories.UserRepository;
import backend.dto.RegisterRequest;
import java.util.Locale;
import java.util.Base64;
import java.util.UUID;
import backend.models.User;
import backend.repositories.UserPersistenceException;


/**
 * Aplica as regras de cadastro e envia o novo usuário ao repository.
 * Também prepara a senha para que ela nunca seja armazenada em texto puro.
 */
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }


    public User register(RegisterRequest request) {
        Objects.requireNonNull(request);

        // Nome, e-mail e senha são conferidos aqui para manter as regras
        // de cadastro separadas do handler HTTP.
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
        // A normalização permite comparar o mesmo e-mail mesmo com maiúsculas ou espaços.
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
            // Não permito duas contas associadas ao mesmo e-mail normalizado.
            throw new UserPersistenceException(UserPersistenceException.Reason.DUPLICATE_USER);
        }


        // O salt é aleatório para cada conta. O banco recebe somente o salt e o hash,
        // nunca a senha original informada pelo usuário.
        PasswordHasher passwordHasher = new PasswordHasher(); //objeto da classe PasswordHasher
        byte[] salt = passwordHasher.generateSalt(); // Gera um salt aleatório de 16 bytes
        byte[] hashedPassword = passwordHasher.hashPassword(password.toCharArray(), salt); // Criptografa usando o salt gerado + senha 
        String saltBase64 = Base64.getEncoder().encodeToString(salt); // Converte o salt para string
        String hashedPasswordBase64 = Base64.getEncoder().encodeToString(hashedPassword); // Converte o hash senha criptografada para string

        String id = UUID.randomUUID().toString();

        // Registro junto ao usuário quais parâmetros foram usados no hash,
        // pois o login precisará deles para verificar a senha futuramente.
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

        // O repository cuida dos detalhes de persistência no MongoDB.
        userRepository.save(user);
        return user;


    }


}
