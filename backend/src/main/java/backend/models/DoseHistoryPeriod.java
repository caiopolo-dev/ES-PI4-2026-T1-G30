// Desenvolvido por Miguel Gallinucci.
package backend.models;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Periodo local inclusivo convertido para a consulta das doses previstas. */
public record DoseHistoryPeriod(LocalDate from, LocalDate to) {

    // Limitei o periodo para evitar que uma consulta recupere um historico muito extenso de uma vez.
    public static final int MAX_DAYS = 90;
    // Fixei o fuso no contrato para a mesma data nao mudar conforme o computador que executa o servidor.
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    // ZoneId identifica um fuso e suas regras; usei para converter os dias locais sem fixar um deslocamento UTC.

    public DoseHistoryPeriod {

        if (from == null || to == null) {
            throw new IllegalArgumentException("Datas inicial e final obrigatorias.");
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Data inicial posterior a final.");
        }

        // Somei um porque a data inicial e a final entram na contagem dos 90 dias.
        // LocalDate.MAX foi bloqueado porque nao permite calcular o dia seguinte.
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS || to.equals(LocalDate.MAX)) {
            throw new IllegalArgumentException("Periodo invalido ou superior a 90 dias.");
        }
    }

    public static DoseHistoryPeriod parse(String from, String to) {

        return new DoseHistoryPeriod(parseDate(from), parseDate(to));
    }

    private static LocalDate parseDate(String value) {

        // Conferi o formato antes de converter para rejeitar datas fora do contrato.
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Data obrigatoria no formato yyyy-MM-dd.");
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Data inexistente.", invalid);
        }
    }

    public Instant startInclusive() {
        // Converto o inicio do dia local para um instante que pode ser comparado com as doses persistidas.

        return from.atStartOfDay(ZONE).toInstant();
    }

    public Instant endExclusive() {
        // O inicio do dia seguinte fica de fora; assim, incluo todo o ultimo dia sem calcular seu ultimo segundo.

        // Usei o proximo dia local porque um dia com horario de verao pode nao ter 24 horas.
        return to.plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}
