package backend.tests;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class MongoConnectionTest {
    public static void main(String[] args) {
        String uri = System.getenv("MONGODB_URI");
        String dbName = System.getenv("MONGODB_DATABASE");

        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException(
                    "MONGODB_URI não está configurada."
            );
        }

        if (dbName == null || dbName.isBlank()) {
            throw new IllegalStateException(
                    "MONGODB_DATABASE não está configurada."
            );
        }

        System.out.println("URI configurada: " + (uri != null && !uri.isBlank()));
        System.out.println("Database: " + dbName);

        try (MongoClient client = MongoClients.create(uri)) {

            MongoDatabase database = client.getDatabase(dbName);

            database.runCommand(new Document("ping", 1));

            System.out.println("Conexão estabelecida com sucesso!");
        }
    }
}
