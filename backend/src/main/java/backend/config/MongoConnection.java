package backend.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

/** Mantém o cliente MongoDB compartilhado durante a execução da aplicação. */
public final class MongoConnection implements AutoCloseable {
    private final MongoClient client;
    private final MongoDatabase database;

    public MongoConnection() {
        this(System.getenv("MONGODB_URI"), System.getenv("MONGODB_DATABASE"));
    }

    public MongoConnection(String uri, String databaseName) {
        requireValue(uri, "MONGODB_URI");
        requireValue(databaseName, "MONGODB_DATABASE");

        MongoClientSettings settings;
        try {
            // Limita a espera quando o banco estiver indisponível.
            settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri.trim()))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToSocketSettings(builder -> builder
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS))
                .build();
        } catch (IllegalArgumentException e) {
            // Não inclui a URI no erro, pois ela pode conter credenciais.
            throw new IllegalArgumentException("Configuração MONGODB_URI inválida.");
        }

        MongoClient createdClient = MongoClients.create(settings);
        MongoDatabase selectedDatabase;
        try {
            selectedDatabase = createdClient.getDatabase(databaseName.trim());
            // Criar o cliente não garante conexão; o ping confirma o acesso ao banco.
            selectedDatabase.runCommand(new Document("ping", 1));
        } catch (IllegalArgumentException e) {
            createdClient.close();
            throw new IllegalArgumentException("Configuração MONGODB_DATABASE inválida.");
        } catch (MongoException e) {
            createdClient.close();
            throw new IllegalStateException("Não foi possível conectar ao MongoDB. Confira a configuração e o servidor.");
        } catch (RuntimeException e) {
            createdClient.close();
            throw new IllegalStateException("Falha ao inicializar a conexão MongoDB.");
        }
        this.client = createdClient;
        this.database = selectedDatabase;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getCollection(String name) {
        requireValue(name, "Nome da collection");
        return database.getCollection(name.trim());
    }

    @Override
    public void close() {
        // Libera as conexões; pode ser chamado automaticamente por try-with-resources.
        client.close();
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " deve ser informado.");
        }
    }
}
