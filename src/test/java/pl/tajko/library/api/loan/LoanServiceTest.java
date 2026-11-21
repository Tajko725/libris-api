package pl.tajko.library.api.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tajko.library.api.book.Book;
import pl.tajko.library.api.book.BookItem;
import pl.tajko.library.api.book.BookItemRepository;
import pl.tajko.library.api.book.Genre;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.PageResponse;
import pl.tajko.library.api.common.ResourceNotFoundException;
import pl.tajko.library.api.reader.Reader;
import pl.tajko.library.api.reader.ReaderRepository;

/**
 * Testy jednostkowe logiki {@link LoanService} z zamockowanymi repozytoriami i stałym zegarem
 * (dzisiaj to 21.11.2026), więc terminy da się sprawdzać dokładnie.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final long ITEM_ID = 7L;
    private static final long READER_ID = 3L;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private BookItemRepository bookItemRepository;

    @Mock
    private ReaderRepository readerRepository;

    private LoanService loanService;

    /** Tworzy serwis ze stałym zegarem i zasadami: 30 dni, limit 5, przedłużenie o 14 dni. */
    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, bookItemRepository, readerRepository,
                new LoanProperties(30, 5, 14), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- pomocnicze ----

    private static BookItem item() {
        Book book = new Book("Czysty kod", "Robert C. Martin", 2008, "9780132350884", Genre.SCIENCE);
        ReflectionTestUtils.setField(book, "id", 1L);
        BookItem item = new BookItem(book);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    private static Reader reader(boolean active) {
        Reader reader = new Reader("Anna", "Nowak", "anna.nowak@example.com", null);
        reader.setActive(active);
        ReflectionTestUtils.setField(reader, "id", READER_ID);
        return reader;
    }

    private static Loan loan(long id, Instant borrowedAt, LocalDate dueDate) {
        Loan loan = new Loan(item(), reader(true), borrowedAt, dueDate);
        ReflectionTestUtils.setField(loan, "id", id);
        return loan;
    }

    private static LoanRequest request() {
        return new LoanRequest(ITEM_ID, READER_ID);
    }

    private void givenItemAndReader(boolean readerActive) {
        when(bookItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));
        when(readerRepository.findByIdForUpdate(READER_ID)).thenReturn(Optional.of(reader(readerActive)));
    }

    // ---- borrow ----

    /**
     * Wypożyczenie ustawia termin zwrotu na dziś plus 30 dni, zapisuje wypożyczenie i zwraca jego dane
     * z podsumowaniem czytelnika i książki.
     */
    @Test
    void shouldBorrowItemWithDueDateFromConfiguration() {
        givenItemAndReader(true);
        when(loanRepository.saveAndFlush(any(Loan.class))).thenAnswer(call -> call.getArgument(0));

        LoanResponse response = loanService.borrow(request());

        assertThat(response.status()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(response.borrowedAt()).isEqualTo(NOW);
        assertThat(response.dueDate()).isEqualTo(TODAY.plusDays(30));
        assertThat(response.returnedAt()).isNull();
        assertThat(response.renewals()).isZero();
        assertThat(response.book().title()).isEqualTo("Czysty kod");
        assertThat(response.book().id()).isEqualTo(1L);
        assertThat(response.book().bookItemId()).isEqualTo(ITEM_ID);
        assertThat(response.reader().id()).isEqualTo(READER_ID);
        assertThat(response.reader().lastName()).isEqualTo("Nowak");
        verify(loanRepository).saveAndFlush(any(Loan.class));
    }

    /**
     * Czytelnik z czterema wypożyczeniami (poniżej limitu 5) może wypożyczyć kolejną książkę.
     */
    @Test
    void shouldAllowBorrowingJustBelowLimit() {
        givenItemAndReader(true);
        when(loanRepository.countByReaderIdAndReturnedAtIsNull(READER_ID)).thenReturn(4L);
        when(loanRepository.saveAndFlush(any(Loan.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(loanService.borrow(request()).status()).isEqualTo(LoanStatus.ACTIVE);
    }

    /**
     * Czytelnik, który ma już 5 niezwróconych książek, nie może wypożyczyć kolejnej.
     */
    @Test
    void shouldRejectBorrowingWhenLimitIsReached() {
        givenItemAndReader(true);
        when(loanRepository.countByReaderIdAndReturnedAtIsNull(READER_ID)).thenReturn(5L);

        assertThatThrownBy(() -> loanService.borrow(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("limit")
                .hasMessageContaining("5");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    /**
     * Czytelnik z książką po terminie nie może wypożyczać, dopóki jej nie odda.
     */
    @Test
    void shouldRejectBorrowingWhenReaderHasOverdueLoan() {
        givenItemAndReader(true);
        when(loanRepository.existsByReaderIdAndReturnedAtIsNullAndDueDateBefore(READER_ID, TODAY)).thenReturn(true);

        assertThatThrownBy(() -> loanService.borrow(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("po terminie");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    /**
     * Nieaktywny czytelnik nie może wypożyczać.
     */
    @Test
    void shouldRejectBorrowingByInactiveReader() {
        givenItemAndReader(false);

        assertThatThrownBy(() -> loanService.borrow(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("nieaktywny");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    /**
     * Egzemplarz, który jest już wypożyczony, nie może być wypożyczony drugi raz.
     */
    @Test
    void shouldRejectBorrowingItemThatIsAlreadyBorrowed() {
        givenItemAndReader(true);
        when(loanRepository.existsByBookItemIdAndReturnedAtIsNull(ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> loanService.borrow(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("już wypożyczony");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    /**
     * Gdy dwa żądania wypożyczają ten sam egzemplarz naraz, przegrane (odrzucone przez bazę) dostaje
     * czytelny konflikt, a nie błąd serwera.
     */
    @Test
    void shouldTurnDatabaseDuplicateIntoConflict() {
        givenItemAndReader(true);
        when(loanRepository.saveAndFlush(any(Loan.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> loanService.borrow(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("już wypożyczony");
    }

    /**
     * Wypożyczenie nieistniejącego egzemplarza zgłasza "nie znaleziono" i nie rusza czytelnika.
     */
    @Test
    void shouldFailBorrowingMissingItem() {
        when(bookItemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.borrow(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("egzemplarza");
        verifyNoInteractions(readerRepository);
        verify(loanRepository, never()).saveAndFlush(any());
    }

    /**
     * Wypożyczenie przez nieistniejącego czytelnika zgłasza "nie znaleziono".
     */
    @Test
    void shouldFailBorrowingByMissingReader() {
        when(bookItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));
        when(readerRepository.findByIdForUpdate(READER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.borrow(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("czytelnika");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    // ---- giveBack ----

    /**
     * Zwrot ustawia datę zwrotu na teraz i zmienia stan na zwrócone.
     */
    @Test
    void shouldReturnLoan() {
        Loan loan = loan(1L, Instant.parse("2026-11-01T10:00:00Z"), LocalDate.of(2026, 12, 1));
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        LoanResponse response = loanService.giveBack(1L);

        assertThat(response.status()).isEqualTo(LoanStatus.RETURNED);
        assertThat(response.returnedAt()).isEqualTo(NOW);
        verify(loanRepository).saveAndFlush(loan);
    }

    /**
     * Przeterminowane wypożyczenie też można zwrócić (bez kar).
     */
    @Test
    void shouldReturnOverdueLoan() {
        Loan loan = loan(1L, Instant.parse("2026-09-01T10:00:00Z"), LocalDate.of(2026, 10, 1));
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        assertThat(loanService.giveBack(1L).status()).isEqualTo(LoanStatus.RETURNED);
    }

    /**
     * Drugi zwrot tego samego wypożyczenia jest konfliktem i nie zmienia daty pierwszego.
     */
    @Test
    void shouldRejectSecondReturn() {
        Loan loan = loan(1L, Instant.parse("2026-11-01T10:00:00Z"), LocalDate.of(2026, 12, 1));
        Instant firstReturn = Instant.parse("2026-11-10T10:00:00Z");
        loan.markReturned(firstReturn);
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.giveBack(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("już zwrócone");
        assertThat(loan.getReturnedAt()).isEqualTo(firstReturn);
        verify(loanRepository, never()).saveAndFlush(any());
    }

    /**
     * Zwrot nieistniejącego wypożyczenia zgłasza "nie znaleziono".
     */
    @Test
    void shouldFailReturningMissingLoan() {
        when(loanRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.giveBack(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- renew ----

    /**
     * Przedłużenie przesuwa termin o 14 dni i zwiększa licznik przedłużeń.
     */
    @Test
    void shouldRenewLoanByConfiguredDays() {
        Loan loan = loan(1L, Instant.parse("2026-11-01T10:00:00Z"), LocalDate.of(2026, 12, 1));
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        LoanResponse response = loanService.renew(1L);

        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(response.renewals()).isEqualTo(1);
        verify(loanRepository).saveAndFlush(loan);
    }

    /**
     * W dniu terminu zwrotu przedłużenie jest jeszcze możliwe.
     */
    @Test
    void shouldRenewOnDueDate() {
        Loan loan = loan(1L, Instant.parse("2026-10-22T10:00:00Z"), TODAY);
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        assertThat(loanService.renew(1L).dueDate()).isEqualTo(TODAY.plusDays(14));
    }

    /**
     * Drugie przedłużenie jest konfliktem.
     */
    @Test
    void shouldRejectSecondRenewal() {
        Loan loan = loan(1L, Instant.parse("2026-11-01T10:00:00Z"), LocalDate.of(2026, 12, 1));
        loan.renew(LocalDate.of(2026, 11, 10), 14);
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.renew(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("już przedłużone");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    /**
     * Po terminie zwrotu przedłużenie jest konfliktem.
     */
    @Test
    void shouldRejectRenewalAfterDueDate() {
        Loan loan = loan(1L, Instant.parse("2026-10-01T10:00:00Z"), LocalDate.of(2026, 11, 20));
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.renew(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("po terminie");
        assertThat(loan.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    /**
     * Zwróconego wypożyczenia nie można przedłużyć.
     */
    @Test
    void shouldRejectRenewalOfReturnedLoan() {
        Loan loan = loan(1L, Instant.parse("2026-11-01T10:00:00Z"), LocalDate.of(2026, 12, 1));
        loan.markReturned(Instant.parse("2026-11-10T10:00:00Z"));
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.renew(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("zwróconego");
    }

    /**
     * Przedłużenie nieistniejącego wypożyczenia zgłasza "nie znaleziono".
     */
    @Test
    void shouldFailRenewingMissingLoan() {
        when(loanRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.renew(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- get i search ----

    /**
     * Odczyt zwraca wypożyczenie ze stanem wyliczonym na dziś: po terminie to OVERDUE.
     */
    @Test
    void shouldReturnLoanWithStatusComputedForToday() {
        Loan overdue = loan(1L, Instant.parse("2026-10-01T10:00:00Z"), LocalDate.of(2026, 11, 20));
        when(loanRepository.findById(1L)).thenReturn(Optional.of(overdue));

        assertThat(loanService.get(1L).status()).isEqualTo(LoanStatus.OVERDUE);
    }

    /**
     * Odczyt nieistniejącego wypożyczenia zgłasza "nie znaleziono".
     */
    @Test
    void shouldFailGettingMissingLoan() {
        when(loanRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    /**
     * Lista zwraca wypożyczenia z metadanymi strony.
     */
    @Test
    void shouldReturnPageOfLoans() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "borrowedAt"));
        Loan first = loan(1L, Instant.parse("2026-11-01T10:00:00Z"), LocalDate.of(2026, 12, 1));
        when(loanRepository.findAll(ArgumentMatchers.<Specification<Loan>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first), pageable, 1));

        PageResponse<LoanResponse> response =
                loanService.search(READER_ID, 1L, LoanStatus.ACTIVE, pageable);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).extracting(LoanResponse::id).containsExactly(1L);
    }

    /**
     * Sortowanie po niedozwolonym polu jest odrzucane, zanim dojdzie do zapytania do bazy.
     */
    @Test
    void shouldRejectSortingByDisallowedProperty() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("version"));

        assertThatThrownBy(() -> loanService.search(null, null, null, pageable))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("version");
        verifyNoInteractions(loanRepository);
    }
}
