package pl.tajko.library.api.loan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Stan wypożyczenia wyliczany z daty zwrotu i terminu, a nie zapisywany w bazie (nie może się rozjechać z danymi).
 */
@Schema(description = "Stan wypożyczenia: ACTIVE (trwa), OVERDUE (trwa po terminie zwrotu), RETURNED (zakończone)")
public enum LoanStatus {

    /** Książka jest wypożyczona i termin zwrotu jeszcze nie minął (włącznie z dniem terminu). */
    ACTIVE,

    /** Książka nie wróciła, a termin zwrotu minął. */
    OVERDUE,

    /** Książka została zwrócona. */
    RETURNED;

    /**
     * Wylicza stan wypożyczenia.
     *
     * @param loan  wypożyczenie
     * @param today dzisiejsza data
     * @return stan wypożyczenia
     */
    public static LoanStatus of(Loan loan, LocalDate today) {
        if (loan.isReturned()) {
            return RETURNED;
        }
        return loan.isOverdue(today) ? OVERDUE : ACTIVE;
    }
}
