//Autor: Cauã Bianchi Ferroni
package backend.repositories;

import backend.models.CronogramaDose;
import backend.models.MedicamentoEmUso;
import backend.models.Treatment;
import backend.models.TreatmentStatus;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.Decimal128;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação de TreatmentRepository persistida diretamente no MongoDB
 * utilizando o driver oficial (mongodb-driver-sync).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Persistir Treatment (incluindo período, status do ciclo de vida, closedAt,
 *       medicamentos em uso e cronogramas aninhados) na collection {@code treatments};</li>
 *   <li>Recuperar tratamentos por ID e por ID + titular ({@code userId});</li>
 *   <li>Listar tratamentos filtrando pelo usuário titular ({@code userId});</li>
 *   <li>Atualizar tratamentos preservando versões históricas de {@link CronogramaDose}
 *       e impedindo alteração de titular ou de tratamentos encerrados.</li>
 * </ul>
 */
public class MongoTreatmentRepository implements TreatmentRepository {

    public static final String DEFAULT_COLLECTION = "treatments";

    private final MongoCollection<Document> collection;
    private final String databaseName;
    private final String collectionName;

    /**
     * Cria o repositório utilizando a collection padrão {@code treatments} no banco informado.
     */
    public MongoTreatmentRepository(MongoDatabase database) {
        this(database, DEFAULT_COLLECTION);
    }

    /**
     * Cria o repositório utilizando uma collection específica no banco informado.
     */
    public MongoTreatmentRepository(MongoDatabase database, String collectionName) {
        if (database == null) {
            throw new IllegalArgumentException("Database não pode ser null");
        }
        String resolvedCollection = (collectionName != null && !collectionName.isBlank())
                ? collectionName.trim()
                : DEFAULT_COLLECTION;
        this.collection = database.getCollection(resolvedCollection);
        this.databaseName = database.getName();
        this.collectionName = resolvedCollection;
        ensureIndexes();
    }

    /**
     * Cria o repositório diretamente a partir de uma {@link MongoCollection}.
     */
    public MongoTreatmentRepository(MongoCollection<Document> collection) {
        if (collection == null) {
            throw new IllegalArgumentException("Collection não pode ser null");
        }
        this.collection = collection;
        this.databaseName = collection.getNamespace().getDatabaseName();
        this.collectionName = collection.getNamespace().getCollectionName();
        ensureIndexes();
    }

    private void ensureIndexes() {
        // Índice para consultas de tratamentos por titular (userId)
        this.collection.createIndex(Indexes.ascending("userId"));
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public Treatment save(Treatment treatment) {
        Objects.requireNonNull(treatment, "treatment não pode ser null");
        validateTreatmentDomain(treatment);

        Document document = toDocument(treatment);
        try {
            collection.insertOne(document);
        } catch (MongoWriteException e) {
            if (e.getError() != null && e.getError().getCode() == 11000) {
                throw new IllegalArgumentException(
                        "Já existe um tratamento cadastrado com o ID: " + treatment.getId(), e);
            }
            throw e;
        }

        return fromDocument(document);
    }

    @Override
    public Optional<Treatment> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Document stored = collection.find(Filters.eq("_id", id.trim())).first();
        if (stored == null) {
            return Optional.empty();
        }
        return Optional.of(fromDocument(stored));
    }

    @Override
    public List<Treatment> findByUserId(String userId) {
        Objects.requireNonNull(userId, "userId não pode ser null");
        String normalizedUserId = userId.trim();
        if (normalizedUserId.isEmpty()) {
            throw new IllegalArgumentException("userId não pode ser vazio");
        }

        List<Treatment> result = new ArrayList<>();
        for (Document doc : collection.find(Filters.eq("userId", normalizedUserId))) {
            result.add(fromDocument(doc));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Optional<Treatment> findByIdAndUserId(String id, String userId) {
        if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        Document stored = collection.find(
                Filters.and(
                        Filters.eq("_id", id.trim()),
                        Filters.eq("userId", userId.trim())
                )
        ).first();

        if (stored == null) {
            return Optional.empty();
        }
        return Optional.of(fromDocument(stored));
    }

    @Override
    public Treatment update(Treatment treatment) {
        Objects.requireNonNull(treatment, "treatment não pode ser null");
        validateTreatmentDomain(treatment);

        String id = treatment.getId();
        Document existingDoc = collection.find(Filters.eq("_id", id)).first();
        if (existingDoc == null) {
            throw new NoSuchElementException("Tratamento não encontrado para atualização: " + id);
        }

        Treatment existingTreatment = fromDocument(existingDoc);
        if (!existingTreatment.getUserId().equals(treatment.getUserId())) {
            throw new IllegalArgumentException(
                    "Não é permitido alterar o titular (userId) de um tratamento existente"
            );
        }

        if (existingTreatment.getStatus() == TreatmentStatus.CLOSED) {
            throw new IllegalStateException("Tratamento encerrado não pode ser alterado");
        }

        Treatment mergedTreatment = preserveMedicationAndScheduleHistory(existingTreatment, treatment);
        Document updatedDoc = toDocument(mergedTreatment);

        UpdateResult updateResult = collection.replaceOne(
                Filters.and(
                        Filters.eq("_id", id),
                        Filters.eq("userId", existingTreatment.getUserId())
                ),
                updatedDoc
        );

        if (updateResult.getMatchedCount() == 0) {
            throw new NoSuchElementException("Tratamento não encontrado para atualização: " + id);
        }

        return fromDocument(updatedDoc);
    }

    /**
     * Garante que atualizações em medicamentos em uso preservem versões anteriores
     * de CronogramaDose sem apagar o histórico passado.
     */
    private Treatment preserveMedicationAndScheduleHistory(
            Treatment existingTreatment,
            Treatment incomingTreatment) {
        List<MedicamentoEmUso> mergedMedications = new ArrayList<>();

        for (MedicamentoEmUso incomingMed : incomingTreatment.getMedications()) {
            Optional<MedicamentoEmUso> existingMedOpt =
                    existingTreatment.findMedicationById(incomingMed.getId());

            if (existingMedOpt.isEmpty()) {
                mergedMedications.add(incomingMed);
            } else {
                MedicamentoEmUso existingMed = existingMedOpt.get();
                List<CronogramaDose> mergedSchedules = mergeSchedules(
                        existingMed.getSchedules(),
                        incomingMed.getSchedules()
                );
                mergedMedications.add(new MedicamentoEmUso(
                        incomingMed.getId(),
                        incomingMed.getName(),
                        incomingMed.getDose(),
                        incomingMed.getQuantity(),
                        incomingMed.getUnit(),
                        incomingMed.getObservations(),
                        incomingMed.isActive(),
                        mergedSchedules,
                        incomingMed.getRevision()
                ));
            }
        }

        return new Treatment(
                incomingTreatment.getId(),
                incomingTreatment.getUserId(),
                incomingTreatment.getName(),
                incomingTreatment.getDescription(),
                incomingTreatment.getStartDate(),
                incomingTreatment.getEndDate(),
                incomingTreatment.getStatus(),
                incomingTreatment.getClosedAt(),
                mergedMedications
        );
    }

    /**
     * Combina a lista histórica de cronogramas já persistida com os novos cronogramas,
     * garantindo que versões passadas nunca sejam destruídas nem reativadas.
     */
    private List<CronogramaDose> mergeSchedules(
            List<CronogramaDose> existingSchedules,
            List<CronogramaDose> incomingSchedules) {
        if (existingSchedules.isEmpty()) {
            return incomingSchedules;
        }
        if (incomingSchedules.isEmpty()) {
            return existingSchedules;
        }

        List<CronogramaDose> history = new ArrayList<>(existingSchedules);
        int maxVersion = 0;
        for (CronogramaDose s : history) {
            if (s.getVersion() > maxVersion) {
                maxVersion = s.getVersion();
            }
        }

        for (CronogramaDose incoming : incomingSchedules) {
            int matchIndex = findMatchingVersionAndDefinition(history, incoming);
            if (matchIndex >= 0) {
                CronogramaDose existing = history.get(matchIndex);
                if (!existing.isActive() && incoming.isActive()) {
                    throw new IllegalArgumentException(
                            "Não é permitido reativar uma versão histórica do cronograma");
                }
                history.set(matchIndex, incoming);
                continue;
            }

            if (incoming.isActive()) {
                for (int i = 0; i < history.size(); i++) {
                    if (history.get(i).isActive()) {
                        history.set(i, history.get(i).deactivate());
                    }
                }
            }

            int nextVersion = incoming.getVersion() > maxVersion
                    ? incoming.getVersion()
                    : maxVersion + 1;
            maxVersion = nextVersion;
            history.add(incoming.withVersion(nextVersion));
        }

        return history;
    }

    private int findMatchingVersionAndDefinition(
            List<CronogramaDose> history,
            CronogramaDose candidate) {
        for (int i = 0; i < history.size(); i++) {
            CronogramaDose current = history.get(i);
            if (current.getVersion() == candidate.getVersion()
                    && sameScheduleDefinition(current, candidate)) {
                return i;
            }
        }
        return -1;
    }

    private boolean sameScheduleDefinition(CronogramaDose a, CronogramaDose b) {
        return Objects.equals(a.getDaysOfWeek(), b.getDaysOfWeek())
                && Objects.equals(a.getTimes(), b.getTimes())
                && Objects.equals(a.getStartDate(), b.getStartDate())
                && Objects.equals(a.getEndDate(), b.getEndDate())
                && Objects.equals(a.getTimezone(), b.getTimezone());
    }

    private void validateTreatmentDomain(Treatment treatment) {
        if (treatment.getUserId() == null || treatment.getUserId().isBlank()) {
            throw new IllegalArgumentException("Treatment deve estar associado a um userId válido");
        }
        if (treatment.getStatus() == null) {
            throw new IllegalArgumentException("Treatment deve possuir um status válido");
        }
        if (treatment.getStatus() != TreatmentStatus.DRAFT
                && (treatment.getName() == null || treatment.getName().isBlank())) {
            throw new IllegalArgumentException("Tratamento ativo ou encerrado deve possuir um nome válido");
        }
    }

    /**
     * Converte a entidade Treatment e seus subdocumentos para um {@link Document} BSON do MongoDB.
     */
    public Document toDocument(Treatment treatment) {
        Objects.requireNonNull(treatment, "treatment não pode ser null");

        Document doc = new Document()
                .append("_id", treatment.getId())
                .append("userId", treatment.getUserId())
                .append("name", treatment.getName())
                .append("description", treatment.getDescription())
                .append("startDate", treatment.getStartDate() != null
                        ? treatment.getStartDate().toString()
                        : null)
                .append("endDate", treatment.getEndDate() != null
                        ? treatment.getEndDate().toString()
                        : null)
                .append("status", treatment.getStatus().name())
                .append("closedAt", treatment.getClosedAt() != null
                        ? Date.from(treatment.getClosedAt())
                        : null);

        List<Document> medicationDocs = new ArrayList<>();

        for (MedicamentoEmUso med : treatment.getMedications()) {

            List<Document> scheduleDocs = new ArrayList<>();

            // Serializa todas as versões dos cronogramas.
            for (CronogramaDose schedule : med.getSchedules()) {
                Document schedDoc = new Document()
                        .append("version", schedule.getVersion())
                        .append("daysOfWeek", schedule.getDaysOfWeek() != null
                                ? new ArrayList<>(schedule.getDaysOfWeek())
                                : null)
                        .append("times", new ArrayList<>(schedule.getTimes()))
                        .append("startDate", schedule.getStartDate())
                        .append("endDate", schedule.getEndDate())
                        .append("timezone", schedule.getTimezone())
                        .append("active", schedule.isActive());

                scheduleDocs.add(schedDoc);
            }

            // Preserva os campos das duas branches.
            Document medDoc = new Document()
                    .append("id", med.getId())
                    .append("name", med.getName())
                    .append("dose", med.getDose())
                    .append("quantity", med.getQuantity())
                    .append("unit", med.getUnit())
                    .append("active", med.isActive())
                    .append("revision", med.getRevision())
                    .append("observations", med.getObservations())
                    .append("schedules", scheduleDocs);

            medicationDocs.add(medDoc);
        }

        doc.append("medications", medicationDocs);
        return doc;
    }

    /**
     * Reconstrói a entidade Treatment e seus subdocumentos a partir de um {@link Document} BSON do MongoDB.
     */
    public Treatment fromDocument(Document doc) {
        Objects.requireNonNull(doc, "doc não pode ser null");

        String id = doc.getString("_id");
        String userId = doc.getString("userId");
        String name = doc.getString("name");
        String description = doc.getString("description");
        LocalDate startDate = parseLocalDate(doc.get("startDate"));
        LocalDate endDate = parseLocalDate(doc.get("endDate"));
        TreatmentStatus status = parseStatus(doc);
        Instant closedAt = parseInstant(doc.get("closedAt"));

        List<MedicamentoEmUso> medications = new ArrayList<>();
        List<Document> medicationDocs = doc.getList("medications", Document.class);

        if (medicationDocs != null) {
            for (Document medDoc : medicationDocs) {
                if (medDoc == null) {
                    continue;
                }
                String medId = medDoc.getString("id");
                String medName = medDoc.getString("name");
                String dose = medDoc.getString("dose");
                Integer quantity = parseQuantity(medDoc.get("quantity"));
                String observations = medDoc.getString("observations");

                List<CronogramaDose> schedules = new ArrayList<>();
                List<Document> scheduleDocs = medDoc.getList("schedules", Document.class);

                if (scheduleDocs != null) {
                    for (Document schedDoc : scheduleDocs) {
                        if (schedDoc == null) {
                            continue;
                        }
                        Object versionRaw = schedDoc.get("version");
                        if (!(versionRaw instanceof Number)) {
                            throw new IllegalArgumentException("Versão do cronograma inválida no documento MongoDB");
                        }
                        int version = ((Number) versionRaw).intValue();
                        List<String> daysOfWeek = schedDoc.getList("daysOfWeek", String.class);
                        List<String> times = schedDoc.getList("times", String.class);
                        String schedStartDate = schedDoc.getString("startDate");
                        String schedEndDate = schedDoc.getString("endDate");
                        String timezone = schedDoc.getString("timezone");
                        Boolean schedActiveRaw = schedDoc.getBoolean("active");
                        boolean schedActive = schedActiveRaw != null ? schedActiveRaw : true;

                        schedules.add(new CronogramaDose(
                                version,
                                daysOfWeek != null ? new ArrayList<>(daysOfWeek) : null,
                                times != null ? new ArrayList<>(times) : Collections.emptyList(),
                                schedStartDate,
                                schedEndDate,
                                timezone,
                                schedActive
                        ));
                    }
                }

                medications.add(new MedicamentoEmUso(
                        medId,
                        medName,
                        dose,
                        quantity,
                        (String) medDoc.get("unit"),
                        observations,
                        !Boolean.FALSE.equals(medDoc.get("active")),
                        schedules,
                        medDoc.get("revision") instanceof Number ? ((Number) medDoc.get("revision")).longValue() : 0L
                ));
            }
        }

        return new Treatment(
                id,
                userId,
                name,
                description,
                startDate,
                endDate,
                status,
                closedAt,
                medications
        );
    }

    /**
     * Exporta o documento MongoDB correspondente a um tratamento em formato JSON (Extended JSON).
     */
    public String toJsonDocument(Treatment treatment) {
        return toDocument(treatment).toJson();
    }

    private TreatmentStatus parseStatus(Document doc) {
        String statusRaw = doc.getString("status");
        if (statusRaw != null && !statusRaw.isBlank()) {
            return TreatmentStatus.valueOf(statusRaw.trim());
        }
        throw new IllegalArgumentException("Status do tratamento ausente no documento MongoDB");
    }

    private LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            return text.isEmpty() ? null : LocalDate.parse(text);
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        throw new IllegalArgumentException("Formato de data inválido no documento MongoDB");
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            return text.isEmpty() ? null : Instant.parse(text);
        }
        throw new IllegalArgumentException("Formato de instante inválido no documento MongoDB");
    }

    private Integer parseQuantity(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Decimal128) {
            return ((Decimal128) value).bigDecimalValue().intValueExact();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("Formato de quantidade inválido no documento MongoDB");
    }
}
