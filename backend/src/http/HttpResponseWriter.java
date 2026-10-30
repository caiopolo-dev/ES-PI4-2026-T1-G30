package backend.src.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Escreve uma resposta por conexão, usando CRLF e tamanho em bytes UTF-8. */
public class HttpResponseWriter {
    public void write(OutputStream output, HttpResponse response) throws IOException {
        byte[] body = response.getBody().getBytes(StandardCharsets.UTF_8);
        StringBuilder headers = new StringBuilder("HTTP/1.1 ")
            .append(response.getStatusCode()).append(' ')
            .append(response.getReasonPhrase()).append("\r\n");
        for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            if (!header.getKey().equalsIgnoreCase("Content-Length")
                    && !header.getKey().equalsIgnoreCase("Connection")) {
                headers.append(header.getKey()).append(": ")
                    .append(header.getValue()).append("\r\n");
            }
        }
        headers.append("Content-Length: ").append(body.length)
            .append("\r\nConnection: close\r\n\r\n");
        output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }
}
