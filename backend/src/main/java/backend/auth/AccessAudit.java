// Desenvolvido por Miguel Gallinucci.
package backend.auth;

import java.util.logging.Logger;

/** Registra somente eventos fixos, sem dados da requisicao. */
public final class AccessAudit {
    private static final Logger LOGGER = Logger.getLogger(AccessAudit.class.getName());

    public enum Event {
        AUTHENTICATION_REQUIRED,
        PERMISSION_DENIED,
        RESOURCE_UNAVAILABLE
    }

    private AccessAudit() {
    }

    public static void record(Event event) {
        // Registrei so o nome do evento para o log nao guardar tokens nem dados pessoais da requisicao.
        LOGGER.info(event.name());
    }
}
