
package backend.repositories;

import backend.models.AuthSession;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

public class MongoSessionRepository implements SessionRepository {

    private final MongoCollection<Document> collection;

    public MongoSessionRepository(MongoCollection<Document> collection) {
        this.collection = java.util.Objects.requireNonNull(collection);

        try {
            // Impede duas sessões com o mesmo hash.
            collection.createIndex(Indexes.ascending("tokenHash"), new IndexOptions().unique(true));

            collection.createIndex(Indexes.ascending("userId"));

            // Remove sessões expiradas do banco eventualmente.
            collection.createIndex(Indexes.ascending("expiresAt"), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));

        } catch (MongoException e) {
            throw new IllegalStateException(
                "Erro ao configurar os índices das sessões.", e
            );
        }
    }

    @Override
    public void save(AuthSession session) {

        Document document = new Document()
            .append("tokenHash", session.getTokenHash())
            .append("userId", session.getUserId())
            .append("createdAt", Date.from(session.getCreatedAt()))
            .append("expiresAt", Date.from(session.getExpiresAt()))
            .append("revokedAt", session.getRevokedAt() == null ? null: Date.from(session.getRevokedAt()));

        try {
            collection.insertOne(document);
        } catch (MongoException e) {
            throw new IllegalStateException(
                "Erro ao salvar sessão.", e
            );
        }
    }

    @Override
    public Optional<AuthSession> findByTokenHash(String tokenHash) {

        try {
            Document document = collection.find(Filters.eq("tokenHash", tokenHash)).first();
            if (document == null) {
                return Optional.empty();
            }

            Date revokedAt = document.getDate("revokedAt");

            AuthSession session = new AuthSession(
                document.getString("tokenHash"),
                document.getString("userId"),
                document.getDate("createdAt").toInstant(),
                document.getDate("expiresAt").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant()
            );

            return Optional.of(session);

        } catch (MongoException e) {
            throw new IllegalStateException(
                "Erro ao consultar sessão.", e
            );
        }
    }

    @Override
    public boolean revoke(String tokenHash, Instant revokedAt) {

        try {
            var result = collection.updateOne(
                Filters.and(
                    Filters.eq("tokenHash", tokenHash),
                    Filters.eq("revokedAt", null),
                    Filters.gt("expiresAt", Date.from(revokedAt))
                ),
                Updates.set("revokedAt", Date.from(revokedAt))
            );

            return result.getModifiedCount() == 1;

        } catch (MongoException e) {
            throw new IllegalStateException(
                "Erro ao revogar sessão.", e
            );
        }
    }
}
