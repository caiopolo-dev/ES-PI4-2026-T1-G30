package backend.services;

import java.util.Locale;
import java.util.Objects;
import backend.repositories.UserRepository;
import backend.dto.LoginRequest;
import backend.models.User;



public class LoginService {

    private final UserRepository userRepository;

    public LoginService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    public User login(LoginRequest request){
        Objects.requireNonNull(request);

        String email= request.getEmail();
        if (email == null || email.isBlank()){
            throw new IllegalArgumentException("email não pode ser vazio.");
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


        User user = userRepository.findByEmail(emailNormalized).orElseThrow(InvalidCredentialsException::new);

        if (!"PBKDF2WithHmacSHA256".equals(user.getPasswordAlgorithm())) {
            throw new IllegalStateException("Algoritmo de senha não suportado.");
        }

        LoginHasher loginHasher = new LoginHasher();

        boolean senhaCorreta = loginHasher.verifyPassword(
            password.toCharArray(),
            user.getPasswordHash(),
            user.getPasswordSalt(),
            user.getPasswordIterations()
        );

        if (!senhaCorreta) {
            throw new InvalidCredentialsException();
        }


        return user;
                
        

    }

}