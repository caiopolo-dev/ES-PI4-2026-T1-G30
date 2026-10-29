package backend.src.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Parser HTTP básico: uma requisição por conexão, corpo com Content-Length. */
public class HttpRequestParser {
    public HttpRequest parse(InputStream input) throws IOException {
        String[] parts = readLine(input).split(" ", -1);
        if (parts.length != 3 || !parts[0].matches("[A-Z]+")
                || !parts[1].startsWith("/")
                || !(parts[2].equals("HTTP/1.1") || parts[2].equals("HTTP/1.0"))) {
            throw new IllegalArgumentException("Invalid request line");
        }
        Map<String, String> headers = new HashMap<>();
        int headerBytes = 0;
        String line;
        while (!(line = readLine(input)).isEmpty()) {
            headerBytes += line.length() + 2;
            if (headerBytes > 65536) {
                throw new IllegalArgumentException("Headers too large");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("Invalid header");
            }
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            if (!name.matches("[!#$%&'*+.^_`|~0-9a-z-]+")
                    || headers.putIfAbsent(name, line.substring(colon + 1).trim()) != null) {
                throw new IllegalArgumentException("Invalid or duplicate header");
            }
        }
        if (headers.containsKey("transfer-encoding")) {
            throw new IllegalArgumentException("Transfer-Encoding unsupported");
        }
        String lengthHeader = headers.getOrDefault("content-length", "0");
        if (!lengthHeader.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid Content-Length");
        }
        int length = Integer.parseInt(lengthHeader);
        if (length > 1048576) {
            throw new IllegalArgumentException("Body too large");
        }
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(body, offset, length - offset);
            if (count == -1) {
                throw new IllegalArgumentException("Incomplete body");
            }
            offset += count;
        }
        String path = parts[1].split("\\?", 2)[0];
        return new HttpRequest(parts[0], path, parts[2], headers,
            new String(body, StandardCharsets.UTF_8));
    }

    private String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (line.size() <= 8192) {
            int value = input.read();
            if (value == -1) {
                throw new IllegalArgumentException("Incomplete headers");
            }
            if (value == '\r') {
                if (input.read() != '\n') {
                    throw new IllegalArgumentException("Invalid line ending");
                }
                return new String(line.toByteArray(), StandardCharsets.US_ASCII);
            }
            if (value == '\n') {
                throw new IllegalArgumentException("Invalid line ending");
            }
            line.write(value);
        }
        throw new IllegalArgumentException("Line too long");
    }
}
