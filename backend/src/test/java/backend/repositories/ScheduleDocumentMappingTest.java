package backend.repositories;

import backend.models.CronogramaDose;
import backend.models.ScheduleFrequency;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleDocumentMappingTest {

    @Test
    void deveSerializarEReconstruirTodosOsCampos() {
        Instant from = Instant.parse("2026-11-20T12:00:00.123456Z");
        Instant to = Instant.parse("2026-12-01T09:30:00.654321Z");
        CronogramaDose schedule = new CronogramaDose(
                3, ScheduleFrequency.DAYS_OF_WEEK, List.of("MONDAY", "FRIDAY"),
                List.of("08:00", "20:00"), "2026-11-01", "2026-12-31",
                "America/Sao_Paulo", false, from, to);

        Document document = MongoTreatmentRepository.toScheduleDocument(schedule);
        CronogramaDose recovered = MongoTreatmentRepository.fromScheduleDocument(document);

        assertEquals(schedule, recovered);
        assertEquals("DAYS_OF_WEEK", document.getString("frequency"));
        assertEquals(from.toString(), document.getString("validFrom"));
        assertEquals(to.toString(), document.getString("validTo"));
    }

    @Test
    void deveLerDocumentoLegadoSemInventarVigencia() {
        Document legacy = new Document("version", 1)
                .append("daysOfWeek", null)
                .append("times", List.of("08:00"))
                .append("startDate", "2026-11-01")
                .append("endDate", null)
                .append("timezone", "America/Sao_Paulo")
                .append("active", true);

        CronogramaDose recovered = MongoTreatmentRepository.fromScheduleDocument(legacy);

        assertEquals(ScheduleFrequency.EVERY_DAY, recovered.getFrequency());
        assertNull(recovered.getValidFrom());
        assertNull(recovered.getValidTo());
    }
}
