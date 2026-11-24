// Desenvolvido por Miguel Gallinucci.
package backend.repositories;

import backend.models.User;
import org.bson.Document;

/** Converte o usuário entre o modelo Java e o documento do MongoDB. */
public final class UserDocumentMapper {
    // Mapper e a classe que converte um formato em outro. Aqui recebo User e devolvo um Document para o MongoDB.
    // Deixei essa conversao separada para User nao precisar conhecer nomes de campos ou classes do banco.
    // O repository chama este metodo antes de inserir; na consulta, fromDocument faz o caminho de volta.
    // Essa separacao ajuda a manter o mesmo formato de dados e a testar a conversao sem conectar ao banco.
    public Document toDocument(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Usuário deve ser informado.");
        }

        // O id do Java é armazenado como _id no MongoDB.
        // Os dados da senha ja chegam processados; este mapper so converte e valida, sem calcular o hash.
        return new Document("_id", readText(user.getId(), "_id"))
            .append("name", readText(user.getName(), "name"))
            .append("email", readText(user.getEmail(), "email"))
            .append("emailNormalized", readText(user.getEmailNormalized(), "emailNormalized"))
            .append("passwordHash", readText(user.getPasswordHash(), "passwordHash"))
            .append("passwordSalt", readText(user.getPasswordSalt(), "passwordSalt"))
            .append("passwordAlgorithm", readText(user.getPasswordAlgorithm(), "passwordAlgorithm"))
            .append("passwordIterations", readIterations(user.getPasswordIterations()));
    }

    // Na consulta, faco o caminho inverso para o restante da aplicacao receber User, sem depender de BSON.
    public User fromDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Documento de usuário deve ser informado.");
        }

        // Reconstrói o modelo sem colocar dependências do MongoDB dentro de User.
        // Validei os campos na leitura para nao devolver um usuario montado com dados incompletos.
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
        // Uma conversao automatica poderia esconder um valor gravado com o tipo errado.
        if (!(value instanceof Integer) || ((Integer) value) <= 0) {
            throw new IllegalArgumentException("Campo de usuário inválido: passwordIterations");
        }

        return (Integer) value;
    }
}
