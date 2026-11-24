// Autor: Samuel Campovilla
package backend.services;

import backend.auth.AuthContext;
import backend.models.Treatment;
import backend.models.TreatmentStatus;
import backend.repositories.TreatmentRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;


// Responsável pelas operações gerais dos tratamentos.
// Utiliza o modelo Treatment para validar os estados e
// o repository para salvar as alterações.

public class TreatmentService {

    private final TreatmentRepository repository;
    // Serviço responsável por verificar se o tratamento pertence
    // ao usuário autenticado, impedindo acessos não autorizados.
    private final TreatmentAccessService accessService;
    // Clock é uma classe abstrata do pacote java.time.
    // Utilizamos para obter o instante atual de forma controlada.
    // Isso facilita os testes, pois podemos simular uma data fixa.
    private final Clock clock;

    private final DoseGenerationService doseGenerationService;


    public TreatmentService(TreatmentRepository repository) {
        this(repository, Clock.systemUTC(), null);
    }

    public TreatmentService(TreatmentRepository repository, Clock clock) {
        this(repository, clock, null);
    }

    public TreatmentService(
            TreatmentRepository repository,
            Clock clock,
            DoseGenerationService doseGenerationService) {

        this.repository = Objects.requireNonNull(repository);
        this.accessService = new TreatmentAccessService(repository);
        this.clock = Objects.requireNonNull(clock);
        this.doseGenerationService = doseGenerationService;
    }



    // Cria um tratamento em DRAFT associado ao usuário autenticado
    public Treatment createDraft(
            AuthContext context,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate) {

        // O userId deve vir da sessão autenticada
        String userId = Objects.requireNonNull(context).userId();

        Treatment treatment = new Treatment(
                userId,
                name,
                description,
                startDate,
                endDate
        );

        return repository.save(treatment);
    }


    // Lista somente os tratamentos do usuário autenticado
    public List<Treatment> list(AuthContext context) {
        return accessService.list(context);
    }


    // Consulta um tratamento verificando sua titularidade
    public Treatment find(AuthContext context, String treatmentId) {
        return accessService.find(context, treatmentId);
    }


    // Atualiza os metadados permitidos do tratamento
    public Treatment updateMetadata(
            AuthContext context,
            String treatmentId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate) {
        // synchronized evita que uma atualização geral do tratamento dispute a persistência com uma alteração de cronograma
        synchronized (TreatmentMutationLock.MONITOR) {
            Treatment treatment = accessService.find(context, treatmentId);

            // Tratamentos encerrados não podem ser alterados
            if (treatment.getStatus() == TreatmentStatus.CLOSED) {
                throw new IllegalStateException(
                        "Tratamento encerrado não pode ser alterado");
            }

            if (treatment.getStatus() == TreatmentStatus.ACTIVE) {

                // Um tratamento ativo não pode alterar seu período
                if (!Objects.equals(treatment.getStartDate(), startDate)
                        || !Objects.equals(treatment.getEndDate(), endDate)) {
                    throw new IllegalStateException(
                            "Não é permitido alterar o período de um tratamento ativo");
                }

                // Tratamentos ativos precisam manter um nome válido
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException(
                            "Tratamento ativo deve possuir nome");
                }

            } else {

                // Em DRAFT, o período planejado pode ser atualizado
                treatment.updatePeriod(startDate, endDate);
            }

            treatment.setName(name);
            treatment.setDescription(description);

            return accessService.update(context, treatment);
        }
    }

    // Ativa um tratamento após validar suas informações
    // e materializa suas doses previstas.
    public Treatment activate(AuthContext context, String treatmentId) {

        synchronized (TreatmentMutationLock.MONITOR) {

            Treatment treatment = accessService.find(context, treatmentId);

            // O modelo verifica se a transição é permitida.
            treatment.activate();

            // Persiste o novo estado antes de gerar as ocorrências.
            Treatment atualizado = accessService.update(context, treatment);

            reconciliarDoses(atualizado);

            return atualizado;
        }
    }


    // Encerra um tratamento sem apagar seu histórico.
    public Treatment close(AuthContext context, String treatmentId) {

        synchronized (TreatmentMutationLock.MONITOR) {

            Treatment treatment = accessService.find(context, treatmentId);

            // Registra o instante atual do encerramento.
            treatment.close(clock.instant());

            Treatment atualizado = accessService.update(context, treatment);

            // Cancela as doses futuras do tratamento encerrado.
            reconciliarDoses(atualizado);

            return atualizado;
        }
    }

    // Atualiza as doses previstas quando o gerador está conectado.
    private void reconciliarDoses(Treatment treatment) {

        if (doseGenerationService != null) {
            doseGenerationService.reconcile(
                    treatment.getUserId(),
                    treatment.getId()
            );
        }
    }

}
