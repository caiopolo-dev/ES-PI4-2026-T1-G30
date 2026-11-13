
package backend.tests;

import backend.dto.LoginRequest;
import backend.handlers.LoginHandler;
import backend.http.HttpRequest;
import backend.http.HttpResponse;
import backend.models.User;
import backend.services.InvalidCredentialsException;
import backend.services.LoginService;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginHandlerTest {

    private LoginService loginService;
    private LoginHandler loginHandler;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        loginHandler = new LoginHandler(loginService);
    }

    private HttpRequest criarRequisicao(
        String contentType,
        String body
    ) {
        return new HttpRequest(
            "POST",
            "/auth/login",
            "HTTP/1.1",
            Map.of("Content-Type", contentType),
            body
        );
    }

    @Test
    void deveRetornar200QuandoLoginForValido() {
        User user = mock(User.class);

        when(user.getId()).thenReturn("user-123");
        when(user.getName()).thenReturn("Usuario Teste");
        when(user.getEmail())
            .thenReturn("teste@dosecerta.com");

        when(loginService.login(any(LoginRequest.class)))
            .thenReturn(user);

        HttpRequest request = criarRequisicao(
            "application/json",
            """
            {
                "email": "teste@dosecerta.com",
                "password": "SenhaTeste123!"
            }
            """
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(200, response.getStatusCode());

        Document json = Document.parse(response.getBody());

        assertEquals("user-123", json.getString("id"));
        assertEquals("Usuario Teste", json.getString("name"));
        assertEquals(
            "teste@dosecerta.com",
            json.getString("email")
        );

        assertFalse(json.containsKey("passwordHash"));
        assertFalse(json.containsKey("passwordSalt"));
        assertFalse(json.containsKey("password"));

        verify(loginService).login(any(LoginRequest.class));
    }

    @Test
    void deveRetornar401QuandoCredenciaisForemInvalidas() {
        when(loginService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException());

        HttpRequest request = criarRequisicao(
            "application/json",
            """
            {
                "email": "teste@dosecerta.com",
                "password": "SenhaErrada123!"
            }
            """
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(401, response.getStatusCode());

        assertTrue(
            response.getBody().contains("Credenciais inválidas")
        );
    }

    @Test
    void deveRetornar400QuandoJsonForInvalido() {
        HttpRequest request = criarRequisicao(
            "application/json",
            "{ json invalido }"
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(400, response.getStatusCode());

        verifyNoInteractions(loginService);
    }

    @Test
    void deveRetornar400QuandoEmailEstiverAusente() {
        HttpRequest request = criarRequisicao(
            "application/json",
            """
            {
                "password": "SenhaTeste123!"
            }
            """
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(400, response.getStatusCode());

        verifyNoInteractions(loginService);
    }

    @Test
    void deveRetornar400QuandoSenhaEstiverAusente() {
        HttpRequest request = criarRequisicao(
            "application/json",
            """
            {
                "email": "teste@dosecerta.com"
            }
            """
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(400, response.getStatusCode());

        verifyNoInteractions(loginService);
    }

    @Test
    void deveRetornar400QuandoContentTypeForInvalido() {
        HttpRequest request = criarRequisicao(
            "text/plain",
            """
            {
                "email": "teste@dosecerta.com",
                "password": "SenhaTeste123!"
            }
            """
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(400, response.getStatusCode());

        verifyNoInteractions(loginService);
    }

    @Test
    void deveRetornar400QuandoCorpoEstiverVazio() {
        HttpRequest request = criarRequisicao(
            "application/json",
            ""
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(400, response.getStatusCode());

        verifyNoInteractions(loginService);
    }

    @Test
    void deveRetornar400QuandoServiceRejeitarDados() {
        when(loginService.login(any(LoginRequest.class)))
            .thenThrow(new IllegalArgumentException());

        HttpRequest request = criarRequisicao(
            "application/json",
            """
            {
                "email": "email-invalido",
                "password": "SenhaTeste123!"
            }
            """
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void deveRetornar500QuandoOcorrerErroInterno() {
        when(loginService.login(any(LoginRequest.class)))
            .thenThrow(
                new IllegalStateException("Erro interno sensivel")
            );

        HttpRequest request = criarRequisicao(
            "application/json",
            """
            {
                "email": "teste@dosecerta.com",
                "password": "SenhaTeste123!"
            }
            """
        );

        HttpResponse response = loginHandler.handle(request);

        assertEquals(500, response.getStatusCode());

        // A mensagem interna nao deve ser exposta ao usuario.
        assertFalse(
            response.getBody().contains("Erro interno sensivel")
        );
    }
}
