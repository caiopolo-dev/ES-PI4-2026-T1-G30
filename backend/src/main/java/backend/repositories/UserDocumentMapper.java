package backend.repositories;

import backend.models.User;
import org.bson.Document;

/** Converte o usuário entre o modelo Java e o documento do MongoDB. */
public final class UserDocumentMapper {
    public Document toDocument(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Usuário deve ser informado.");
        }

        // O id do Java é armazenado como _id no MongoDB.
        return new Document("_id", readText(user.getId(), "_id"))
            .append("name", readText(user.getName(), "name"))
            .append("email", readText(user.getEmail(), "email"))
            .append("emailNormalized", readText(user.getEmailNormalized(), "emailNormalized"))
            .append("passwordHash", readText(user.getPasswordHash(), "passwordHash"))
            .append("passwordSalt", readText(user.getPasswordSalt(), "passwordSalt"))
            .append("passwordAlgorithm", readText(user.getPasswordAlgorithm(), "passwordAlgorithm"))
            .append("passwordIterations", readIterations(user.getPasswordIterations()));
    }

    public User fromDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Documento de usuário deve ser informado.");
        }

        // Reconstrói o modelo sem colocar dependências do MongoDB dentro de User.
        return new User(
            readText(document.get("_id"), "_id"),
            readText(document.get("name"), "name"),
            readText(document.get("email"), "email"),
            readText(document.get("emailNormalized"), "emailNormalized"),
            readText(document.get("passwordHash"), "passwordHash"),
            readText(document.get("passwordSalt"), "passwordSalt"),
            readText(document.get("passwordAlgorithm"), "passwordAlgorithm"),
            readIterations(document.get("passwordIterations"))
        );
    }

    private String readText(Object value, String field) {
        if (!(value instanceof String) || ((String) value).isBlank()) {
            // Informa apenas o campo inválido, sem mostrar seu conteúdo.
            throw new IllegalArgumentException("Campo de usuário inválido: " + field);
        }
        return (String) value;
    }

    private int readIterations(Object value) {
        // A modelagem exige Int32 positivo, sem converter ou truncar outros tipos.
        if (!(value instanceof Integer) || ((Integer) value) <= 0) {
            throw new IllegalArgumentException("Campo de usuário inválido: passwordIterations");
        }
        return (Integer) value;
    }
}
