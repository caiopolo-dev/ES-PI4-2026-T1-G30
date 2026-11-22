// Desenvolvido por Miguel Gallinucci.
package backend.services;

import backend.models.Medication;
import backend.repositories.MedicationRepository;
import java.util.List;
import java.util.Objects;

/**
 * Executa os casos de uso relacionados aos medicamentos.
 */
public class MedicationService {
    private final MedicationRepository repository;

    public MedicationService(MedicationRepository repository) {
        // Injecao de dependencia e receber outro objeto pronto; isso permite trocar o repository nos testes.
        // Recebi a interface pelo construtor para permitir trocar o armazenamento sem mudar o servico.
        // Rejeitei null aqui para identificar uma configuracao incompleta antes de atender uma consulta.
        this.repository = Objects.requireNonNull(repository, "repository não pode ser null");
    }

    // MedicationHandler chama esta operacao; o resultado e a lista encontrada no repository.
    public List<Medication> listMedications() {
        // A consulta fica no repository; o servico oferece a operacao sem depender de HTTP ou sockets.
        return repository.findAll();
    }
}
