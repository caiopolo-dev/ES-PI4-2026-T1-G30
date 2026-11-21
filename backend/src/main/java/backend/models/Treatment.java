// Autores: Cauã Bianchi Ferroni, Samuel Campovilla
package backend.models;

import java.time.*;
import java.util.*;

/**
 * Representa um tratamento médico associado a um usuário específico (titular).
 * Agrega os medicamentos em uso (MedicamentoEmUso) e suas respectivas versões
 * de cronograma (CronogramaDose).
 *
 * Alteração importante: Uso de um novo ENUM para o status: TreatmentStatus
 *  * DRAFT  - Tratamento em elaboração, podendo estar incompleto.
 *  * ACTIVE - Tratamento validado e em andamento.
 *  * CLOSED - Tratamento encerrado, com histórico preservado.
 *
 * As transições seguem o fluxo DRAFT -> ACTIVE -> CLOSED.
 * Tratamentos encerrados não podem ser reativados.
 *
 *
 */
public class Treatment {
    private final String id;
    private final String userId;

    private String name;
    private String description;
    // startDate e endDate representam o período planejado.
    private LocalDate startDate;
    private LocalDate endDate;

    private TreatmentStatus status;
    private Instant closedAt; // closedAt registra o instante efetivo de encerramento.

    private final List<MedicamentoEmUso> medications;



    public Treatment(
            String id,
            String userId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            TreatmentStatus status,
            Instant closedAt,
            List<MedicamentoEmUso> medications) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Usuário inválido");
        }

        if (status == null) {
            throw new IllegalArgumentException("Status obrigatório");
        }

        if (startDate != null && endDate != null
                && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Período inválido");
        }

        if (status != TreatmentStatus.DRAFT
                && (name == null || name.isBlank())) {
            throw new IllegalArgumentException(
                    "Tratamento ativo ou encerrado deve possuir nome");
        }

        if (status == TreatmentStatus.CLOSED && closedAt == null) {
            throw new IllegalArgumentException(
                    "Tratamento encerrado deve possuir closedAt");
        }

        if (status != TreatmentStatus.CLOSED && closedAt != null) {
            throw new IllegalArgumentException(
                    "Somente tratamentos encerrados possuem closedAt");
        }

        this.id = (id == null || id.isBlank())
                ? UUID.randomUUID().toString()
                : id.trim();

        this.userId = userId.trim();
        this.name = name == null ? null : name.trim();
        this.description = description == null ? null : description.trim();

        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.closedAt = closedAt;

        this.medications = new ArrayList<>();

        if (medications != null) {
            for (MedicamentoEmUso medication : medications) {
                if (medication != null) {
                    this.medications.add(copiarMedicamento(medication));
                }
            }
        }


        // Valida os tratamentos ativos reconstruídos do banco
        if (status == TreatmentStatus.ACTIVE) {
            validarDadosAtivacao();
        }

        // Tratamentos encerrados devem possuir uma data inicial
        if (status == TreatmentStatus.CLOSED && startDate == null) {
            throw new IllegalArgumentException(
                    "Tratamento encerrado deve possuir data inicial");
        }

    }


    // Construtor secundário para criar novos rascunhos
    public Treatment(
            String userId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate) {

        this(
                null,
                userId,
                name,
                description,
                startDate,
                endDate,
                TreatmentStatus.DRAFT,
                null,
                Collections.emptyList()
        );
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }


    public void setName(String name) {
        validarAlteracao();

        if (status == TreatmentStatus.ACTIVE && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Tratamento ativo deve possuir nome");
        }

        this.name = name == null ? null : name.trim();
    }

    // Verifica se o tratamento ainda permite alterações.
    // Tratamentos encerrados devem preservar seus dados e histórico.
    private void validarAlteracao() {
        if (status == TreatmentStatus.CLOSED) {
            throw new IllegalStateException("Tratamento encerrado não pode ser alterado");
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        // Verifica se o tratamento permite alterações
        validarAlteracao();

        this.description = description != null ? description.trim() : null;
    }

    public List<MedicamentoEmUso> getMedications() {

        // Retorna cópias para evitar alterações nos objetos originais
        List<MedicamentoEmUso> copia = new ArrayList<>();

        for (MedicamentoEmUso medication : medications) {
            copia.add(copiarMedicamento(medication));
        }

        return Collections.unmodifiableList(copia);
    }

    // Verifica se uma atualização preserva as versões anteriores
    private void validarHistorico(
            MedicamentoEmUso anterior,
            MedicamentoEmUso atualizado) {

        for (CronogramaDose antigo : anterior.getSchedules()) {

            CronogramaDose novo = atualizado
                    .getScheduleByVersion(antigo.getVersion())
                    .orElseThrow(() -> new IllegalArgumentException("Não é permitido apagar versões do cronograma"));

            // A definição de uma versão existente não pode ser reescrita
            if (!Objects.equals(antigo.getDaysOfWeek(), novo.getDaysOfWeek())
                    || !Objects.equals(antigo.getTimes(), novo.getTimes())
                    || !Objects.equals(antigo.getStartDate(), novo.getStartDate())
                    || !Objects.equals(antigo.getEndDate(), novo.getEndDate())
                    || !Objects.equals(antigo.getTimezone(), novo.getTimezone())) {
                throw new IllegalArgumentException("Não é permitido alterar uma versão histórica");
            }

            // Uma versão histórica inativa não pode ser reativada
            if (!antigo.isActive() && novo.isActive()) {
                throw new IllegalArgumentException("Não é permitido reativar uma versão histórica");
            }

            // Impede que um snapshot já registrado seja alterado ou removido.
            if (antigo.hasPosologySnapshot()
                    && (!Objects.equals(antigo.getQuantitySnapshot(), novo.getQuantitySnapshot())
                    || !Objects.equals(antigo.getUnitSnapshot(), novo.getUnitSnapshot()))) {

                throw new IllegalArgumentException(
                        "Não é permitido alterar a posologia de uma versão existente"
                );
            }
        }
    }


    public void addMedication(MedicamentoEmUso medication) {

        // Não permite alterar medicamentos de tratamentos encerrados
        validarAlteracao();
        Objects.requireNonNull(medication, "medication não pode ser null");

        MedicamentoEmUso copia = copiarMedicamento(medication);

        for (int i = 0; i < medications.size(); i++) {

            MedicamentoEmUso anterior = medications.get(i);

            if (anterior.getId().equals(copia.getId())) {

                // Garante que a atualização preserve o histórico
                validarHistorico(anterior, copia);

                medications.set(i, copia);

                // Não permite deixar um tratamento ativo sem cronograma válido
                if (status == TreatmentStatus.ACTIVE && !possuiCronogramaValido()) {

                    medications.set(i, anterior);

                    throw new IllegalArgumentException("O tratamento precisa manter um cronograma válido");
                }

                return;
            }
        }

        // Adiciona um medicamento novo
        medications.add(copia);
    }


    public Optional<MedicamentoEmUso> findMedicationById(String medicationId) {
        if (medicationId == null) {
            return Optional.empty();
        }
        for (MedicamentoEmUso medication : medications) {
            if (medicationId.equals(medication.getId())) {
                return Optional.of(copiarMedicamento(medication));
            }
        }
        return Optional.empty();
    }



     // Encerra um tratamento que está ativo.
     // O encerramento não apaga os medicamentos nem seus cronogramas.
     // Apenas altera o status e registra quando ocorreu.
    public void close(Instant closedAt) {

        // Somente tratamentos ativos podem ser encerrados
        if (status != TreatmentStatus.ACTIVE) {
            throw new IllegalStateException("Somente tratamentos ativos podem ser encerrados");
        }

        // O instante do encerramento é obrigatório
        if (closedAt == null) {
            throw new IllegalArgumentException("Data de encerramento inválida");
        }

        this.status = TreatmentStatus.CLOSED;
        this.closedAt = closedAt;
    }


     // Ativa um tratamento que está em DRAFT.
     // Antes da ativação, verifica se o tratamento possui
     // as informações necessárias para iniciar.
    public void activate() {

        // Somente tratamentos em DRAFT podem ser ativados
        if (status != TreatmentStatus.DRAFT) {
            throw new IllegalStateException("Somente tratamentos em DRAFT podem ser ativados");
        }

        // Verifica os dados e o cronograma antes de ativar
        validarDadosAtivacao();

        this.status = TreatmentStatus.ACTIVE;
    }




     // Atualiza o período planejado de um tratamento.
     // Permite alterações apenas enquanto estiver em DRAFT.
    public void updatePeriod(LocalDate startDate, LocalDate endDate) {

        // Somente rascunhos podem ter o período alterado
        if (status != TreatmentStatus.DRAFT) {
            throw new IllegalStateException("Somente tratamentos em DRAFT podem alterar o período");
        }

        // A data final não pode ser anterior à data inicial
        if (startDate != null && endDate != null
                && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("A data final não pode anteceder a inicial");
        }

        // Atualiza as datas somente após validar o período
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Verifica se os dados necessários para ativar estão completos
    private void validarDadosAtivacao() {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O tratamento deve possuir um nome");
        }

        if (startDate == null) {
            throw new IllegalArgumentException("A data de início é obrigatória");
        }

        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("A data final não pode anteceder a inicial");
        }

        if (!possuiCronogramaValido()) {
            throw new IllegalArgumentException("O tratamento precisa de um medicamento com cronograma válido");
        }
    }

    // Verifica se pelo menos um medicamento possui cronograma válido
    private boolean possuiCronogramaValido() {

        for (MedicamentoEmUso medication : medications) {

            Optional<CronogramaDose> cronograma = medication.getActiveSchedule();

            if (cronograma.isPresent() && cronogramaValido(cronograma.get())) {
                return true;
            }
        }

        return false;
    }


    // Verifica os campos básicos e a vigência de um cronograma
    private boolean cronogramaValido(CronogramaDose cronograma) {

        if (cronograma == null || !cronograma.isActive()) {
            return false;
        }

        try {
            LocalDate inicio = LocalDate.parse(cronograma.getStartDate());

            LocalDate fim = cronograma.getEndDate() == null ? null : LocalDate.parse(cronograma.getEndDate());

            if (fim != null && fim.isBefore(inicio)) {
                return false;
            }

            // Verifica se o fuso horário existe
            ZoneId.of(cronograma.getTimezone());

            // Verifica os horários e impede repetições
            // Set é uma interface que representa uma coleção de elementos sem duplicatas !!!
            Set<LocalTime> horarios = new HashSet<>();

            if (cronograma.getTimes() == null || cronograma.getTimes().isEmpty()) {
                return false;
            }

            for (String horario : cronograma.getTimes()) {
                // verificando se a String possui o formato HH:mm, como "08:00" ou "20:30"
                if (horario == null || !horario.matches("\\d{2}:\\d{2}")) {
                    return false;
                }

                LocalTime hora = LocalTime.parse(horario);

                if (!horarios.add(hora)) {
                    return false;
                }
            }

            // null representa recorrência diária no modelo atual
            List<String> dias = cronograma.getDaysOfWeek();
            Set<DayOfWeek> diasSemana = new HashSet<>();

            if (dias != null) {

                if (dias.isEmpty()) {
                    return false;
                }

                for (String dia : dias) {

                    if (dia == null) {
                        return false;
                    }

                    DayOfWeek diaSemana = DayOfWeek.valueOf(dia);

                    if (!diasSemana.add(diaSemana)) {
                        return false;
                    }
                }
            }

            // Calcula a interseção entre tratamento e cronograma
            LocalDate inicioEfetivo = inicio.isAfter(startDate) ? inicio : startDate;

            LocalDate fimEfetivo = endDate;

            if (fim != null && (fimEfetivo == null || fim.isBefore(fimEfetivo))) {
                fimEfetivo = fim;
            }

            if (fimEfetivo != null && fimEfetivo.isBefore(inicioEfetivo)) {
                return false;
            }

            // Cronograma diário possui ocorrência na vigência
            if (dias == null) {
                return true;
            }

            // Em até 7 dias, verifica se algum dia escolhido ocorre
            for (int i = 0; i < 7; i++) {

                LocalDate data = inicioEfetivo.plusDays(i);

                if (fimEfetivo != null && data.isAfter(fimEfetivo)) {
                    break;
                }

                if (diasSemana.contains(data.getDayOfWeek())) {
                    return true;
                }
            }

            return false;

            // Trata datas, horários e dias da semana inválidos
        } catch (DateTimeException | IllegalArgumentException e) {
            return false;
        }
    }


    // Cria uma cópia do medicamento para proteger os dados internos
    private MedicamentoEmUso copiarMedicamento(MedicamentoEmUso medication) {
        return new MedicamentoEmUso(
                medication.getId(),
                medication.getName(),
                medication.getDose(),
                medication.getQuantity(),
                medication.getUnit(),
                medication.getObservations(),
                medication.isActive(),
                medication.getSchedules(),
                medication.getRevision(),
                medication.getDeactivatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Treatment)) return false;

        Treatment treatment = (Treatment) o;

        return Objects.equals(id, treatment.id)
                && Objects.equals(userId, treatment.userId)
                && Objects.equals(name, treatment.name)
                && Objects.equals(description, treatment.description)
                && Objects.equals(startDate, treatment.startDate)
                && Objects.equals(endDate, treatment.endDate)
                && status == treatment.status
                && Objects.equals(closedAt, treatment.closedAt)
                && Objects.equals(medications, treatment.medications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, userId, name, description,
                startDate, endDate, status, closedAt, medications
        );
    }


    @Override
    public String toString() {
        return "Treatment{"
                + "id='" + id + '\''
                + ", userId='" + userId + '\''
                + ", name='" + name + '\''
                + ", description='" + description + '\''
                + ", startDate=" + startDate
                + ", endDate=" + endDate
                + ", status=" + status
                + ", closedAt=" + closedAt
                + ", medications=" + medications
                + '}';
    }

    public TreatmentStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

}

