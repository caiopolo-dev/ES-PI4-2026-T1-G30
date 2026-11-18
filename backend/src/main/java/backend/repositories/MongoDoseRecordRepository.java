package backend.repositories;

import backend.models.RegistroDeDose;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// IMPORT DAS CLASSES QUE COMUNICAM COM O MONGO
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;

import java.util.Date;

public class MongoDoseRecordRepository implements DoseRecordRepository{

    private final MongoCollection<Document> collection;

    // Construtor: recebe o MongoDatabase e seleciona a collection de registros
    public MongoDoseRecordRepository(MongoDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database não pode ser null");
        }

        this.collection = database.getCollection("dose_records");

        // Cria um índice único para impedir mais de um registro por dose prevista
        collection.createIndex(
                new Document("plannedDoseId", 1),
                new IndexOptions().unique(true)
        );
    }


    // Método auxiliar, igual usei em MongoPlannedDoseRepository
    // Converte um documento do MongoDB para um objeto RegistroDeDose
    private RegistroDeDose fromDocument(Document doc) {
        Date actualTime = doc.getDate("actualTime");

        return new RegistroDeDose(
                doc.getString("_id"),
                doc.getString("plannedDoseId"),
                doc.getString("action"),
                actualTime != null ? actualTime.toInstant() : null,  // verifica se existe um horário registrado,
                //se existir, converte de Date para Instant, se não existir, retorna null
                doc.getDate("recordedAt").toInstant()
        );
    }

    @Override
    public RegistroDeDose save(RegistroDeDose registro) {
        if (registro == null) {
            throw new IllegalArgumentException("Registro não pode ser null");
        }

        // Identifica o registro pela dose prevista associada
        Document filtro = new Document("plannedDoseId", registro.getPlannedDoseId());

        // Monta o documento que será armazenado no MongoDB
        Document documento = new Document("_id", registro.getId())
                .append("plannedDoseId", registro.getPlannedDoseId())
                .append("action", registro.getAction())
                .append("actualTime", registro.getActualTime() != null
                        ? Date.from(registro.getActualTime()) : null)
                .append("recordedAt", Date.from(registro.getRecordedAt()));

        // Insere o registro somente se ainda não existir
        Document salvo = collection.findOneAndUpdate(
                filtro, // Localiza pelo ID da dose prevista
                new Document("$setOnInsert", documento), // Insere sem sobrescrever registros existentes
                new FindOneAndUpdateOptions()
                        .upsert(true) // Insere caso não encontre o registro
                        .returnDocument(ReturnDocument.AFTER) // Retorna o documento armazenado
        );

        RegistroDeDose resultado = fromDocument(salvo);

        // Impede que uma dose já registrada receba uma ação contraditória
        if (!resultado.getAction().equals(registro.getAction())) {
            throw new IllegalStateException("Esta dose já possui um registro com outra ação");
        }

        return resultado;
    }

    @Override
    public Optional<RegistroDeDose> findByPlannedDoseId(String plannedDoseId) {
        if (plannedDoseId == null || plannedDoseId.isBlank()) {
            throw new IllegalArgumentException("ID da dose prevista não pode ser nulo ou vazio");
        }

        // Busca o primeiro registro associado ao ID da dose prevista
        Document documento = collection.find(
                new Document("plannedDoseId", plannedDoseId)
        ).first();

        // Se não encontrou nenhum registro, retorna um Optional vazio
        if (documento == null) {
            return Optional.empty();
        }

        // Converte o documento para RegistroDeDose e retorna dentro do Optional
        return Optional.of(fromDocument(documento));
    }

    @Override
    public List<RegistroDeDose> findByPeriod(Instant inicio, Instant fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("O período deve ser válido");
        }

        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O início deve ser anterior ao fim");
        }

        // Filtra os registros realizados dentro do período informado
        // MongoDB utiliza o tipo BSON Date para armazenar datas
        Document filtro = new Document("recordedAt",
                new Document("$gte", Date.from(inicio)) // from() recebe um Instant e devolve um Date que representa o mesmo instante
                        .append("$lt", Date.from(fim)));

        List<RegistroDeDose> registros = new ArrayList<>();

        // Percorre os documentos encontrados e os converte para RegistroDeDose
        for (Document documento : collection.find(filtro)) {
            registros.add(fromDocument(documento));
        }

        return registros;
    }
}
