
package backend.tests;

import backend.dto.LoginRequest;
import backend.models.User;
import backend.repositories.UserRepository;
import backend.services.LoginService;
import backend.services.LoginHasher;
import backend.services.InvalidCredentialsException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginServiceTest {

    private UserRepository userRepository;
    private LoginService loginService;

    private static final String EMAIL = "teste@dosecerta.com";
    private static final String PASSWORD = "SenhaTeste123!";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        loginService = new LoginService(userRepository);
    }

    // Cria um usuario simulado com uma senha armazenada como hash.
    private User criarUsuarioComSenha(String password) {
        User user = mock(User.class);

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        // Valor reduzido apenas para acelerar os testes.
        int iterations = 1000;

        LoginHasher hasher = new LoginHasher();

        byte[] hash = hasher.hashPassword(
            password.toCharArray(),
            salt,
            iterations
        );

        when(user.getPasswordAlgorithm())
            .thenReturn("PBKDF2WithHmacSHA256");

        when(user.getPasswordSalt())
            .thenReturn(Base64.getEncoder().encodeToString(salt));

        when(user.getPasswordHash())
            .thenReturn(Base64.getEncoder().encodeToString(hash));

        when(user.getPasswordIterations())
            .thenReturn(iterations);

        return user;
    }

    @Test
    void deveRealizarLoginComSenhaCorreta() {
        User user = criarUsuarioComSenha(PASSWORD);

        when(userRepository.findByEmail(EMAIL))
            .thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest(
            EMAIL,
            PASSWORD
        );

        User resultado = loginService.login(request);

        assertSame(user, resultado);
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void deveRejeitarSenhaIncorreta() {
        User user = criarUsuarioComSenha(PASSWORD);

        when(userRepository.findByEmail(EMAIL))
            .thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest(
            EMAIL,
            "SenhaErrada123!"
        );

        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login(request)
        );
    }

    @Test
    void deveRejeitarEmailInexistente() {
        when(userRepository.findByEmail(EMAIL))
            .thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest(
            EMAIL,
            PASSWORD
        );

        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login(request)
        );
    }

    @Test
    void deveNormalizarEmailAntesDaConsulta() {
        User user = criarUsuarioComSenha(PASSWORD);

        when(userRepository.findByEmail(EMAIL))
            .thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest(
            "  TESTE@DOSECERTA.COM  ",
            PASSWORD
        );

        User resultado = loginService.login(request);

        assertSame(user, resultado);
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void deveRejeitarEmailInvalido() {
        LoginRequest request = new LoginRequest(
            "email-invalido",
            PASSWORD
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> loginService.login(request)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void deveRejeitarEmailVazio() {
        LoginRequest request = new LoginRequest(
            " ",
            PASSWORD
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> loginService.login(request)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void deveRejeitarSenhaVazia() {
        LoginRequest request = new LoginRequest(
            EMAIL,
            ""
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> loginService.login(request)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void deveRejeitarSenhaMuitoCurta() {
        LoginRequest request = new LoginRequest(
            EMAIL,
            "123"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> loginService.login(request)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void deveRejeitarAlgoritmoNaoSuportado() {
        User user = mock(User.class);

        when(user.getPasswordAlgorithm())
            .thenReturn("AlgoritmoInvalido");

        when(userRepository.findByEmail(EMAIL))
            .thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest(
            EMAIL,
            PASSWORD
        );

        assertThrows(
            IllegalStateException.class,
            () -> loginService.login(request)
        );
    }
}
