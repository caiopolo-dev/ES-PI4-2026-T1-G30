package backend.tests;

import backend.http.HttpResponse;
import backend.http.HttpResponseWriter;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class HttpResponseWriterTest {

    // Testar com response válida, analisar output produzido e headers
    @Test
    void writesValidResponse() throws IOException {
        // Criar response HTTP válida
        HttpResponse response = HttpResponse.ok("{\"status\":\"ok\"}");
        // cria com "Content-Type: application/json; charset=utf-8\r\n"
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        byte[] bodyBytes = response.getBody().getBytes(StandardCharsets.UTF_8);

        HttpResponseWriter writer = new HttpResponseWriter();
        writer.write(output, response);

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.startsWith("HTTP/1.1 200 OK\r\n"), "Response errada");
        // Testando os headers que são atribuidos automaticamente pela classe HttpResponse:
        assertTrue(
                result.contains("Content-Type: application/json; charset=utf-8\r\n"),
                "Content-Type incorreto"
        );
        assertTrue(result.contains("Content-Length: " + bodyBytes.length + "\r\n"),
                "Content-Length incorreto");
        assertTrue(result.contains("Connection: close\r\n"), "Connection header incorreto");
        assertTrue(result.endsWith("\r\n\r\n{\"status\":\"ok\"}"), "Response errada");
    }

    // Testar Content-Length com caracteres UTF-8
    @Test
    void writesCorrectUtf8ContentLength() throws IOException {
        HttpResponse response = HttpResponse.ok("Olá");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        HttpResponseWriter writer = new HttpResponseWriter();
        writer.write(output, response);

        String result = output.toString(StandardCharsets.UTF_8);

        // "Olá" possui 3 caracteres, mas ocupa 4 bytes em UTF-8
        assertTrue(result.contains("Content-Length: 4\r\n"),
                "Content-Length UTF-8 incorreto");

        assertTrue(result.endsWith("\r\n\r\nOlá"),
                "Body UTF-8 incorreto");
    }

    // Testar substituição de headers gerenciados pelo writer
    @Test
    void replacesManagedHeaders() throws IOException {
        HttpResponse response = HttpResponse.ok("Hello");

        // Define valores incorretos propositalmente
        response.getHeaders().put("Content-Length", "999");
        response.getHeaders().put("Connection", "keep-alive");

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        HttpResponseWriter writer = new HttpResponseWriter();
        writer.write(output, response);

        String result = output.toString(StandardCharsets.UTF_8);

        // Verifica se os valores corretos foram escritos
        assertTrue(result.contains("Content-Length: 5\r\n"));
        assertTrue(result.contains("Connection: close\r\n"));

        // Verifica se os valores incorretos foram ignorados
        assertFalse(result.contains("Content-Length: 999\r\n"));
        assertFalse(result.contains("Connection: keep-alive\r\n"));

        // Verifica se não existem headers duplicados
        assertEquals(1, result.split("Content-Length: ", -1).length - 1);
        assertEquals(1, result.split("Connection: ", -1).length - 1);
    }

    // Testar resposta HTTP com status 404
    @Test
    void writesNotFoundResponse() throws IOException {
        HttpResponse response = HttpResponse.notFound("Route not found");

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        HttpResponseWriter writer = new HttpResponseWriter();
        writer.write(output, response);

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.startsWith("HTTP/1.1 404 Not Found\r\n"),
                "Status 404 incorreto");

        assertTrue(result.endsWith("\r\n\r\n" + response.getBody()),
                "Body da resposta 404 incorreto");
    }

    // Testar resposta HTTP com body vazio
    @Test
    void writesEmptyBody() throws IOException {
        HttpResponse response = HttpResponse.ok("");

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        HttpResponseWriter writer = new HttpResponseWriter();
        writer.write(output, response);

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.startsWith("HTTP/1.1 200 OK\r\n"),
                "Status incorreto");

        assertTrue(result.contains("Content-Length: 0\r\n"),
                "Content-Length deveria ser zero");

        assertTrue(result.endsWith("\r\n\r\n"),
                "Separação entre headers e body incorreta");
    }

    @Test
    void writesValidNoContentResponse() throws IOException {
        HttpResponse response = new HttpResponse(204, "No Content", "");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new HttpResponseWriter().write(output, response);

        String result = output.toString(StandardCharsets.UTF_8);
        assertTrue(result.startsWith("HTTP/1.1 204 No Content\r\n"));
        assertTrue(result.contains("Content-Length: 0\r\n"));
        assertTrue(result.endsWith("\r\n\r\n"));
    }

    @Test
    void writesValidUnauthorizedResponse() throws IOException {
        HttpResponse response = new HttpResponse(
            401,
            "Unauthorized",
            "{\"error\":\"Autenticação necessária.\"}"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new HttpResponseWriter().write(output, response);

        String result = output.toString(StandardCharsets.UTF_8);
        int bodyLength = response.getBody().getBytes(StandardCharsets.UTF_8).length;
        assertTrue(result.startsWith("HTTP/1.1 401 Unauthorized\r\n"));
        assertTrue(result.contains("Content-Length: " + bodyLength + "\r\n"));
        assertTrue(result.endsWith("\r\n\r\n" + response.getBody()));
    }

    @Test
    void preservesBinaryBodyAndLength() throws IOException {
        byte[] body = new byte[] {0, 1, 2, (byte) 0xff, 10};
        HttpResponse response = new HttpResponse(200, "OK", body, "application/octet-stream");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new HttpResponseWriter().write(output, response);

        byte[] written = output.toByteArray();
        byte[] separator = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        int bodyStart = indexOf(written, separator) + separator.length;
        assertTrue(bodyStart >= separator.length);
        assertArrayEquals(body, java.util.Arrays.copyOfRange(written, bodyStart, written.length));
        String headers = new String(written, 0, bodyStart, StandardCharsets.US_ASCII);
        assertTrue(headers.contains("Content-Type: application/octet-stream\r\n"));
        assertTrue(headers.contains("Content-Length: 5\r\n"));
    }

    private int indexOf(byte[] source, byte[] target) {
        for (int index = 0; index <= source.length - target.length; index++) {
            boolean found = true;
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return index;
            }
        }
        return -1;
    }

} 
