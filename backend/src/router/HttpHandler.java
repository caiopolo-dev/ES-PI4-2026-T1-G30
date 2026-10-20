package backend.src.router;

import backend.src.http.HttpRequest;
import backend.src.http.HttpResponse;

/**
 * Abstração para qualquer componente capaz de processar uma requisição HTTP.
 * Funciona como a fronteira entre a camada HTTP e os serviços da aplicação.
 */
@FunctionalInterface
public interface HttpHandler {
    HttpResponse handle(HttpRequest request);
}

