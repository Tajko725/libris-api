package pl.tajko.library.api.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tajko.library.api.book.Book;
import pl.tajko.library.api.book.BookItem;
import pl.tajko.library.api.book.Genre;
import pl.tajko.library.api.reader.Reader;

/**
 * Testy jednostkowe reguł encji {@link Loan}: tworzenie, zwrot, przedłużanie i przeterminowanie.
 */
class LoanTest {

    private static final Instant BORROWED_AT = Instant.parse("2026-11-21T10:00:00Z");
    private static final LocalDate BORROWED_DAY = LocalDate.of(2026, 11, 21);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 12, 21);

    private static BookItem item() {
        return new BookItem(new Book("Czysty kod", "Robert C. Martin", 2008, "9780132350884", Genre.SCIENCE));
    }

    private static Reader reader() {
        return new Reader("Anna", "Nowak", "anna.nowak@example.com", null);
    }

    private static Loan loan() {
        return new Loan(item(), reader(), BORROWED_AT, DUE_DATE);
    }

    // ---- tworzenie ----

    /**
     * Nowe wypożyczenie jest aktywne, nieprzedłużone i bez identyfikatora.
     */
    @Test
    void shouldCreateActiveLoan() {
        Loan loan = loan();

        assertThat(loan.getId()).isNull();
        assertThat(loan.getBorrowedAt()).isEqualTo(BORROWED_AT);
        assertThat(loan.getDueDate()).isEqualTo(DUE_DATE);
        assertThat(loan.getReturnedAt()).isNull();
        assertThat(loan.isReturned()).isFalse();
        assertThat(loan.getRenewals()).isZero();
        assertThat(loan.getBookItem()).isNotNull();
        assertThat(loan.getReader()).isNotNull();
    }

    /**
     * Termin zwrotu w dniu wypożyczenia jest dozwolony.
     */
    @Test
    void shouldAcceptDueDateEqualToBorrowDay() {
        assertThat(new Loan(item(), reader(), BORROWED_AT, BORROWED_DAY).getDueDate()).isEqualTo(BORROWED_DAY);
    }

    /**
     * Puste argumenty są odrzucane.
     */
    @Test
    void shouldRejectMissingArguments() {
        assertThatThrownBy(() -> new Loan(null, reader(), BORROWED_AT, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Egzemplarz");
        assertThatThrownBy(() -> new Loan(item(), null, BORROWED_AT, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Czytelnik");
        assertThatThrownBy(() -> new Loan(item(), reader(), null, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("wypożyczenia");
        assertThatThrownBy(() -> new Loan(item(), reader(), BORROWED_AT, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Termin");
    }

    /**
     * Nieaktywny czytelnik nie może wypożyczać.
     */
    @Test
    void shouldRejectInactiveReader() {
        Reader inactive = reader();
        inactive.setActive(false);

        assertThatThrownBy(() -> new Loan(item(), inactive, BORROWED_AT, DUE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nieaktywny");
    }

    /**
     * Termin zwrotu przed dniem wypożyczenia jest odrzucany.
     */
    @Test
    void shouldRejectDueDateBeforeBorrowDay() {
        assertThatThrownBy(() -> new Loan(item(), reader(), BORROWED_AT, BORROWED_DAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wcześniejszy");
    }

    // ---- przeterminowanie ----

    /**
     * Wypożyczenie nie jest przeterminowane do dnia terminu włącznie, a od następnego dnia jest.
     */
    @Test
    void shouldBeOverdueOnlyAfterDueDate() {
        Loan loan = loan();

        assertThat(loan.isOverdue(DUE_DATE.minusDays(1))).isFalse();
        assertThat(loan.isOverdue(DUE_DATE)).isFalse();
        assertThat(loan.isOverdue(DUE_DATE.plusDays(1))).isTrue();
    }

    /**
     * Zwrócone wypożyczenie nigdy nie jest przeterminowane, nawet po terminie.
     */
    @Test
    void shouldNotBeOverdueWhenReturned() {
        Loan loan = loan();
        loan.markReturned(BORROWED_AT.plusSeconds(3600));

        assertThat(loan.isOverdue(DUE_DATE.plusDays(100))).isFalse();
    }

    // ---- zwrot ----

    /**
     * Zwrot ustawia datę zwrotu i oznacza wypożyczenie jako zwrócone.
     */
    @Test
    void shouldMarkLoanAsReturned() {
        Loan loan = loan();
        Instant returnedAt = BORROWED_AT.plusSeconds(86_400);

        loan.markReturned(returnedAt);

        assertThat(loan.isReturned()).isTrue();
        assertThat(loan.getReturnedAt()).isEqualTo(returnedAt);
    }

    /**
     * Zwrot w tej samej chwili co wypożyczenie jest dozwolony.
     */
    @Test
    void shouldAcceptReturnAtBorrowMoment() {
        Loan loan = loan();

        loan.markReturned(BORROWED_AT);

        assertThat(loan.isReturned()).isTrue();
    }

    /**
     * Drugi zwrot tego samego wypożyczenia jest odrzucany i nie zmienia daty pierwszego.
     */
    @Test
    void shouldRejectSecondReturn() {
        Loan loan = loan();
        Instant firstReturn = BORROWED_AT.plusSeconds(60);
        loan.markReturned(firstReturn);

        assertThatThrownBy(() -> loan.markReturned(BORROWED_AT.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("już zwrócone");
        assertThat(loan.getReturnedAt()).isEqualTo(firstReturn);
    }

    /**
     * Pusta data zwrotu lub data przed wypożyczeniem są odrzucane, a wypożyczenie zostaje aktywne.
     */
    @Test
    void shouldRejectInvalidReturnDate() {
        Loan loan = loan();

        assertThatThrownBy(() -> loan.markReturned(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loan.markReturned(BORROWED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wcześniejsza");
        assertThat(loan.isReturned()).isFalse();
    }

    // ---- przedłużanie ----

    /**
     * Przedłużenie przesuwa termin o podaną liczbę dni i zwiększa licznik przedłużeń.
     */
    @Test
    void shouldRenewLoan() {
        Loan loan = loan();

        loan.renew(BORROWED_DAY.plusDays(10), 14);

        assertThat(loan.getDueDate()).isEqualTo(DUE_DATE.plusDays(14));
        assertThat(loan.getRenewals()).isEqualTo(1);
    }

    /**
     * Przedłużenie w dniu terminu jest jeszcze dozwolone.
     */
    @Test
    void shouldAllowRenewalOnDueDate() {
        Loan loan = loan();

        loan.renew(DUE_DATE, 14);

        assertThat(loan.getDueDate()).isEqualTo(DUE_DATE.plusDays(14));
    }

    /**
     * Drugie przedłużenie jest odrzucane i nie zmienia terminu.
     */
    @Test
    void shouldRejectSecondRenewal() {
        Loan loan = loan();
        loan.renew(BORROWED_DAY.plusDays(10), 14);
        LocalDate dueAfterFirst = loan.getDueDate();

        assertThatThrownBy(() -> loan.renew(BORROWED_DAY.plusDays(11), 14))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("już przedłużone");
        assertThat(loan.getDueDate()).isEqualTo(dueAfterFirst);
        assertThat(loan.getRenewals()).isEqualTo(Loan.MAX_RENEWALS);
    }

    /**
     * Po terminie zwrotu nie można przedłużyć wypożyczenia.
     */
    @Test
    void shouldRejectRenewalAfterDueDate() {
        Loan loan = loan();

        assertThatThrownBy(() -> loan.renew(DUE_DATE.plusDays(1), 14))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("po terminie");
        assertThat(loan.getDueDate()).isEqualTo(DUE_DATE);
        assertThat(loan.getRenewals()).isZero();
    }

    /**
     * Zwróconego wypożyczenia nie można przedłużyć.
     */
    @Test
    void shouldRejectRenewalOfReturnedLoan() {
        Loan loan = loan();
        loan.markReturned(BORROWED_AT.plusSeconds(60));

        assertThatThrownBy(() -> loan.renew(BORROWED_DAY, 14))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zwróconego");
    }

    /**
     * Liczba dni przedłużenia mniejsza niż 1 jest odrzucana.
     */
    @Test
    void shouldRejectNonPositiveRenewalDays() {
        Loan loan = loan();

        assertThatThrownBy(() -> loan.renew(BORROWED_DAY, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loan.renew(BORROWED_DAY, -3)).isInstanceOf(IllegalArgumentException.class);
        assertThat(loan.getRenewals()).isZero();
    }

    // ---- equals, hashCode, toString ----

    /**
     * Dwa niezapisane wypożyczenia nie są równe, a ten sam identyfikator oznacza równość.
     */
    @Test
    void shouldCompareLoansById() {
        Loan first = loan();
        Loan second = loan();
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(first);

        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 1L);
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);

        ReflectionTestUtils.setField(second, "id", 2L);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(null).isNotEqualTo("tekst");
    }

    /**
     * Opis tekstowy nie zawiera danych osobowych.
     */
    @Test
    void shouldNotExposePersonalDataInToString() {
        assertThat(loan().toString())
                .contains("dueDate=2026-12-21")
                .doesNotContain("Anna")
                .doesNotContain("Nowak")
                .doesNotContain("example.com");
    }
}