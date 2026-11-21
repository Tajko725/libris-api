package pl.tajko.library.api.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.AbstractIntegrationTest;
import pl.tajko.library.api.book.Book;
import pl.tajko.library.api.book.BookItem;
import pl.tajko.library.api.book.BookItemRepository;
import pl.tajko.library.api.book.BookRepository;
import pl.tajko.library.api.book.Genre;
import pl.tajko.library.api.reader.Reader;
import pl.tajko.library.api.reader.ReaderRepository;

/**
 * Testy integracyjne repozytorium wypożyczeń z prawdziwą bazą PostgreSQL: mapowanie, zapytania,
 * jedno aktywne wypożyczenie na egzemplarz, ochrona historii (RESTRICT), ograniczenia bazy i wersjonowanie.
 */
@Transactional
class LoanRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final Instant BORROWED_AT = Instant.parse("2026-11-21T10:00:00Z");
    private static final Instant RETURNED_AT = Instant.parse("2026-11-25T12:00:00Z");
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 12, 21);

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BookItemRepository bookItemRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Book book;
    private Reader anna;
    private Reader jan;

    /**
     * Czyści tabele w kolejności zgodnej z kluczami obcymi (zmiany są wycofywane po teście)
     * i przygotowuje książkę oraz dwóch czytelników.
     */
    @BeforeEach
    void setUp() {
        loanRepository.deleteAllInBatch();
        bookItemRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        readerRepository.deleteAllInBatch();

        book = bookRepository.save(new Book("Czysty kod", "Robert C. Martin", 2008, "9780132350884", Genre.SCIENCE));
        anna = readerRepository.save(new Reader("Anna", "Nowak", "anna.nowak@example.com", null));
        jan = readerRepository.save(new Reader("Jan", "Kowalski", "jan.kowalski@example.com", null));
    }

    // ---- pomocnicze ----

    private BookItem newItem() {
        return bookItemRepository.save(new BookItem(book));
    }

    private Loan saveLoan(BookItem item, Reader reader, LocalDate dueDate) {
        return loanRepository.saveAndFlush(new Loan(item, reader, BORROWED_AT, dueDate));
    }

    private Loan saveLoan(BookItem item, Reader reader) {
        return saveLoan(item, reader, DUE_DATE);
    }

    private Loan saveReturnedLoan(BookItem item, Reader reader, LocalDate dueDate) {
        Loan loan = new Loan(item, reader, BORROWED_AT, dueDate);
        loan.markReturned(RETURNED_AT);
        return loanRepository.saveAndFlush(loan);
    }

    // ---- zapis i odczyt ----

    /**
     * Zapisane wypożyczenie wraca z bazy ze wszystkimi polami, powiązaniami i wersją 0.
     */
    @Test
    void shouldSaveAndLoadAllFields() {
        BookItem item = newItem();
        Loan saved = saveLoan(item, anna);
        entityManager.clear();

        Loan loaded = loanRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getBookItem().getId()).isEqualTo(item.getId());
        assertThat(loaded.getReader().getId()).isEqualTo(anna.getId());
        assertThat(loaded.getBorrowedAt()).isEqualTo(BORROWED_AT);
        assertThat(loaded.getDueDate()).isEqualTo(DUE_DATE);
        assertThat(loaded.getReturnedAt()).isNull();
        assertThat(loaded.getRenewals()).isZero();
        assertThat(loaded.getVersion()).isZero();
    }

    /**
     * Zwrot i przedłużenie są zapisywane w bazie.
     */
    @Test
    void shouldPersistReturnAndRenewal() {
        Loan renewed = saveLoan(newItem(), anna);
        renewed.renew(LocalDate.of(2026, 11, 22), 14);
        loanRepository.saveAndFlush(renewed);

        Loan returned = saveLoan(newItem(), jan);
        returned.markReturned(RETURNED_AT);
        loanRepository.saveAndFlush(returned);
        entityManager.clear();

        Loan loadedRenewed = loanRepository.findById(renewed.getId()).orElseThrow();
        assertThat(loadedRenewed.getDueDate()).isEqualTo(DUE_DATE.plusDays(14));
        assertThat(loadedRenewed.getRenewals()).isEqualTo(1);

        Loan loadedReturned = loanRepository.findById(returned.getId()).orElseThrow();
        assertThat(loadedReturned.getReturnedAt()).isEqualTo(RETURNED_AT);
        assertThat(loadedReturned.isReturned()).isTrue();
    }

    // ---- jedno aktywne wypożyczenie na egzemplarz ----

    /**
     * Drugie aktywne wypożyczenie tego samego egzemplarza jest odrzucane przez bazę.
     */
    @Test
    void shouldRejectSecondActiveLoanOfSameItem() {
        BookItem item = newItem();
        saveLoan(item, anna);

        assertThatThrownBy(() -> saveLoan(item, jan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Po zwrocie egzemplarz można wypożyczyć ponownie, a historia obu wypożyczeń zostaje.
     */
    @Test
    void shouldAllowBorrowingAgainAfterReturnAndKeepHistory() {
        BookItem item = newItem();
        saveReturnedLoan(item, anna, DUE_DATE);

        saveLoan(item, jan);

        assertThat(loanRepository.count()).isEqualTo(2);
        assertThat(loanRepository.findByBookItemIdAndReturnedAtIsNull(item.getId()))
                .get().extracting(loan -> loan.getReader().getId()).isEqualTo(jan.getId());
    }

    /**
     * Różne egzemplarze tej samej książki mogą być wypożyczone naraz, a jeden czytelnik może mieć wiele wypożyczeń.
     */
    @Test
    void shouldAllowManyActiveLoansForDifferentItemsAndSameReader() {
        saveLoan(newItem(), anna);
        saveLoan(newItem(), anna);
        saveLoan(newItem(), jan);

        assertThat(loanRepository.count()).isEqualTo(3);
        assertThat(loanRepository.countByReaderIdAndReturnedAtIsNull(anna.getId())).isEqualTo(2);
    }

    // ---- zapytania ----

    /**
     * Aktywne wypożyczenie egzemplarza jest znajdowane, a po zwrocie egzemplarz jest wolny.
     */
    @Test
    void shouldFindActiveLoanOnlyUntilReturned() {
        BookItem borrowed = newItem();
        BookItem free = newItem();
        Loan loan = saveLoan(borrowed, anna);

        assertThat(loanRepository.findByBookItemIdAndReturnedAtIsNull(borrowed.getId())).isPresent();
        assertThat(loanRepository.existsByBookItemIdAndReturnedAtIsNull(borrowed.getId())).isTrue();
        assertThat(loanRepository.existsByBookItemIdAndReturnedAtIsNull(free.getId())).isFalse();
        assertThat(loanRepository.findByBookItemIdAndReturnedAtIsNull(free.getId())).isEmpty();

        loan.markReturned(RETURNED_AT);
        loanRepository.saveAndFlush(loan);

        assertThat(loanRepository.existsByBookItemIdAndReturnedAtIsNull(borrowed.getId())).isFalse();
    }

    /**
     * Historia (także zakończone wypożyczenia) jest widoczna dla egzemplarza i czytelnika.
     */
    @Test
    void shouldDetectLoanHistory() {
        BookItem withHistory = newItem();
        BookItem withoutHistory = newItem();
        saveReturnedLoan(withHistory, anna, DUE_DATE);

        assertThat(loanRepository.existsByBookItemId(withHistory.getId())).isTrue();
        assertThat(loanRepository.existsByBookItemId(withoutHistory.getId())).isFalse();
        assertThat(loanRepository.existsByReaderId(anna.getId())).isTrue();
        assertThat(loanRepository.existsByReaderId(jan.getId())).isFalse();
    }

    /**
     * Do limitu liczą się tylko aktywne wypożyczenia danego czytelnika.
     */
    @Test
    void shouldCountOnlyActiveLoansOfGivenReader() {
        saveLoan(newItem(), anna);
        saveLoan(newItem(), anna);
        saveReturnedLoan(newItem(), anna, DUE_DATE);
        saveLoan(newItem(), jan);

        assertThat(loanRepository.countByReaderIdAndReturnedAtIsNull(anna.getId())).isEqualTo(2);
        assertThat(loanRepository.countByReaderIdAndReturnedAtIsNull(jan.getId())).isEqualTo(1);
    }

    /**
     * Zaległość to niezwrócone wypożyczenie z terminem wcześniejszym niż dziś: w dniu terminu jeszcze
     * nie, po terminie tak, zwrócone nie, a cudze wypożyczenia nie liczą się.
     */
    @Test
    void shouldDetectOverdueLoansOfReader() {
        saveLoan(newItem(), anna, DUE_DATE);
        saveReturnedLoan(newItem(), jan, LocalDate.of(2026, 12, 1));

        assertThat(loanRepository.existsByReaderIdAndReturnedAtIsNullAndDueDateBefore(
                anna.getId(), DUE_DATE.minusDays(1))).isFalse();
        assertThat(loanRepository.existsByReaderIdAndReturnedAtIsNullAndDueDateBefore(
                anna.getId(), DUE_DATE)).isFalse();
        assertThat(loanRepository.existsByReaderIdAndReturnedAtIsNullAndDueDateBefore(
                anna.getId(), DUE_DATE.plusDays(1))).isTrue();
        assertThat(loanRepository.existsByReaderIdAndReturnedAtIsNullAndDueDateBefore(
                jan.getId(), LocalDate.of(2027, 1, 1))).isFalse();
    }

    // ---- ochrona historii ----

    /**
     * Egzemplarza z wypożyczeniem (także zakończonym) nie da się usunąć. Kontekst persystencji jest
     * czyszczony przed usunięciem, żeby błąd zgłosiła baza (klucz obcy), a nie Hibernate.
     */
    @Test
    void shouldNotAllowDeletingItemWithLoanHistory() {
        BookItem item = newItem();
        saveReturnedLoan(item, anna, DUE_DATE);
        entityManager.clear();

        assertThatThrownBy(() -> {
            bookItemRepository.deleteById(item.getId());
            bookItemRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Czytelnika z wypożyczeniem (także zakończonym) nie da się usunąć. Kontekst persystencji jest
     * czyszczony przed usunięciem, żeby błąd zgłosiła baza (klucz obcy), a nie Hibernate.
     */
    @Test
    void shouldNotAllowDeletingReaderWithLoanHistory() {
        saveReturnedLoan(newItem(), anna, DUE_DATE);
        entityManager.clear();

        assertThatThrownBy(() -> {
            readerRepository.deleteById(anna.getId());
            readerRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Egzemplarz i czytelnik bez wypożyczeń nadal można usunąć.
     */
    @Test
    void shouldStillAllowDeletingItemAndReaderWithoutLoans() {
        BookItem item = newItem();

        bookItemRepository.delete(item);
        readerRepository.delete(jan);
        bookItemRepository.flush();

        assertThat(bookItemRepository.findById(item.getId())).isEmpty();
        assertThat(readerRepository.findById(jan.getId())).isEmpty();
    }

    // ---- ograniczenia bazy ----

    static Stream<Arguments> invalidRows() {
        return Stream.of(
                Arguments.of("termin zwrotu przed dniem wypożyczenia", -1, null, 0),
                Arguments.of("zwrot przed wypożyczeniem", 30, -1, 0),
                Arguments.of("ujemna liczba przedłużeń", 30, null, -1));
    }

    /**
     * Baza odrzuca wiersze łamiące ograniczenia, nawet gdy ktoś omija encję i wstawia dane przez SQL.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRows")
    void shouldRejectRowsViolatingDatabaseConstraints(
            String caseName, int dueOffsetDays, Integer returnedOffsetHours, int renewals) {
        BookItem item = newItem();
        OffsetDateTime borrowed = OffsetDateTime.ofInstant(BORROWED_AT, ZoneOffset.UTC);
        LocalDate due = LocalDate.of(2026, 11, 21).plusDays(dueOffsetDays);
        OffsetDateTime returned = returnedOffsetHours == null ? null : borrowed.plusHours(returnedOffsetHours);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO loan (book_item_id, reader_id, borrowed_at, due_date, returned_at, renewals) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                item.getId(), anna.getId(), borrowed, due, returned, renewals))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Poprawny wiersz wstawiony przez SQL jest przyjmowany (sprawdza, że powyższe odrzucenia
     * wynikają z ograniczeń, a nie z błędu w samym poleceniu).
     */
    @Test
    void shouldAcceptValidRowInsertedWithSql() {
        BookItem item = newItem();

        jdbcTemplate.update(
                "INSERT INTO loan (book_item_id, reader_id, borrowed_at, due_date) VALUES (?, ?, ?, ?)",
                item.getId(), anna.getId(), OffsetDateTime.ofInstant(BORROWED_AT, ZoneOffset.UTC), DUE_DATE);

        assertThat(loanRepository.existsByBookItemIdAndReturnedAtIsNull(item.getId())).isTrue();
    }

    // ---- wersja ----

    /**
     * Każda zmiana zwiększa wersję o 1.
     */
    @Test
    void shouldIncrementVersionOnUpdate() {
        Loan loan = saveLoan(newItem(), anna);

        loan.markReturned(RETURNED_AT);
        loanRepository.saveAndFlush(loan);
        entityManager.clear();

        assertThat(loanRepository.findById(loan.getId()).orElseThrow().getVersion()).isEqualTo(1);
    }

    /**
     * Zapis na podstawie nieaktualnej wersji (ktoś zmienił wiersz w międzyczasie) jest odrzucany.
     */
    @Test
    void shouldRejectStaleUpdate() {
        Loan loan = saveLoan(newItem(), anna);
        jdbcTemplate.update("UPDATE loan SET version = version + 1 WHERE id = ?", loan.getId());

        loan.markReturned(RETURNED_AT);

        assertThatThrownBy(() -> loanRepository.flush())
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}