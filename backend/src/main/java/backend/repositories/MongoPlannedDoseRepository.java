package backend.repositories;

import backend.models.DosePrevista;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class MongoPlannedDoseRepository implements PlannedDoseRepository{
    @Override
    public DosePrevista save(DosePrevista dose) {
        return null;
    }

    @Override
    public List<DosePrevista> saveAll(List<DosePrevista> doses) {
        return List.of();
    }

    @Override
    public Optional<DosePrevista> findById(String id) {
        return Optional.empty();
    }

    @Override
    public List<DosePrevista> findByUserAndDate(String user, LocalDate data) {
        return List.of();
    }

    @Override
    public List<DosePrevista> findByTreatmentAndPeriod(String treatmentId, Instant inicio, Instant fim) {
        return List.of();
    }
}
