package backend.tests;

import backend.http.HttpRequest;
import backend.http.HttpRequestParser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

// Teste do Parser usando JUnit
public class HttpRequestParserTest {

    // Testa requisição válida
    @Test
    void parsesValidGetRequest() throws Exception {
        // Cria uma rawRequest para usar de modelo para os testes
        String rawRequest =
                "GET /health HTTP/1.1\r\n" +
                        "Host: localhost:8080\r\n" +
                        "Accept: application/json\r\n" +
                        "\r\n";
        // Converte os bytes para passar no método parse
        ByteArrayInputStream input = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.US_ASCII));

        // Executa o método parse
        HttpRequestParser parser = new HttpRequestParser();
        HttpRequest request = parser.parse(input);
        // Verifica os resultados
        assertEquals("GET", request.getMethod());
        assertEquals("/health", request.getPath());
        assertEquals("HTTP/1.1", request.getHttpVersion());
        assertEquals("localhost:8080", request.getHeaders().get("host"));
    }

    // Testa uma requisição malformada, sem a versão HTTP
    @Test
    void rejectsMissingHttpVersion() throws Exception {
        String rawRequest =
                "GET /health\r\n" +
                        "Host: localhost:8080\r\n" +
                        "\r\n";

        ByteArrayInputStream input = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.UTF_8));

        HttpRequestParser parser = new HttpRequestParser();


        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            parser.parse(input);
        });

        assertEquals("Invalid request line", exception.getMessage());
    }

    // Testar pegar o body
    @Test
    void parsesRequestBody() throws Exception {
        String rawRequest =
                "POST /medications HTTP/1.1\r\n" +
                        "Host: localhost:8080\r\n" +
                        "Content-Length: 5\r\n" +
                        "\r\n" +
                        "hello";

        // Converte os bytes para passar no método parse
        ByteArrayInputStream input = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.US_ASCII));

        // Executa o método parse
        HttpRequestParser parser = new HttpRequestParser();
        HttpRequest request = parser.parse(input);

        assertEquals("POST", request.getMethod());
        assertEquals("hello", request.getBody());
    }

    // Testar header malformado
    @Test
    void rejectsMalformedHeader() throws Exception {
        String rawRequest =
                "GET /health HTTP/1.1\r\n" +
                        "Host localhost8080\r\n" + // Erro está aqui! (sem :)
                        "Accept: application/json\r\n" +
                        "\r\n";

        ByteArrayInputStream input = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.UTF_8));

        HttpRequestParser parser = new HttpRequestParser();


        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            parser.parse(input);
        });

        assertEquals("Invalid header", exception.getMessage()); // Olhar assert do método de baixo
    }
    // Testa a falha no REGEX
    @Test
    void rejectsInvalidHeaderName() {
        String rawRequest =
                "GET /health HTTP/1.1\r\n" +
                        "Host localhost:8080\r\n" + // Erro está aqui! (sem apenas um -> ":" -> FALHA NO REGEX)
                        "Accept: application/json\r\n" +
                        "\r\n";

        ByteArrayInputStream input = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.UTF_8));

        HttpRequestParser parser = new HttpRequestParser();


        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            parser.parse(input);
        });

        assertEquals("Invalid or duplicate header", exception.getMessage());
    }

    // Testar body incompleto.
    @Test
    void rejectsIncompleteBody() throws Exception {
        String rawRequest =
                "POST /medications HTTP/1.1\r\n" +
                        "Host: localhost:8080\r\n" +
                        "Content-Length: 10\r\n" + // Content-length aponta para 10 bytes
                        "\r\n" +
                        "hello"; // Hello só tem 5! Sobram bytes
        ByteArrayInputStream input = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.UTF_8));

        HttpRequestParser parser = new HttpRequestParser();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            parser.parse(input);
        });

        assertEquals("Incomplete body", exception.getMessage());
    }
    // Testar overflow do body
    @Test
    void rejectsOversizedBody() throws Exception {
        String body = "a".repeat(1_048_577); // O limite de bytes de um body é 1048576 bytes, então limite + 1 deve dar erro

        String rawRequest =
                "POST /medications HTTP/1.1\r\n" +
                        "Host: localhost:8080\r\n" +
                        "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" + // Já vai dar excessão ao ver content-length
                        "\r\n" +
                        body;

        ByteArrayInputStream input = new ByteArrayInputStream(
                rawRequest.getBytes(StandardCharsets.UTF_8)
        );

        HttpRequestParser parser = new HttpRequestParser();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(input)
        );

        assertEquals("Body too large", exception.getMessage());
    }
    // Overflow de bytes nos headers -> limite = 65.536 bytes
    @Test
    void rejectsOversizedHeaders() {
        StringBuilder rawRequest = new StringBuilder(
                "GET /health HTTP/1.1\r\n"
        );

        for (int i = 0; i < 70; i++) {
            rawRequest.append("X-Test-")
                    .append(i)
                    .append(": ")
                    .append("a".repeat(1000))
                    .append("\r\n");
        }

        rawRequest.append("\r\n");

        ByteArrayInputStream input = new ByteArrayInputStream(
                rawRequest.toString().getBytes(StandardCharsets.US_ASCII)
        );


        HttpRequestParser parser = new HttpRequestParser();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(input)
        );

        assertEquals("Headers too large", exception.getMessage());
    }

}