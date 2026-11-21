// Autor: Samuel Campovilla
package backend.services;

import backend.repositories.TreatmentRepository;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Executa periodicamente a reconciliação das doses previstas.
// Mantém a janela de geração e permite repetir operações
// que tenham falhado em execuções anteriores.
public class DoseReconciliationJob implements AutoCloseable {

    private static final int INTERVAL_MINUTES = 5;

    private final TreatmentRepository treatmentRepository;
    private final DoseGenerationService doseGenerationService;

    // Cria um executor responsável por executar tarefas agendadas.
    // Utilizamos apenas uma thread para evitar ciclos simultâneos.
    private final ScheduledExecutorService executor;

    private boolean started;

    public DoseReconciliationJob(
            TreatmentRepository treatmentRepository,
            DoseGenerationService doseGenerationService) {

        this.treatmentRepository = Objects.requireNonNull(treatmentRepository);
        this.doseGenerationService = Objects.requireNonNull(doseGenerationService);

        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.started = false;
    }

    // Inicia a tarefa periódica apenas uma vez.
    public synchronized void start() {

        if (started) {
            return;
        }

        // Executa a primeira reconciliação imediatamente.
        // Aguarda cinco minutos após o término de cada execução.
        executor.scheduleWithFixedDelay(
                this::runOnce,
                0,
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        started = true;
    }

    // Executa um ciclo de reconciliação de todos os tratamentos elegíveis.
    public void runOnce() {

        try {

            var tratamentos = treatmentRepository.findDoseReconciliationTargets();

            for (var tratamento : tratamentos) {

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                try {

                    doseGenerationService.reconcile(
                            tratamento.userId(),
                            tratamento.treatmentId()
                    );

                } catch (RuntimeException erro) {

                    // Uma falha em um tratamento não deve impedir
                    // a reconciliação dos demais.
                    System.err.println(
                            "Falha na reconciliação de doses: "
                                    + erro.getClass().getSimpleName()
                    );
                }
            }

        } catch (RuntimeException erro) {

            // Evita que uma falha na consulta encerre
            // permanentemente a tarefa agendada.
            System.err.println(
                    "Falha no ciclo de reconciliação: "
                            + erro.getClass().getSimpleName()
            );
        }
    }

    // Encerra o executor quando o servidor for finalizado.
    @Override
    public void close() {

        executor.shutdownNow();
        executor.close();
    }
}
