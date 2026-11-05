package backend.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Escreve uma resposta por conexão, usando CRLF e tamanho em bytes UTF-8.
 * A resposta consiste em:
 * 1. Linha de status
 * 2. HTTP Headers
 * 3. Linha vazia
 * 4. Body da reposta
 * */
public class HttpResponseWriter {
    public void write(OutputStream output, HttpResponse response) throws IOException {
        //Converter o corpo da resposta para bytes
        byte[] bodyBytes = response.getBody().getBytes(StandardCharsets.UTF_8);

        StringBuilder responseHead = new StringBuilder();

        responseHead.append("HTTP/1.1 ")
                .append(response.getStatusCode())
                .append(" ")
                .append(response.getReasonPhrase())
                .append("\r\n");

        // Fazer o append dos headers, exceto Content-length e Connection
        // Map.Entry é um tipo que significa um único key-value pair.
        // Nesse caso, um header do mapa de headers
        for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {

            String name = header.getKey();
            String value = header.getValue();
            // Content-Length: calculado pelo writer com base no tamanho do corpo em bytes
            // Connection: definido pelo writer como "close".
            // São atribuidos depois do for loop
            if (name.equalsIgnoreCase("Content-Length")
                    || name.equalsIgnoreCase("Connection")) {
                continue;
            }

            responseHead.append(name)
                    .append(": ")
                    .append(value)
                    .append("\r\n");
        }

        responseHead.append("Content-Length: ")
                .append(bodyBytes.length)
                .append("\r\n");

        responseHead.append("Connection: close\r\n");

        // Parte final dos headers, antes do body
        responseHead.append("\r\n");


        output.write(
                responseHead.toString()
                        .getBytes(StandardCharsets.US_ASCII)
        );

        output.write(bodyBytes);
        output.flush();
    }
}
