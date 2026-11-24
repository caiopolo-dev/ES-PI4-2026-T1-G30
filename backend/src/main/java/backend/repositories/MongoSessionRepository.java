// Desenvolvido por Caio Ferreira Polo.
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

/**
 * Armazena e consulta sessões na coleção do MongoDB.
 * A persistência permite reconhecer o mesmo token em requisições diferentes
 * e registrar quando uma sessão foi encerrada.
 */
public class MongoSessionRepository implements SessionRepository {

    private final MongoCollection<Document> collection;

    public MongoSessionRepository(MongoCollection<Document> collection) {
        this.collection = java.util.Objects.requireNonNull(collection);

        try {
            // O índice único impede que duas sessões usem o mesmo hash de token.
            collection.createIndex(Indexes.ascending("tokenHash"), new IndexOptions().unique(true));

            // Este índice facilita consultas relacionadas ao titular da sessão.
            collection.createIndex(Indexes.ascending("userId"));

            // O índice TTL permite que o MongoDB remova documentos depois de expiresAt.
            // A remoção é automática e pode não acontecer exatamente no instante da expiração.
            collection.createIndex(Indexes.ascending("expiresAt"), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));

        } catch (MongoException e) {
            throw new IllegalStateException(
                "Erro ao configurar os índices das sessões.", e
            );
        }
    }

    @Override
    public void save(AuthSession session) {

        // Converto a sessão para Document porque esse é o formato persistido no MongoDB.
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
            // A consulta usa o hash; o token original não precisa ser armazenado no banco.
            Document document = collection.find(Filters.eq("tokenHash", tokenHash)).first();
            if (document == null) {
                return Optional.empty();
            }

            Date revokedAt = document.getDate("revokedAt");

            // Reconstruo o modelo para que o SessionService possa verificar
            // expiração e revogação. O repository apenas recupera os dados.
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
            // A revogação é lógica: mantenho o documento e preencho revokedAt.
            // A atualização só aceita uma sessão ainda não revogada e não expirada.
            var result = collection.updateOne(
                Filters.and(
                    Filters.eq("tokenHash", tokenHash),
                    Filters.eq("revokedAt", null),
                    Filters.gt("expiresAt", Date.from(revokedAt))
                ),
                Updates.set("revokedAt", Date.from(revokedAt))
            );

            // Uma alteração confirma que exatamente uma sessão ativa foi encerrada.
            return result.getModifiedCount() == 1;

        } catch (MongoException e) {
            throw new IllegalStateException(
                "Erro ao revogar sessão.", e
            );
        }
    }
}
