package backend.tests;

import backend.src.models.CronogramaDose;
import backend.src.models.MedicamentoEmUso;
import backend.src.models.Treatment;
import backend.src.repositories.MongoTreatmentRepository;
import backend.src.repositories.TreatmentRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Testes de unidade e integração da persistência de tratamentos,
 * medicamentos em uso e versionamento de cronogramas (Issue #16).
 */
public class TreatmentPersistenceTest {

    public static void main(String[] args) {
        testMinimumScenarioFromIssue();
        System.out.println("PASS: Cenário mínimo (Tratamento A -> Medicamento X -> 08:00 / 20:00)");

        testScheduleVersioningPreservesHistory();
        System.out.println("PASS: Preservação de versões históricas de CronogramaDose (v1 -> v2 -> v3)");

        testRepositoryAutomaticHistoryPreservationOnUpdate();
        System.out.println("PASS: Preservação automática de histórico pelo MongoTreatmentRepository.update()");

        testUserAssociationAndFiltering();
        System.out.println("PASS: Validação de associação ao usuário e consulta por findByUserId()");

        testMongoDocumentMappingAndIsolation();
        System.out.println("PASS: Mapeamento de documento MongoDB (_id, medications, schedules) e isolamento");

        System.out.println("\nTodos os testes da Issue #16 executados com sucesso!");
    }

    /**
     * Valida o "Teste mínimo" especificado na Issue #16:
     * Tratamento A -> Medicamento X (dose: 1 unidade, horários: 08:00 / 20:00).
     */
    private static void testMinimumScenarioFromIssue() {
        TreatmentRepository repository = new MongoTreatmentRepository();

        CronogramaDose scheduleV1 = new CronogramaDose(
                1,
                Arrays.asList("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                Arrays.asList("08:00", "20:00"),
                "2026-10-29",
                null,
                "America/Sao_Paulo",
                true
        );

        MedicamentoEmUso medicamentoX = new MedicamentoEmUso(
                "med-x",
                "Medicamento X",
                "1 unidade",
                1,
                "Tomar após as refeições",
                scheduleV1
        );

        Treatment tratamentoA = new Treatment(
                "trat-a",
                "usuario-01",
                "Tratamento A",
                "Tratamento inicial de uso contínuo",
                true,
                Collections.singletonList(medicamentoX)
        );

        repository.save(tratamentoA);

        Optional<Treatment> recoveredOpt = repository.findById("trat-a");
        check(recoveredOpt.isPresent(), "Tratamento A deve ser recuperado pelo ID");

        Treatment recovered = recoveredOpt.get();
        // 1. Dados do tratamento permanecem
        check("trat-a".equals(recovered.getId()), "ID do tratamento deve permanecer");
        check("usuario-01".equals(recovered.getUserId()), "userId do tratamento deve permanecer");
        check("Tratamento A".equals(recovered.getName()), "Nome do tratamento deve permanecer");
        check(recovered.isActive(), "Status ativo do tratamento deve permanecer");

        // 2. Medicamento permanece associado
        check(recovered.getMedications().size() == 1, "Deve possuir 1 medicamento associado");
        MedicamentoEmUso recoveredMed = recovered.getMedications().get(0);
        check("med-x".equals(recoveredMed.getId()), "ID do medicamento em uso deve permanecer");
        check("Medicamento X".equals(recoveredMed.getName()), "Nome do medicamento deve permanecer");
        check("1 unidade".equals(recoveredMed.getDose()), "Dose '1 unidade' deve permanecer");
        check(Integer.valueOf(1).equals(recoveredMed.getQuantity()), "Quantidade deve permanecer 1");
        check("Tomar após as refeições".equals(recoveredMed.getObservations()), "Observações devem permanecer");

        // 3. Cronograma permanece associado
        check(recoveredMed.getSchedules().size() == 1, "Deve possuir 1 cronograma associado");
        Optional<CronogramaDose> activeSchedOpt = recoveredMed.getActiveSchedule();
        check(activeSchedOpt.isPresent(), "Cronograma ativo deve estar disponível");
        CronogramaDose recoveredSchedule = activeSchedOpt.get();
        check(Arrays.asList("08:00", "20:00").equals(recoveredSchedule.getTimes()),
                "Horários 08:00 / 20:00 devem permanecer");
        check("2026-10-29".equals(recoveredSchedule.getStartDate()), "startDate deve permanecer");
        check("America/Sao_Paulo".equals(recoveredSchedule.getTimezone()), "timezone deve permanecer");
        check(recoveredSchedule.isActive(), "Cronograma deve estar marcado como ativo");

        // 4. Versão do cronograma permanece disponível
        check(recoveredSchedule.getVersion() == 1, "Versão 1 do cronograma deve permanecer disponível");
    }

    /**
     * Valida a evolução de versões de cronograma:
     * Schedule v1 (08:00 / 20:00) -> alteração -> Schedule v2 (08:00 / 21:00) -> Schedule v3.
     */
    private static void testScheduleVersioningPreservesHistory() {
        TreatmentRepository repository = new MongoTreatmentRepository();

        CronogramaDose v1 = new CronogramaDose(
                Arrays.asList("08:00", "20:00"),
                "2026-10-01",
                "America/Sao_Paulo"
        );
        MedicamentoEmUso med = new MedicamentoEmUso("med-1", "Losartana 50mg", "1 comprimido", v1);
        Treatment treatment = new Treatment("trat-hist", "user-10", "Controle Pressão", Collections.singletonList(med));

        repository.save(treatment);

        // Alteração 1: mudar horário de 08:00 / 20:00 para 08:00 / 21:00 (gera Schedule v2)
        Treatment toUpdate = repository.findById("trat-hist").get();
        MedicamentoEmUso medToUpdate = toUpdate.getMedications().get(0);
        medToUpdate.updateScheduleTimes(Arrays.asList("08:00", "21:00"), "2026-10-15");
        repository.update(toUpdate);

        Treatment afterV2 = repository.findById("trat-hist").get();
        MedicamentoEmUso medAfterV2 = afterV2.getMedications().get(0);
        check(medAfterV2.getSchedules().size() == 2, "Histórico deve conter 2 versões (v1 e v2)");

        CronogramaDose storedV1 = medAfterV2.getScheduleByVersion(1).orElse(null);
        CronogramaDose storedV2 = medAfterV2.getScheduleByVersion(2).orElse(null);
        check(storedV1 != null, "Versão 1 ainda deve existir no histórico");
        check(!storedV1.isActive(), "Versão 1 deve estar inativa após criação da v2");
        check(Arrays.asList("08:00", "20:00").equals(storedV1.getTimes()),
                "Horários originais da v1 (08:00 / 20:00) devem ser preservados");

        check(storedV2 != null, "Versão 2 deve existir");
        check(storedV2.isActive(), "Versão 2 deve ser a versão ativa");
        check(Arrays.asList("08:00", "21:00").equals(storedV2.getTimes()),
                "Horários da v2 devem ser 08:00 / 21:00");

        // Alteração 2: mudar frequência (daysOfWeek), quantidade e vigência (gera Schedule v3)
        medAfterV2.setQuantity(2);
        medAfterV2.updateSchedule(
                Arrays.asList("MONDAY", "WEDNESDAY", "FRIDAY"),
                Arrays.asList("09:00"),
                "2026-11-01",
                "2026-12-31",
                "America/Sao_Paulo"
        );
        repository.update(afterV2);

        Treatment afterV3 = repository.findById("trat-hist").get();
        MedicamentoEmUso medAfterV3 = afterV3.getMedications().get(0);
        check(medAfterV3.getSchedules().size() == 3, "Histórico deve conter 3 versões (v1, v2 e v3)");
        check(Integer.valueOf(2).equals(medAfterV3.getQuantity()), "Quantidade atualizada deve ser persistida");
        check(!medAfterV3.getScheduleByVersion(1).get().isActive(), "v1 permanece inativa");
        check(!medAfterV3.getScheduleByVersion(2).get().isActive(), "v2 passa a ficar inativa");

        CronogramaDose storedV3 = medAfterV3.getActiveSchedule().get();
        check(storedV3.getVersion() == 3, "Versão ativa deve ser a 3");
        check(Arrays.asList("MONDAY", "WEDNESDAY", "FRIDAY").equals(storedV3.getDaysOfWeek()),
                "Dias da semana da v3 devem ser preservados");
        check("2026-12-31".equals(storedV3.getEndDate()), "Data de fim de vigência da v3 deve ser preservada");
    }

    /**
     * Garante que mesmo quando o chamador instancia um novo objeto Treatment/MedicamentoEmUso
     * contendo apenas o novo cronograma, o MongoTreatmentRepository.update() preserva as versões
     * anteriores já salvas no banco.
     */
    private static void testRepositoryAutomaticHistoryPreservationOnUpdate() {
        MongoTreatmentRepository repository = new MongoTreatmentRepository();

        CronogramaDose initialSchedule = new CronogramaDose(
                Arrays.asList("08:00", "20:00"),
                "2026-10-01",
                "America/Sao_Paulo"
        );
        MedicamentoEmUso initialMed = new MedicamentoEmUso("med-auto", "Vitamina D", "1 cápsula", initialSchedule);
        Treatment initialTreatment = new Treatment(
                "trat-auto",
                "user-99",
                "Suplementação",
                Collections.singletonList(initialMed)
        );
        repository.save(initialTreatment);

        // Chamador envia objeto atualizado apenas com o novo horário (08:00 / 21:00)
        CronogramaDose modifiedSchedule = new CronogramaDose(
                Arrays.asList("08:00", "21:00"),
                "2026-10-20",
                "America/Sao_Paulo"
        );
        MedicamentoEmUso updatedMed = new MedicamentoEmUso("med-auto", "Vitamina D", "1 cápsula", modifiedSchedule);
        Treatment updatedTreatment = new Treatment(
                "trat-auto",
                "user-99",
                "Suplementação",
                Collections.singletonList(updatedMed)
        );
        repository.update(updatedTreatment);

        Treatment persisted = repository.findById("trat-auto").get();
        List<CronogramaDose> schedules = persisted.getMedications().get(0).getSchedules();
        check(schedules.size() == 2, "Repositório deve preservar v1 e adicionar v2 automaticamente");
        check(schedules.get(0).getVersion() == 1 && !schedules.get(0).isActive(),
                "v1 preservada como inativa");
        check(Arrays.asList("08:00", "20:00").equals(schedules.get(0).getTimes()),
                "Horários de v1 intactos");
        check(schedules.get(1).getVersion() == 2 && schedules.get(1).isActive(),
                "v2 adicionada como ativa");
        check(Arrays.asList("08:00", "21:00").equals(schedules.get(1).getTimes()),
                "Horários de v2 persistidos");
    }

    /**
     * Valida a associação ao usuário titular e as consultas filtradas por findByUserId().
     */
    private static void testUserAssociationAndFiltering() {
        TreatmentRepository repository = new MongoTreatmentRepository();

        Treatment t1UserA = new Treatment("t1", "user-A", "Tratamento 1 do Usuário A");
        Treatment t2UserA = new Treatment("t2", "user-A", "Tratamento 2 do Usuário A");
        Treatment t1UserB = new Treatment("t3", "user-B", "Tratamento 1 do Usuário B");

        repository.save(t1UserA);
        repository.save(t2UserA);
        repository.save(t1UserB);

        List<Treatment> userATreatments = repository.findByUserId("user-A");
        check(userATreatments.size() == 2, "Usuário A deve possuir exatamente 2 tratamentos");
        check("t1".equals(userATreatments.get(0).getId()), "Primeiro tratamento de user-A deve ser t1");
        check("t2".equals(userATreatments.get(1).getId()), "Segundo tratamento de user-A deve ser t2");

        List<Treatment> userBTreatments = repository.findByUserId("user-B");
        check(userBTreatments.size() == 1, "Usuário B deve possuir exatamente 1 tratamento");
        check("t3".equals(userBTreatments.get(0).getId()), "Tratamento de user-B deve ser t3");

        List<Treatment> unknownUserTreatments = repository.findByUserId("user-inexistente");
        check(unknownUserTreatments.isEmpty(), "Usuário sem tratamentos deve retornar lista vazia");

        check(repository.findByIdAndUserId("t1", "user-A").isPresent(),
                "findByIdAndUserId deve encontrar quando titular corresponde");
        check(!repository.findByIdAndUserId("t1", "user-B").isPresent(),
                "findByIdAndUserId não deve retornar tratamento de outro titular");

        // Impedir troca de titular (userId) na atualização
        try {
            Treatment wrongOwnerUpdate = new Treatment("t1", "user-B", "Tentativa de troca de titular");
            repository.update(wrongOwnerUpdate);
            throw new AssertionError("Deveria rejeitar alteração de titular (userId)");
        } catch (IllegalArgumentException expected) {
            // Esperado
        }
    }

    /**
     * Verifica a estrutura de documento MongoDB gerada e o isolamento de referências em memória.
     */
    private static void testMongoDocumentMappingAndIsolation() {
        MongoTreatmentRepository repository = new MongoTreatmentRepository();

        CronogramaDose schedule = new CronogramaDose(
                Arrays.asList("07:00", "19:00"),
                "2026-10-29",
                "America/Sao_Paulo"
        );
        MedicamentoEmUso med = new MedicamentoEmUso("med-iso", "Amoxicilina", "500 mg", 21, "De 12 em 12h", schedule);
        Treatment treatment = new Treatment("trat-iso", "user-iso", "Infecção", Collections.singletonList(med));

        repository.save(treatment);

        // Alterar o objeto em memória não deve alterar o documento salvo no banco sem chamar update()
        treatment.setName("Nome Modificado Apenas Em Memória");
        med.setDose("Dose Modificada Apenas Em Memória");

        Treatment fromDb = repository.findById("trat-iso").get();
        check("Infecção".equals(fromDb.getName()), "Documento persistido deve estar isolado de mutações externas");
        check("500 mg".equals(fromDb.getMedications().get(0).getDose()),
                "Subdocumento de medicamento deve estar isolado de mutações externas");

        Map<String, Object> rawDoc = repository.toDocument(fromDb);
        check("trat-iso".equals(rawDoc.get("_id")), "Chave primária no documento MongoDB deve ser _id");
        check("user-iso".equals(rawDoc.get("userId")), "Campo userId deve constar no documento MongoDB");

        String json = repository.toJsonDocument(fromDb);
        check(json.contains("\"_id\":\"trat-iso\""), "JSON do documento MongoDB deve conter _id");
        check(json.contains("\"schedules\":[{\"version\":1"), "JSON deve conter array de schedules versionado");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Falha no teste: " + message);
        }
    }
}

