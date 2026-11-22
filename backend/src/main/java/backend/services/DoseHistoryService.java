// Desenvolvido por Miguel Gallinucci.
package backend.services;

import backend.auth.AuthContext;
import backend.models.DoseHistoryItem;
import backend.models.DoseHistoryPeriod;
import backend.models.DoseHistoryResponse;
import backend.models.DosePrevista;
import backend.models.ScheduleStatus;
import backend.repositories.DoseRecordRepository;
import backend.repositories.PlannedDoseRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compoe o historico persistido sem gerar doses ou registrar acoes. */
public final class DoseHistoryService {

    private final PlannedDoseRepository doses;
    private final DoseRecordRepository records;

    // Usei Clock para conseguir fixar o horario nos testes.
    private final Clock clock;
    // Clock fornece o horario atual; recebe-lo permite usar um horario fixo e previsivel nos testes.

    public DoseHistoryService(PlannedDoseRepository doses, DoseRecordRepository records) {

        this(doses, records, Clock.systemUTC());
    }

    public DoseHistoryService(PlannedDoseRepository doses, DoseRecordRepository records, Clock clock) {
        // Reutilizei os contratos existentes para consultar os mesmos dados da agenda, sem criar outro historico.

        this.doses = Objects.requireNonNull(doses);
        this.records = Objects.requireNonNull(records);
        this.clock = Objects.requireNonNull(clock);
    }

    // HistoryHandler chama com titular, periodo e filtros; devolvo previsoes e acoes em uma resposta unica.
    // A consulta so le os repositories existentes: nao cria doses nem modifica registros.
    public DoseHistoryResponse getHistory(AuthContext context, DoseHistoryPeriod period,
            String treatmentId, String medicationInUseId) {

        // Peguei a identidade do contexto porque o cliente nao pode escolher o titular.
        String userId = Objects.requireNonNull(context).userId();
        Objects.requireNonNull(period);

        String treatmentFilter = filter(treatmentId);
        String medicationFilter = filter(medicationInUseId);

        // Capturei o instante uma vez para toda a resposta usar a mesma referencia.
        var referenceTime = clock.instant();
        var start = period.startInclusive();
        var end = period.endExclusive();

        List<DosePrevista> selected = new ArrayList<>();

        for (var dose : doses.findByUserAndPeriod(userId, start, end)) {
            // O periodo considera o horario previsto da dose, mesmo se a acao foi registrada em outro dia.
            // Conferi titular e periodo novamente para nao expor uma ocorrencia indevida.
            if (!userId.equals(dose.getUserId()) || dose.getScheduledAt().isBefore(start)
                    || !dose.getScheduledAt().isBefore(end)) {
                continue;
            }

            if (treatmentFilter != null && !treatmentFilter.equals(dose.getTreatmentId())) {
                // Os filtros restringem doses ja autorizadas; um ID alheio nao permite consultar outro titular.
                continue;
            }

            if (medicationFilter != null && !medicationFilter.equals(dose.getMedicationInUseId())) {
                continue;
            }

            // Inclui futuras e canceladas tambem; esta consulta mostra o historico, nao calcula adesao.
            selected.add(dose);
        }

        // Usei o ID como desempate porque duas doses podem ter o mesmo horario.
        selected.sort(
            Comparator.comparing(DosePrevista::getScheduledAt)
                .thenComparing(DosePrevista::getId)
        );

        var ids = selected.stream().map(DosePrevista::getId).toList();

        // Busquei os registros em lote para evitar uma consulta por dose.
        // Busca em lote consulta varios IDs de uma vez; assim reduzo as idas ao banco.
        Map<String, backend.models.RegistroDeDose> associated = ids.isEmpty()
            // Sem doses selecionadas, nao preciso fazer outra consulta ao banco.
            ? Map.of() : records.findByPlannedDoseIdsAndUserId(ids, userId);

        List<DoseHistoryItem> items = new ArrayList<>();

        for (var dose : selected) {

            // Associei pelo ID da ocorrencia, pois o mesmo medicamento pode ter varias doses no dia.
            var record = associated.get(dose.getId());

            if (record != null && (!userId.equals(record.getUserId())
                    || !dose.getId().equals(record.getPlannedDoseId()))) {
                // Ignorei uma associacao inconsistente para nao expor a acao de outra dose ou pessoa.
                record = null;
            }

            // Mantive os dados da ocorrencia porque o cadastro atual pode ter mudado.
            // Deixei unidade e acao ausentes como null para nao inventar informacoes.
            // Sem registro nao significa SKIPPED; o cancelamento e informado separadamente da acao.
            items.add(new DoseHistoryItem(
                dose.getId(),
                dose.getTreatmentId(),
                dose.getMedicationInUseId(),
                dose.getMedicationName(),
                dose.getScheduleVersion(),
                dose.getScheduledAt(),
                dose.getScheduleTimezone(),
                dose.getPlannedQuantity(),
                null,
                record == null ? null : record.getAction(),
                record == null ? null : record.getActualTime(),
                record == null ? null : record.getRecordedAt(),
                dose.getStatus() == ScheduleStatus.CANCELLED
            ));
        }

        return new DoseHistoryResponse(
            // A estrutura permanece a mesma mesmo sem itens, facilitando a leitura pelo frontend.
            period.from(),
            period.to(),
            DoseHistoryPeriod.ZONE.getId(),
            referenceTime,
            items
        );
    }

    private String filter(String value) {
        // null significa filtro nao enviado; texto vazio e rejeitado para nao esconder um parametro incorreto.

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException("Filtro vazio.");
        }

        return value.trim();
    }
}
