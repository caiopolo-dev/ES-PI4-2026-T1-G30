package backend.tests;

import backend.config.MongoConnection;
import org.bson.Document;

/** Verifica a configuração, a conexão e o fechamento do cliente MongoDB. */
public class MongoConnectionValidationTest {
    public static void main(String[] args) throws Exception {
        expectConfigurationFailure(null, "test", "MONGODB_URI");
        expectConfigurationFailure(" ", "test", "MONGODB_URI");
        expectConfigurationFailure("mongodb://localhost", null, "MONGODB_DATABASE");
        expectConfigurationFailure("mongodb://localhost", " ", "MONGODB_DATABASE");
        expectConfigurationFailure("invalid://user:secret@localhost", "test", "Não foi possível conectar");
        System.out.println("PASS: configuração ausente e URI inválida.");

        // Reserva uma porta sem MongoDB para verificar o limite de espera.
        try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            expectConfigurationFailure("mongodb://127.0.0.1:" + listener.getLocalPort(),
                "test", "Não foi possível conectar");
        }
        System.out.println("PASS: servidor indisponível retorna erro controlado.");

        // A verificação real é opcional e executa apenas ping, sem gravar dados.
        if (args.length > 0 && args[0].equals("--integration")) {
            MongoConnection connection = new MongoConnection();
            var database = connection.getDatabase();
            try (connection) {
                if (!database.getName().equals(System.getenv("MONGODB_DATABASE").trim())) {
                    throw new AssertionError("Banco selecionado incorretamente.");
                }
                database.runCommand(new Document("ping", 1));
                if (!connection.getCollection("users").getNamespace().getCollectionName().equals("users")) {
                    throw new AssertionError("Collection incorreta.");
                }
            }
            try {
                database.runCommand(new Document("ping", 1));
                throw new AssertionError("Cliente deveria estar encerrado.");
            } catch (IllegalStateException expected) {
                System.out.println("PASS: conexão real, acesso à collection e fechamento do cliente.");
            }
        }
    }

    private static void expectConfigurationFailure(String uri, String database, String expectedMessage) {
        try (MongoConnection ignored = new MongoConnection(uri, database)) {
            throw new AssertionError("Configuração deveria falhar.");
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains(expectedMessage) || e.getMessage().contains("secret")
                    || e.getCause() != null) {
                throw new AssertionError("Erro deveria ser controlado e não expor credenciais.");
            }
        }
    }
}
