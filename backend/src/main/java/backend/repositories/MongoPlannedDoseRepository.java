package backend.repositories;

import backend.models.DosePrevista;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document; // Documento no formato BSON adequado para o Mongo!

import java.util.Date;

public class MongoPlannedDoseRepository implements PlannedDoseRepository{

    private final MongoCollection<Document> collection;

    // Construtor: O MongoDatabase será fornecido pelo código que cria o repository
    public MongoPlannedDoseRepository(MongoDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database não pode ser null");
        }

        this.collection = database.getCollection("planned_doses");

        // Cria um índice único para evitar doses duplicadas do mesmo medicamento,
        // na mesma versão do cronograma e no mesmo horário previsto.
        collection.createIndex(
                new Document("medicationInUseId", 1)
                        .append("scheduleVersion", 1)
                        .append("scheduledAt", 1),
                new IndexOptions().unique(true)
        );
    }


    @Override
    public DosePrevista save(DosePrevista dose) {
        if (dose == null) {
            throw new IllegalArgumentException("Dose não pode ser null");
        }

        // Identifica a ocorrência, mesmo que ela tenha outro ID
        Document filtro = new Document("medicationInUseId", dose.getMedicationInUseId())
                .append("scheduleVersion", dose.getScheduleVersion())
                .append("scheduledAt", Date.from(dose.getScheduledAt()));

        // Monta o documento que será armazenado no MongoDB
        Document documento = new Document("_id", dose.getId())
                .append("userId", dose.getUserId())
                .append("treatmentId", dose.getTreatmentId())
                .append("medicationInUseId", dose.getMedicationInUseId())
                .append("scheduleVersion", dose.getScheduleVersion())
                .append("scheduledAt", Date.from(dose.getScheduledAt()))
                .append("scheduleTimezone", dose.getScheduleTimezone())
                .append("plannedQuantity", dose.getPlannedQuantity())
                .append("status", dose.getStatus());

        // Insere somente se a ocorrência ainda não existir
        Document salvo = collection.findOneAndUpdate(
                filtro, // Define os campos usados para localizar a dose
                new Document("$setOnInsert", documento), // Insere os campos somente se a dose não existir
                new FindOneAndUpdateOptions() // Configura a operação
                        .upsert(true) // Permite inserir um novo documento caso nenhum seja encontrado
                        .returnDocument(ReturnDocument.AFTER) // Retorna o documento após a operação usando operador AFTER do mongo
        );

        return fromDocument(salvo);
    }
    // Método auxiliar para retornar uma cópia de um documento (De DosePrevista) -> usado acima
    private DosePrevista fromDocument(Document doc) {
        return new DosePrevista(
                doc.getString("_id"),
                doc.getString("userId"),
                doc.getString("treatmentId"),
                doc.getString("medicationInUseId"),
                doc.getInteger("scheduleVersion"),
                doc.getDate("scheduledAt").toInstant(),
                doc.getString("scheduleTimezone"),
                doc.getInteger("plannedQuantity"),
                doc.getString("status")
        );
    }


    @Override
    public List<DosePrevista> saveAll(List<DosePrevista> doses) {
        if (doses == null) {
            throw new IllegalArgumentException("Lista de doses não pode ser null");
        }

        List<DosePrevista> dosesSalvas = new ArrayList<>();

        // Percorre a lista e salva cada dose individualmente
        for (DosePrevista dose : doses) {
            dosesSalvas.add(save(dose));
        }

        return dosesSalvas;
    }

    // Optional<T> é um container que pode conter um objeto do tipo T ou estar vazio.
    // É utilizado quando um método pode não encontrar um resultado, evitando retornar null.
    // Optional.of(objeto) encapsula um valor existente, enquanto Optional.empty()
    // representa a ausência de valor. Para acessar o conteúdo, podemos usar
    // isPresent(), get() ou orElse().

    @Override
    public Optional<DosePrevista> findById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID não pode ser nulo ou vazio");
        }

        // Busca o primeiro documento cujo _id corresponde ao informado
        Document documento = collection.find(new Document("_id", id)).first();

        // Se não encontrou o documento, retorna um Optional vazio
        if (documento == null) {
            return Optional.empty();
        }

        // Converte o documento para DosePrevista e retorna dentro do Optional
        return Optional.of(fromDocument(documento));
    }

    // Buscar todas as doses previstas de um usuário em uma data específica, independentemente do medicamento ou tratamento

    @Override
    public List<DosePrevista> findByUserAndDate(String user, LocalDate data) {
        if (user == null || user.isBlank() || data == null) {
            throw new IllegalArgumentException("Usuário e data devem ser válidos");
        }

        // Define o fuso horário utilizado para interpretar a data
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");

        // Calcula o início do dia e o início do dia seguinte em UTC
        Instant inicio = data.atStartOfDay(fuso).toInstant();
        Instant fim = data.plusDays(1).atStartOfDay(fuso).toInstant();

        // Filtra pelo usuário e pelas doses dentro do período
        Document filtro = new Document("userId", user)
                .append("scheduledAt", new Document("$gte", Date.from(inicio))
                        .append("$lt", Date.from(fim)));

        List<DosePrevista> doses = new ArrayList<>();

        // Percorre os documentos encontrados e converte para DosePrevista
        for (Document documento : collection.find(filtro)) {
            doses.add(fromDocument(documento));
        }

        return doses;
    }

    // buscar todas as doses previstas de um tratamento dentro de um intervalo de tempo
    @Override
    public List<DosePrevista> findByTreatmentAndPeriod(String treatmentId, Instant inicio, Instant fim) {
        if (treatmentId == null || treatmentId.isBlank() || inicio == null || fim == null) {
            throw new IllegalArgumentException("Tratamento e período devem ser válidos");
        }

        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O início deve ser anterior ao fim");
        }

        // Filtra as doses do tratamento dentro do período informado
        // MongoDB utiliza o tipo BSON Date para armazenar datas
        Document filtro = new Document("treatmentId", treatmentId)
                .append("scheduledAt", new Document("$gte", Date.from(inicio)) // from() recebe um Instant e devolve um Date que representa o mesmo instante
                        .append("$lt", Date.from(fim)));

        List<DosePrevista> doses = new ArrayList<>();

        // Percorre os documentos encontrados e os converte para DosePrevista
        for (Document documento : collection.find(filtro)) {
            doses.add(fromDocument(documento));
        }

        return doses;
    }


    @Override
    public List<DosePrevista> findByUserAndPeriod(String userId, Instant inicio, Instant fim) {
        if (userId == null || userId.isBlank() || inicio == null || fim == null) {
            throw new IllegalArgumentException("Usuário e período devem ser válidos");
        }

        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O início deve ser anterior ao fim");
        }

        // Filtra as doses do usuário dentro do período informado
        Document filtro = new Document("userId", userId)
                .append("scheduledAt", new Document("$gte", Date.from(inicio))
                        .append("$lt", Date.from(fim)));

        List<DosePrevista> doses = new ArrayList<>();

        // Percorre os documentos encontrados e converte para DosePrevista
        for (Document documento : collection.find(filtro)) {
            doses.add(fromDocument(documento));
        }

        return doses;
    }

    @Override
    public int countAll() {
        // Conta os documentos armazenados na collection
        return Math.toIntExact(collection.countDocuments()); // o método usado, ao contrário de um cast simples,
        // lança uma exceção caso o valor não caiba em um int
    }
}
