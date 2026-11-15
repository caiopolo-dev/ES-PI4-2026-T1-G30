package backend.tests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import backend.config.MongoConnection;
import org.bson.Document;

/** Verifica a configuração, a conexão e o fechamento do cliente MongoDB. */
public class MongoConnectionValidationTest {
    @Test
    void rejectsInvalidConfiguration() {
        expectConfigurationFailure(null, "test", "MONGODB_URI");
        expectConfigurationFailure(" ", "test", "MONGODB_URI");
        expectConfigurationFailure("mongodb://localhost", null, "MONGODB_DATABASE");
        expectConfigurationFailure("mongodb://localhost", " ", "MONGODB_DATABASE");
        expectConfigurationFailure("invalid://user:secret@localhost", "test", "Não foi possível conectar");

    }

    @Test
    void rejectsUnavailableServer() throws Exception {
        // Reserva uma porta sem MongoDB para verificar o limite de espera.
        try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            expectConfigurationFailure("mongodb://127.0.0.1:" + listener.getLocalPort(),
                "test", "Não foi possível conectar");
        }

    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "mongo.integration", matches = "true")
    void connectsAndClosesClient() {
        // Executa apenas ping, sem gravar dados.
        MongoConnection connection = new MongoConnection();
        var database = connection.getDatabase();
        try (connection) {
            assertEquals(System.getenv("MONGODB_DATABASE").trim(), database.getName());
            database.runCommand(new Document("ping", 1));
            assertEquals("users", connection.getCollection("users").getNamespace().getCollectionName());
        }
        assertThrows(IllegalStateException.class,
            () -> database.runCommand(new Document("ping", 1)));
    }

    private static void expectConfigurationFailure(String uri, String database, String expectedMessage) {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            try (MongoConnection ignored = new MongoConnection(uri, database)) {
                fail("Configuração deveria falhar.");
            }
        });
        assertTrue(error.getMessage().contains(expectedMessage));
        assertFalse(error.getMessage().contains("secret"));
        assertNull(error.getCause());
    }
}
