// Autor: Samuel Campovilla
package backend.repositories;

import backend.models.DosePrevista;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import backend.models.ScheduleStatus;
import java.math.BigDecimal;
import org.bson.types.Decimal128; // A classe pertence ao driver Java do MongoDB

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document; // Documento no formato BSON adequado para o Mongo!
import com.mongodb.MongoException;

import java.time.temporal.ChronoUnit;

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
                new Document("userId", 1)
                        .append("treatmentId", 1)
                        .append("medicationInUseId", 1)
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

        // Identifica uma ocorrência pelos campos de sua identidade lógica.
        Document filtro = new Document("userId", dose.getUserId())
                .append("treatmentId", dose.getTreatmentId())
                .append("medicationInUseId", dose.getMedicationInUseId())
                .append("scheduleVersion", dose.getScheduleVersion())
                .append("scheduledAt", Date.from(dose.getScheduledAt()));

        // Uma ocorrência cancelada só pode atualizar uma dose já existente.
        if (dose.getStatus() == ScheduleStatus.CANCELLED) {

            // Atualiza o status somente quando a ocorrência está agendada.
            Document filtroCancelamento = new Document(filtro).append("status", ScheduleStatus.SCHEDULED.name());

            Document atualizacao = new Document("$set", new Document("status", ScheduleStatus.CANCELLED.name()));

            Document salvo = collection.findOneAndUpdate(
                    filtroCancelamento,
                    atualizacao,
                    new FindOneAndUpdateOptions()
                            .returnDocument(ReturnDocument.AFTER)
            );

            if (salvo != null) return fromDocument(salvo);

            // Uma ocorrência já cancelada permanece cancelada.
            Document existente = collection.find(filtro).first();

            if (existente == null) {
                throw new IllegalStateException("Não é possível cancelar uma dose inexistente");
            }

            return fromDocument(existente);
        }

        // Monta o documento completo somente para uma nova ocorrência.
        Document documento = new Document("_id", dose.getId())
                .append("userId", dose.getUserId())
                .append("treatmentId", dose.getTreatmentId())
                .append("medicationInUseId", dose.getMedicationInUseId())
                .append("medicationName", dose.getMedicationName())
                .append("scheduleVersion", dose.getScheduleVersion())
                .append("scheduledAt", Date.from(dose.getScheduledAt()))
                .append("scheduleTimezone", dose.getScheduleTimezone())
                .append("plannedQuantity", new Decimal128(dose.getPlannedQuantity()))
                .append("status", ScheduleStatus.SCHEDULED.name());

        // Insere os dados apenas quando a ocorrência ainda não existe.
        // Se ela já existir, nenhum campo será alterado.
        Document atualizacao = new Document("$setOnInsert", documento);

        try {

            Document salvo = collection.findOneAndUpdate(
                    filtro,
                    atualizacao,
                    new FindOneAndUpdateOptions()
                            .upsert(true)
                            .returnDocument(ReturnDocument.AFTER)
            );

            return fromDocument(salvo);

        } catch (MongoException erro) {

            // Em execuções concorrentes, duas operações podem disputar
            // a inserção da mesma ocorrência.
            // O erro 11000 representa uma violação de índice único.
            if (erro.getCode() != 11000) {
                throw erro;
            }

            // Recupera a ocorrência que venceu a inserção.
            // Isso também preserva IDs antigos de documentos já existentes.
            Document existente = collection.find(filtro).first();

            if (existente == null) {
                // O conflito pode ter ocorrido por outro índice.
                // Nesse caso não devemos ocultar o erro original.
                throw erro;
            }

            return fromDocument(existente);
        }
    }


    // Método auxiliar para retornar uma cópia de um documento (De DosePrevista) -> usado acima
    // Converte um documento do MongoDB para um objeto DosePrevista
    private DosePrevista fromDocument(Document doc) {

        // Recupera a quantidade armazenada no MongoDB
        Object valor = doc.get("plannedQuantity");
        BigDecimal quantidade;

        // Decimal128 é uma das melhores maneiras de armazenar quantidades fracionárias no MongoDB sem perder precisão
        if (valor instanceof Decimal128) {
            // Novo formato: Decimal128 do MongoDB para BigDecimal do Java
            quantidade = ((Decimal128) valor).bigDecimalValue();
        } else if (valor instanceof Number) {
            // Compatibilidade com documentos antigos que armazenavam Integer
            quantidade = new BigDecimal(valor.toString());
        } else {
            throw new IllegalStateException("Quantidade armazenada inválida");
        }

        return new DosePrevista(
                doc.getString("_id"),
                doc.getString("userId"),
                doc.getString("treatmentId"),
                doc.getString("medicationInUseId"),
                doc.getString("medicationName"),
                doc.getInteger("scheduleVersion"),
                doc.getDate("scheduledAt").toInstant(),
                doc.getString("scheduleTimezone"),
                quantidade,
                ScheduleStatus.valueOf(doc.getString("status"))
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

    // Cancelamento por tratamento
    @Override
    public long cancelScheduledForTreatmentFrom(
            String userId,
            String treatmentId,
            Instant inicio) {

        if (userId == null || userId.isBlank()
                || treatmentId == null || treatmentId.isBlank()
                || inicio == null) {
            throw new IllegalArgumentException("Usuário, tratamento e instante devem ser válidos");
        }

        // Seleciona somente as ocorrências agendadas
        // do tratamento a partir do instante informado.
        Document filtro = new Document("userId", userId)
                .append("treatmentId", treatmentId)
                .append("status", ScheduleStatus.SCHEDULED.name())
                .append("scheduledAt", new Document("$gte", limiteMongo(inicio)));

        Document atualizacao = new Document("$set",
                new Document("status", ScheduleStatus.CANCELLED.name()));

        // Atualiza todos os documentos que correspondem ao filtro
        // e retorna a quantidade de documentos modificados.
        return collection.updateMany(filtro, atualizacao).getModifiedCount();
    }


    // Cancelamento por versão de cronograma
    @Override
    public long cancelScheduledForMedicationVersionFrom(
            String userId,
            String treatmentId,
            String medicationInUseId,
            int scheduleVersion,
            Instant inicio) {

        if (userId == null || userId.isBlank()
                || treatmentId == null || treatmentId.isBlank()
                || medicationInUseId == null || medicationInUseId.isBlank()
                || scheduleVersion < 1 || inicio == null) {
            throw new IllegalArgumentException("Dados de cancelamento inválidos");
        }

        // Restringe o cancelamento à versão informada.
        // Outras versões e medicamentos não devem ser afetados.
        Document filtro = new Document("userId", userId)
                .append("treatmentId", treatmentId)
                .append("medicationInUseId", medicationInUseId)
                .append("scheduleVersion", scheduleVersion)
                .append("status", ScheduleStatus.SCHEDULED.name())
                .append("scheduledAt", new Document("$gte", limiteMongo(inicio)));

        Document atualizacao = new Document("$set",
                new Document("status", ScheduleStatus.CANCELLED.name()));

        return collection.updateMany(filtro, atualizacao).getModifiedCount();
    }


    // O MongoDB armazena instantes com precisão de milissegundos.
    // Arredonda o limite para cima quando houver nanossegundos,
    // evitando cancelar uma ocorrência anterior ao instante informado.
    private Date limiteMongo(Instant instante) {

        Instant limite = instante.truncatedTo(ChronoUnit.MILLIS);

        if (instante.isAfter(limite)) {
            limite = limite.plusMillis(1);
        }

        return Date.from(limite);
    }

}
