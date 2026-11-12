package backend.repositories;

import backend.models.User;
import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import static com.mongodb.client.model.Filters.eq;

/** Salva e consulta usuários na collection fornecida pela conexão. */
public final class MongoUserRepository implements UserRepository {
    private final MongoCollection<Document> users;
    private final UserDocumentMapper mapper;

    public MongoUserRepository(MongoCollection<Document> users) {
        this.users = Objects.requireNonNull(users, "Collection de usuários deve ser informada.");
        this.mapper = new UserDocumentMapper();
        // O banco garante a unicidade mesmo quando duas gravações chegam juntas.
        try {
            this.users.createIndex(Indexes.ascending("emailNormalized"),
                new IndexOptions().unique(true).name("users_email_normalized_unique"));
        } catch (MongoException | IllegalStateException e) {
            throw databaseFailure(e);
        }
    }

    @Override
    public void save(User user) {
        // Insere um novo documento sem substituir usuários existentes.
        Document document = mapper.toDocument(user);
        // Evita armazenar uma chave de e-mail diferente da política de consulta.
        if (!normalizeEmail(user.getEmail()).equals(user.getEmailNormalized())) {
            throw new IllegalArgumentException("E-mail normalizado inconsistente.");
        }
        try {
            users.insertOne(document);
        } catch (MongoException | IllegalStateException e) {
            throw databaseFailure(e);
        }
    }

    @Override
    public Optional<User> findById(String id) {
        requireText(id, "ID");
        Document document;
        try {
            document = users.find(eq("_id", id)).first();
        } catch (MongoException | IllegalStateException e) {
            throw databaseFailure(e);
        }
        return toUser(document);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        Document document;
        try {
            document = users.find(eq("emailNormalized", normalizedEmail)).first();
        } catch (MongoException | IllegalStateException e) {
            throw databaseFailure(e);
        }
        return toUser(document);
    }

    private Optional<User> toUser(Document document) {
        if (document == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.fromDocument(document));
        } catch (IllegalArgumentException e) {
            throw new UserPersistenceException(UserPersistenceException.Reason.INVALID_DOCUMENT);
        }
    }

    private UserPersistenceException databaseFailure(RuntimeException error) {
        if (error instanceof MongoTimeoutException || error instanceof MongoSocketException
                || error instanceof IllegalStateException) {
            return new UserPersistenceException(UserPersistenceException.Reason.DATABASE_UNAVAILABLE);
        }
        if (error instanceof MongoException && ((MongoException) error).getCode() == 11000) {
            return new UserPersistenceException(UserPersistenceException.Reason.DUPLICATE_USER);
        }
        // Não repassa mensagens nem causas do driver que possam conter dados sensíveis.
        return new UserPersistenceException(UserPersistenceException.Reason.DATABASE_ERROR);
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " deve ser informado.");
        }
    }

    private String normalizeEmail(String email) {
        requireText(email, "E-mail");
        // A comparação ignora espaços nas extremidades e diferenças entre maiúsculas e minúsculas.
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
