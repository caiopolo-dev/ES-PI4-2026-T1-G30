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
        LOGGER.info(event.name());
    }
}
