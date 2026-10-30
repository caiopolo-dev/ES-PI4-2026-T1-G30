package backend.src.repositories;

import backend.src.models.CronogramaDose;
import backend.src.models.MedicamentoEmUso;
import backend.src.models.Treatment;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação de TreatmentRepository baseada no modelo de documentos do MongoDB.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Persistir Treatment com seus medicamentos em uso e cronogramas aninhados;</li>
 *   <li>Recuperar tratamentos por ID;</li>
 *   <li>Listar tratamentos filtrando pelo usuário titular (userId);</li>
 *   <li>Atualizar tratamentos preservando versões históricas de CronogramaDose;</li>
 *   <li>Operar tanto conectado a uma instância MongoDB via Wire Protocol (OP_MSG/BSON)
 *       quanto em coleção de documentos MongoDB em memória (sem dependências externas).</li>
 * </ul>
 */
public class MongoTreatmentRepository implements TreatmentRepository {

    public static final String DEFAULT_DATABASE = "dosecerta";
    public static final String DEFAULT_COLLECTION = "treatments";

    private final String host;
    private final int port;
    private final String databaseName;
    private final String collectionName;
    private final boolean useRemoteSocket;

    /**
     * Armazenamento de documentos no formato estrutural do MongoDB (_id -> Document Map)
     * para operação local/embarcada e fallback determinístico em testes.
     */
    private final Map<String, Map<String, Object>> documentCollection = new LinkedHashMap<>();

    /**
     * Cria o repositório utilizando a coleção de documentos MongoDB embarcada (padrão para execução local e testes).
     */
    public MongoTreatmentRepository() {
        this(null, 27017, DEFAULT_DATABASE, DEFAULT_COLLECTION, false);
    }

    /**
     * Cria o repositório configurado para uma instância MongoDB em host e porta específicos.
     */
    public MongoTreatmentRepository(String host, int port, String databaseName, String collectionName) {
        this(host, port, databaseName, collectionName, true);
    }

    private MongoTreatmentRepository(
            String host,
            int port,
            String databaseName,
            String collectionName,
            boolean useRemoteSocket) {
        this.host = host != null ? host.trim() : "127.0.0.1";
        this.port = port > 0 ? port : 27017;
        this.databaseName = (databaseName != null && !databaseName.trim().isEmpty())
                ? databaseName.trim()
                : DEFAULT_DATABASE;
        this.collectionName = (collectionName != null && !collectionName.trim().isEmpty())
                ? collectionName.trim()
                : DEFAULT_COLLECTION;
        this.useRemoteSocket = useRemoteSocket;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public synchronized Treatment save(Treatment treatment) {
        Objects.requireNonNull(treatment, "treatment não pode ser null");
        validateTreatmentDomain(treatment);

        Map<String, Object> document = toDocument(treatment);
        String id = (String) document.get("_id");

        if (useRemoteSocket && isMongoServerReachable()) {
            insertDocumentOnMongoServer(document);
        }
        documentCollection.put(id, deepCopyDocument(document));

        return fromDocument(document);
    }

    @Override
    public synchronized Optional<Treatment> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalizedId = id.trim();

        Map<String, Object> stored = documentCollection.get(normalizedId);
        if (stored == null) {
            return Optional.empty();
        }
        return Optional.of(fromDocument(deepCopyDocument(stored)));
    }

    @Override
    public synchronized List<Treatment> findByUserId(String userId) {
        Objects.requireNonNull(userId, "userId não pode ser null");
        String normalizedUserId = userId.trim();
        if (normalizedUserId.isEmpty()) {
            throw new IllegalArgumentException("userId não pode ser vazio");
        }

        List<Treatment> result = new ArrayList<>();
        for (Map<String, Object> doc : documentCollection.values()) {
            if (normalizedUserId.equals(doc.get("userId"))) {
                result.add(fromDocument(deepCopyDocument(doc)));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized Optional<Treatment> findByIdAndUserId(String id, String userId) {
        if (id == null || id.trim().isEmpty() || userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> doc = documentCollection.get(id.trim());
        if (doc == null || !userId.trim().equals(doc.get("userId"))) {
            return Optional.empty();
        }
        return Optional.of(fromDocument(deepCopyDocument(doc)));
    }

    @Override
    public synchronized Treatment update(Treatment treatment) {
        Objects.requireNonNull(treatment, "treatment não pode ser null");
        validateTreatmentDomain(treatment);

        String id = treatment.getId();
        Map<String, Object> existingDoc = documentCollection.get(id);
        if (existingDoc == null) {
            throw new NoSuchElementException("Tratamento não encontrado para atualização: " + id);
        }

        Treatment existingTreatment = fromDocument(existingDoc);
        if (!existingTreatment.getUserId().equals(treatment.getUserId())) {
            throw new IllegalArgumentException(
                    "Não é permitido alterar o titular (userId) de um tratamento existente"
            );
        }

        Treatment mergedTreatment = preserveMedicationAndScheduleHistory(existingTreatment, treatment);
        Map<String, Object> updatedDoc = toDocument(mergedTreatment);

        if (useRemoteSocket && isMongoServerReachable()) {
            updateDocumentOnMongoServer(id, updatedDoc);
        }
        documentCollection.put(id, deepCopyDocument(updatedDoc));

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

            if (!existingMedOpt.isPresent()) {
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
                        incomingMed.getObservations(),
                        mergedSchedules
                ));
            }
        }

        return new Treatment(
                incomingTreatment.getId(),
                incomingTreatment.getUserId(),
                incomingTreatment.getName(),
                incomingTreatment.getDescription(),
                incomingTreatment.isActive(),
                mergedMedications
        );
    }

    /**
     * Combina a lista histórica de cronogramas já persistida com os novos cronogramas,
     * garantindo que versões passadas nunca sejam destruídas.
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
        if (treatment.getUserId() == null || treatment.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("Treatment deve estar associado a um userId válido");
        }
        if (treatment.getName() == null || treatment.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Treatment deve possuir um nome válido");
        }
    }

    /**
     * Converte a entidade Treatment e seus subdocumentos para um documento MongoDB.
     */
    public Map<String, Object> toDocument(Treatment treatment) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("_id", treatment.getId());
        doc.put("userId", treatment.getUserId());
        doc.put("name", treatment.getName());
        doc.put("description", treatment.getDescription());
        doc.put("active", treatment.isActive());

        List<Map<String, Object>> medicationDocs = new ArrayList<>();
        for (MedicamentoEmUso med : treatment.getMedications()) {
            Map<String, Object> medDoc = new LinkedHashMap<>();
            medDoc.put("id", med.getId());
            medDoc.put("name", med.getName());
            medDoc.put("dose", med.getDose());
            medDoc.put("quantity", med.getQuantity());
            medDoc.put("observations", med.getObservations());

            List<Map<String, Object>> scheduleDocs = new ArrayList<>();
            for (CronogramaDose schedule : med.getSchedules()) {
                Map<String, Object> schedDoc = new LinkedHashMap<>();
                schedDoc.put("version", schedule.getVersion());
                schedDoc.put(
                        "daysOfWeek",
                        schedule.getDaysOfWeek() != null
                                ? new ArrayList<>(schedule.getDaysOfWeek())
                                : null
                );
                schedDoc.put("times", new ArrayList<>(schedule.getTimes()));
                schedDoc.put("startDate", schedule.getStartDate());
                schedDoc.put("endDate", schedule.getEndDate());
                schedDoc.put("timezone", schedule.getTimezone());
                schedDoc.put("active", schedule.isActive());
                scheduleDocs.add(schedDoc);
            }
            medDoc.put("schedules", scheduleDocs);
            medicationDocs.add(medDoc);
        }
        doc.put("medications", medicationDocs);
        return doc;
    }

    /**
     * Reconstrói a entidade Treatment e seus subdocumentos a partir de um documento MongoDB.
     */
    @SuppressWarnings("unchecked")
    public Treatment fromDocument(Map<String, Object> doc) {
        String id = (String) doc.get("_id");
        String userId = (String) doc.get("userId");
        String name = (String) doc.get("name");
        String description = (String) doc.get("description");
        boolean active = doc.get("active") instanceof Boolean ? (Boolean) doc.get("active") : true;

        List<MedicamentoEmUso> medications = new ArrayList<>();
        List<Map<String, Object>> medicationDocs =
                (List<Map<String, Object>>) doc.get("medications");

        if (medicationDocs != null) {
            for (Map<String, Object> medDoc : medicationDocs) {
                String medId = (String) medDoc.get("id");
                String medName = (String) medDoc.get("name");
                String dose = (String) medDoc.get("dose");
                Integer quantity = medDoc.get("quantity") instanceof Number
                        ? ((Number) medDoc.get("quantity")).intValue()
                        : null;
                String observations = (String) medDoc.get("observations");

                List<CronogramaDose> schedules = new ArrayList<>();
                List<Map<String, Object>> scheduleDocs =
                        (List<Map<String, Object>>) medDoc.get("schedules");

                if (scheduleDocs != null) {
                    for (Map<String, Object> schedDoc : scheduleDocs) {
                        int version = ((Number) schedDoc.get("version")).intValue();
                        List<String> daysOfWeek = (List<String>) schedDoc.get("daysOfWeek");
                        List<String> times = (List<String>) schedDoc.get("times");
                        String startDate = (String) schedDoc.get("startDate");
                        String endDate = (String) schedDoc.get("endDate");
                        String timezone = (String) schedDoc.get("timezone");
                        boolean schedActive = schedDoc.get("active") instanceof Boolean
                                ? (Boolean) schedDoc.get("active")
                                : true;

                        schedules.add(new CronogramaDose(
                                version,
                                daysOfWeek != null ? new ArrayList<>(daysOfWeek) : null,
                                new ArrayList<>(times),
                                startDate,
                                endDate,
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
                        observations,
                        schedules
                ));
            }
        }

        return new Treatment(id, userId, name, description, active, medications);
    }

    /**
     * Exporta o documento MongoDB correspondente a um tratamento em formato JSON (Extended JSON).
     */
    public String toJsonDocument(Treatment treatment) {
        return mapToJson(toDocument(treatment));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyDocument(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map) {
            return deepCopyDocument((Map<String, Object>) value);
        }
        if (value instanceof List) {
            List<Object> listCopy = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                listCopy.add(deepCopyValue(item));
            }
            return listCopy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(quoteJson(entry.getKey())).append(":").append(valueToJson(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return quoteJson((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            return mapToJson((Map<String, Object>) value);
        }
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<Object> list = (List<Object>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(valueToJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return quoteJson(value.toString());
    }

    private String quoteJson(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // =========================================================================
    // Suporte a MongoDB Wire Protocol (OP_MSG / BSON) via TCP Socket puro
    // =========================================================================

    private boolean isMongoServerReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void insertDocumentOnMongoServer(Map<String, Object> document) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("insert", collectionName);
        command.put("documents", Collections.singletonList(document));
        command.put("$db", databaseName);
        sendOpMsg(command);
    }

    private void updateDocumentOnMongoServer(String id, Map<String, Object> document) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("_id", id);

        Map<String, Object> updateSpec = new LinkedHashMap<>();
        updateSpec.put("q", query);
        updateSpec.put("u", document);
        updateSpec.put("upsert", true);

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("update", collectionName);
        command.put("updates", Collections.singletonList(updateSpec));
        command.put("$db", databaseName);
        sendOpMsg(command);
    }

    private void sendOpMsg(Map<String, Object> command) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(2000);

            byte[] bsonPayload = encodeBsonDocument(command);
            int totalLength = 16 + 4 + 1 + bsonPayload.length;

            ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(totalLength); // messageLength
            buffer.putInt(1);           // requestID
            buffer.putInt(0);           // responseTo
            buffer.putInt(2013);        // opCode: OP_MSG (2013)
            buffer.putInt(0);           // flagBits
            buffer.put((byte) 0);       // section kind 0 (Body)
            buffer.put(bsonPayload);

            OutputStream out = socket.getOutputStream();
            out.write(buffer.array());
            out.flush();

            InputStream in = socket.getInputStream();
            DataInputStream dataIn = new DataInputStream(in);
            int respLen = Integer.reverseBytes(dataIn.readInt());
            if (respLen > 4 && respLen < 16 * 1024 * 1024) {
                byte[] rest = new byte[respLen - 4];
                dataIn.readFully(rest);
            }
        } catch (IOException ignored) {
            // Mantém a operação resiliente com a coleção de documentos local
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] encodeBsonDocument(Map<String, Object> doc) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val == null) {
                body.write(0x0A); // Null
                writeCString(body, key);
            } else if (val instanceof String) {
                body.write(0x02); // UTF-8 string
                writeCString(body, key);
                writeBsonString(body, (String) val);
            } else if (val instanceof Integer) {
                body.write(0x10); // int32
                writeCString(body, key);
                writeInt32LE(body, (Integer) val);
            } else if (val instanceof Boolean) {
                body.write(0x08); // boolean
                writeCString(body, key);
                body.write(((Boolean) val) ? 1 : 0);
            } else if (val instanceof Map) {
                body.write(0x03); // embedded document
                writeCString(body, key);
                body.write(encodeBsonDocument((Map<String, Object>) val));
            } else if (val instanceof List) {
                body.write(0x04); // array
                writeCString(body, key);
                Map<String, Object> arrayMap = new LinkedHashMap<>();
                List<Object> list = (List<Object>) val;
                for (int i = 0; i < list.size(); i++) {
                    arrayMap.put(String.valueOf(i), list.get(i));
                }
                body.write(encodeBsonDocument(arrayMap));
            }
        }
        body.write(0x00); // EOO

        byte[] bodyBytes = body.toByteArray();
        ByteArrayOutputStream full = new ByteArrayOutputStream(4 + bodyBytes.length);
        writeInt32LE(full, 4 + bodyBytes.length);
        full.write(bodyBytes);
        return full.toByteArray();
    }

    private void writeCString(ByteArrayOutputStream out, String str) throws IOException {
        out.write(str.getBytes(StandardCharsets.UTF_8));
        out.write(0x00);
    }

    private void writeBsonString(ByteArrayOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeInt32LE(out, bytes.length + 1);
        out.write(bytes);
        out.write(0x00);
    }

    private void writeInt32LE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
}

