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

        HttpResponseWriter writer = new HttpResponseWriter();
        writer.write(output, response);

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.startsWith("HTTP/1.1 200 OK\r\n"), "Response errada");
        assertTrue(result.endsWith("\r\n\r\n{\"status\":\"ok\"}"), "Response errada");
    }

} 
