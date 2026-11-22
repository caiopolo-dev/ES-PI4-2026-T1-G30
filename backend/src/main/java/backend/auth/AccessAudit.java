// Desenvolvido por Miguel Gallinucci.
package backend.auth;

import java.util.logging.Logger;

/** Registra somente eventos fixos, sem dados da requisicao. */
public final class AccessAudit {
    private static final Logger LOGGER = Logger.getLogger(AccessAudit.class.getName());

    public enum Event {
        // Sessao ausente ou invalida: corresponde a uma resposta 401.
        AUTHENTICATION_REQUIRED,
        // Usuario identificado, mas sem permissao para a operacao: corresponde a 403.
        PERMISSION_DENIED,
        // Nao diferencia recurso inexistente de recurso de outro titular, evitando revelar dados alheios.
        RESOURCE_UNAVAILABLE
    }

    private AccessAudit() {
        // O construtor privado impede criar instancias; o registro e feito pelo metodo static.
    }

    public static void record(Event event) {
        // Registrei so o nome do evento para o log nao guardar tokens nem dados pessoais da requisicao.
        LOGGER.info(event.name());
    }
}
