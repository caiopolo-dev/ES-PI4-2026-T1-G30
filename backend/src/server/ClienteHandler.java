package backend.src.server;

import backend.src.http.HttpRequest;
import backend.src.http.HttpRequestParser;
import backend.src.http.HttpResponse;
import backend.src.http.HttpResponseWriter;
import backend.src.router.Router;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

public class ClienteHandler implements Runnable {
    private final Socket socket;
    private final Router router;

    public ClienteHandler(Socket socket) {
        this(socket, new Router());
    }

    public ClienteHandler(Socket socket, Router router) {
        this.socket = Objects.requireNonNull(socket);
        this.router = Objects.requireNonNull(router);
    }

    @Override
    public void run() {
        try (Socket connection = socket) {
            connection.setSoTimeout(5000);
            HttpResponse response;
            try {
                HttpRequest request = new HttpRequestParser().parse(connection.getInputStream());
                response = router.route(request);
            } catch (IllegalArgumentException e) {
                response = HttpResponse.badRequest("Invalid HTTP request");
            } catch (RuntimeException e) {
                response = HttpResponse.internalServerError("Internal server error");
            }
            new HttpResponseWriter().write(connection.getOutputStream(), response);
        } catch (IOException e) {
            System.err.println("Erro na conexão: " + e.getMessage());
        }
    }
}
