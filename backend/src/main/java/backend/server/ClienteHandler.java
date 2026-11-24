// Desenvolvido originalmente por Caio Ferreira Polo.
// Issue #8 - Núcleo do servidor e ciclo de vida.
package backend.server;

import backend.http.HttpRequest;
import backend.http.HttpRequestParser;
import backend.http.HttpResponse;
import backend.http.HttpResponseWriter;
import backend.router.Router;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

/**
 * Atende uma única conexão recebida pelo servidor.
 * A classe implementa Runnable para que cada cliente possa ser processado
 * em uma thread sem bloquear a chegada das próximas conexões.
 */
public class ClienteHandler implements Runnable {
    // O Socket representa a comunicação com este cliente específico.
    private final Socket socket;
    // O Router compartilhado escolhe qual handler processará a requisição recebida.
    private final Router router;

    // Este construtor cria um Router próprio quando nenhum foi fornecido.
    public ClienteHandler(Socket socket) {
        this(socket, new Router());
    }

    // Este construtor permite reutilizar o Router já configurado pelo servidor.
    public ClienteHandler(Socket socket, Router router) {
        this.socket = Objects.requireNonNull(socket);
        this.router = Objects.requireNonNull(router);
    }

    @Override
    public void run() {
        // O try-with-resources fecha o Socket automaticamente quando o atendimento termina.
        try (Socket connection = socket) {
            // O timeout impede que uma conexão incompleta fique aguardando dados indefinidamente.
            connection.setSoTimeout(5000);
            HttpResponse response;
            try {
                // O parser transforma os bytes recebidos em uma HttpRequest estruturada.
                HttpRequest request = new HttpRequestParser().parse(connection.getInputStream());
                // O Router compara método e caminho para chamar o handler correspondente.
                response = router.route(request);
            } catch (IllegalArgumentException e) {
                // Uma requisição HTTP malformada é devolvida como erro do cliente.
                response = HttpResponse.badRequest("Invalid HTTP request");
            } catch (RuntimeException e) {
                // Falhas inesperadas viram erro interno sem expor detalhes ao cliente.
                response = HttpResponse.internalServerError("Internal server error");
            }
            // O writer converte a resposta em bytes e os envia pela mesma conexão.
            new HttpResponseWriter().write(connection.getOutputStream(), response);
        } catch (IOException e) {
            // Erros de comunicação são registrados no servidor porque talvez
            // já não seja possível responder ao cliente.
            System.err.println("Erro na conexão: " + e.getMessage());
        }
    }
}
