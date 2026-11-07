package backend.tests;

import backend.config.MongoConnection;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

// Executa somente quando solicitado; não grava nem remove documentos.
@EnabledIfEnvironmentVariable(named = "RUN_MONGODB_TESTS", matches = "true")
class MongoConnectionIntegrationTest {
    @Test
    void connectsUsingEnvironmentAndClosesClient() {
        MongoConnection connection = new MongoConnection();
        var database = connection.getDatabase();
        try (connection) {
            assertEquals(System.getenv("MONGODB_DATABASE"), database.getName());
            assertEquals(1.0, ((Number) database.runCommand(new Document("ping", 1)).get("ok")).doubleValue());
            assertEquals("users", connection.getCollection("users").getNamespace().getCollectionName());
            assertThrows(IllegalArgumentException.class, () -> connection.getCollection(" "));
        }
        assertThrows(IllegalStateException.class, () -> database.runCommand(new Document("ping", 1)));
    }

    @Test
    void reportsUnavailableDatabase() throws Exception {
        // Uma porta TCP reservada sem MongoDB simula um serviço indisponível.
        try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            var error = assertThrows(IllegalStateException.class, () -> new MongoConnection(
                "mongodb://127.0.0.1:" + listener.getLocalPort(), "dosecerta_test"));
            assertTrue(error.getMessage().contains("Não foi possível conectar"));
            assertNull(error.getCause());
        }
    }
}
