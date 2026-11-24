package backend.router;

import backend.http.HttpRequest;
import backend.http.HttpResponse;

/**
 * Abstração para qualquer componente capaz de processar uma requisição HTTP.
 * Funciona como a fronteira entre a camada HTTP e os serviços da aplicação.
 */
@FunctionalInterface
public interface HttpHandler {
    HttpResponse handle(HttpRequest request);
}

