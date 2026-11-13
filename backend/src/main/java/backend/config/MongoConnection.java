package backend.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

/** Mantém uma conexão reutilizável com o MongoDB. */
public final class MongoConnection implements AutoCloseable {
    private final MongoClient client;
    private final MongoDatabase database;

    public MongoConnection() {
        // Usa as mesmas variáveis do teste de conexão existente.
        this(System.getenv("MONGODB_URI"), System.getenv("MONGODB_DATABASE"));
    }

    public MongoConnection(String uri, String databaseName) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("MONGODB_URI não está configurada.");
        }
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalStateException("MONGODB_DATABASE não está configurada.");
        }

        MongoClient openedClient = null;
        try {
            // Limita a espera quando o servidor não está disponível.
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri.trim()))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToSocketSettings(builder -> builder
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS))
                .build();

            openedClient = MongoClients.create(settings);
            MongoDatabase selectedDatabase = openedClient.getDatabase(databaseName.trim());
            // O ping confirma o acesso antes de disponibilizar a conexão.
            selectedDatabase.runCommand(new Document("ping", 1));
            this.client = openedClient;
            this.database = selectedDatabase;
        } catch (RuntimeException e) {
            if (openedClient != null) {
                openedClient.close();
            }
            throw new IllegalStateException(
                "Não foi possível conectar ao MongoDB. Confira a configuração e o servidor."
            );
        }
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getCollection(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da collection deve ser informado.");
        }
        return database.getCollection(name);
    }

    @Override
    public void close() {
        // Permite liberar os recursos com try-with-resources.
        client.close();
    }
}
