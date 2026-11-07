package backend.tests;

import backend.config.MongoConnection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MongoConnectionTest {
    @Test
    void rejectsMissingConfiguration() {
        // Configuração ausente deve falhar antes de tentar acessar a rede.
        assertThrows(IllegalArgumentException.class, () -> new MongoConnection(null, "test"));
        assertThrows(IllegalArgumentException.class, () -> new MongoConnection(" ", "test"));
        assertThrows(IllegalArgumentException.class, () -> new MongoConnection("mongodb://localhost", " "));
    }

    @Test
    void invalidUriDoesNotExposeCredentials() {
        var error = assertThrows(IllegalArgumentException.class,
            () -> new MongoConnection("invalid://user:secret@localhost", "test"));
        assertEquals("Configuração MONGODB_URI inválida.", error.getMessage());
        assertNull(error.getCause());
    }
}
