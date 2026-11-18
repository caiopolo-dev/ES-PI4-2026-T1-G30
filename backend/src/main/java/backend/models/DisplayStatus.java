package backend.models;

/**
 * Estado derivado de apresentação de uma dose na agenda diária.
 *
 * <ul>
 *   <li>UPCOMING: Dose agendada para um horário futuro.</li>
 *   <li>DUE: Dose agendada dentro da janela atual de administração.</li>
 *   <li>OVERDUE: Dose cujo horário previsto já passou e permanece sem registro de ação (pendente/vencida).</li>
 *   <li>TAKEN: Dose com registro explícito de tomada pelo usuário.</li>
 *   <li>SKIPPED: Dose com registro explícito de não tomada pelo usuário.</li>
 * </ul>
 */
public enum DisplayStatus {
    UPCOMING,
    DUE,
    OVERDUE,
    TAKEN,
    SKIPPED
}
