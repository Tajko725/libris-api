package pl.tajko.library.api.loan;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.book.BookItem;
import pl.tajko.library.api.book.BookItemRepository;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.PageResponse;
import pl.tajko.library.api.common.ResourceNotFoundException;
import pl.tajko.library.api.common.SortValidator;
import pl.tajko.library.api.reader.Reader;
import pl.tajko.library.api.reader.ReaderRepository;

/**
 * Logika biznesowa wypożyczeń: wypożyczanie, zwrot, przedłużanie i wyszukiwanie. Reguły dotyczące jednego
 * wypożyczenia pilnuje encja {@link Loan}, a tutaj sprawdzane są reguły wymagające innych danych
 * (limit wypożyczeń, zaległości, dostępność egzemplarza). Bieżący czas pochodzi z wstrzykniętego {@link Clock}.
 */
@Service
public class LoanService {

    /** Pola encji, po których wolno sortować listę. */
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "borrowedAt", "dueDate", "returnedAt", "renewals");

    private final LoanRepository loanRepository;
    private final BookItemRepository bookItemRepository;
    private final ReaderRepository readerRepository;
    private final LoanProperties properties;
    private final Clock clock;

    /**
     * Tworzy serwis z potrzebnymi zależnościami.
     *
     * @param loanRepository     repozytorium wypożyczeń
     * @param bookItemRepository repozytorium egzemplarzy
     * @param readerRepository   repozytorium czytelników
     * @param properties         zasady wypożyczeń z konfiguracji
     * @param clock              zegar aplikacji
     */
    public LoanService(LoanRepository loanRepository, BookItemRepository bookItemRepository,
                       ReaderRepository readerRepository, LoanProperties properties, Clock clock) {
        this.loanRepository = loanRepository;
        this.bookItemRepository = bookItemRepository;
        this.readerRepository = readerRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Wypożycza egzemplarz czytelnikowi. Termin zwrotu to dzisiejsza data plus okres z konfiguracji.
     *
     * @param request egzemplarz i czytelnik
     * @return utworzone wypożyczenie
     * @throws ResourceNotFoundException jeśli egzemplarz albo czytelnik nie istnieje
     * @throws ConflictException         jeśli czytelnik jest nieaktywny, ma zaległość lub osiągnął limit
     *                                   wypożyczeń albo egzemplarz jest już wypożyczony
     */
    @Transactional
    public LoanResponse borrow(LoanRequest request) {
        Long itemId = request.bookItemId();
        Long readerId = request.readerId();

        BookItem item = bookItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono egzemplarza o id " + itemId));
        // Blokada wiersza czytelnika: równoczesne wypożyczenia tego samego czytelnika wykonują się po kolei,
        // więc limit nie da się obejść dwoma żądaniami naraz.
        Reader reader = readerRepository.findByIdForUpdate(readerId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono czytelnika o id " + readerId));

        Instant now = clock.instant();
        LocalDate today = today();

        if (!reader.isActive()) {
            throw new ConflictException("Czytelnik jest nieaktywny i nie może wypożyczać książek");
        }
        if (loanRepository.existsByBookItemIdAndReturnedAtIsNull(itemId)) {
            throw itemAlreadyBorrowed(itemId);
        }
        if (loanRepository.existsByReaderIdAndReturnedAtIsNullAndDueDateBefore(readerId, today)) {
            throw new ConflictException("Czytelnik ma książkę po terminie zwrotu. Najpierw musi ją oddać");
        }
        if (loanRepository.countByReaderIdAndReturnedAtIsNull(readerId) >= properties.maxActivePerReader()) {
            throw new ConflictException("Czytelnik osiągnął limit wypożyczeń ("
                    + properties.maxActivePerReader() + " książek jednocześnie)");
        }

        Loan loan = new Loan(item, reader, now, today.plusDays(properties.periodDays()));
        try {
            loanRepository.saveAndFlush(loan);
        } catch (DataIntegrityViolationException ex) {
            // Ktoś wypożyczył ten sam egzemplarz w tej samej chwili; złapał to częściowy indeks unikalny w bazie.
            throw itemAlreadyBorrowed(itemId);
        }
        return LoanResponse.from(loan, today);
    }

    /**
     * Zwraca wypożyczenie o podanym identyfikatorze.
     *
     * @param id identyfikator wypożyczenia
     * @return wypożyczenie
     * @throws ResourceNotFoundException jeśli wypożyczenie nie istnieje
     */
    @Transactional(readOnly = true)
    public LoanResponse get(Long id) {
        return LoanResponse.from(findOrThrow(id), today());
    }

    /**
     * Zwraca stronę wypożyczeń z opcjonalnymi filtrami.
     *
     * @param readerId identyfikator czytelnika, może być {@code null}
     * @param bookId   identyfikator książki (tytułu), może być {@code null}
     * @param status   stan wypożyczenia, może być {@code null}
     * @param pageable strona i sortowanie
     * @return strona wypożyczeń
     * @throws InvalidRequestException jeśli sortowanie dotyczy niedozwolonego pola
     */
    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> search(Long readerId, Long bookId, LoanStatus status, Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);
        LocalDate today = today();

        Specification<Loan> specification = Specification.unrestricted();
        if (readerId != null) {
            specification = specification.and(LoanSpecifications.byReader(readerId));
        }
        if (bookId != null) {
            specification = specification.and(LoanSpecifications.byBook(bookId));
        }
        if (status != null) {
            specification = specification.and(LoanSpecifications.hasStatus(status, today));
        }
        return PageResponse.from(loanRepository.findAll(specification, pageable)
                .map(loan -> LoanResponse.from(loan, today)));
    }

    /**
     * Rejestruje zwrot książki. Egzemplarz od razu staje się wolny, a historia wypożyczenia zostaje.
     *
     * @param id identyfikator wypożyczenia
     * @return zakończone wypożyczenie
     * @throws ResourceNotFoundException jeśli wypożyczenie nie istnieje
     * @throws ConflictException         jeśli książka została już zwrócona
     */
    @Transactional
    public LoanResponse giveBack(Long id) {
        Loan loan = findOrThrow(id);
        try {
            loan.markReturned(clock.instant());
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        loanRepository.saveAndFlush(loan);
        return LoanResponse.from(loan, today());
    }

    /**
     * Przedłuża termin zwrotu o liczbę dni z konfiguracji. Dozwolone jednorazowo i tylko przed upływem terminu.
     *
     * @param id identyfikator wypożyczenia
     * @return wypożyczenie z nowym terminem
     * @throws ResourceNotFoundException jeśli wypożyczenie nie istnieje
     * @throws ConflictException         jeśli wypożyczenie jest zwrócone, było już przedłużone
     *                                   albo termin zwrotu minął
     */
    @Transactional
    public LoanResponse renew(Long id) {
        Loan loan = findOrThrow(id);
        LocalDate today = today();
        try {
            loan.renew(today, properties.renewalDays());
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        loanRepository.saveAndFlush(loan);
        return LoanResponse.from(loan, today);
    }

    private Loan findOrThrow(Long id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono wypożyczenia o id " + id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static ConflictException itemAlreadyBorrowed(Long itemId) {
        return new ConflictException("Egzemplarz o id " + itemId + " jest już wypożyczony");
    }
}
