package pl.tajko.library.api.book;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.ResourceNotFoundException;
import pl.tajko.library.api.loan.LoanRepository;

/**
 * Logika biznesowa egzemplarzy książek: dodawanie wielu sztuk naraz, lista i usuwanie.
 * Egzemplarz zawsze należy do jednej książki, a każda operacja najpierw sprawdza,
 * czy ta książka istnieje.
 */
@Service
public class BookItemService {

    /** Największa liczba egzemplarzy, którą można dodać jednym żądaniem. */
    public static final int MAX_COPIES_PER_REQUEST = 100;

    private final BookRepository bookRepository;
    private final BookItemRepository bookItemRepository;
    private final LoanRepository loanRepository;

    /**
     * Tworzy serwis z potrzebnymi repozytoriami.
     *
     * @param bookRepository     repozytorium książek
     * @param bookItemRepository repozytorium egzemplarzy
     * @param loanRepository     repozytorium wypożyczeń (do ochrony historii przed usunięciem)
     */
    public BookItemService(BookRepository bookRepository, BookItemRepository bookItemRepository,
                           LoanRepository loanRepository) {
        this.bookRepository = bookRepository;
        this.bookItemRepository = bookItemRepository;
        this.loanRepository = loanRepository;
    }

    /**
     * Dodaje podaną liczbę egzemplarzy do książki. Kolejne wywołania dodają kolejne sztuki
     * (2 razy po 3 daje razem 6 egzemplarzy).
     *
     * @param bookId identyfikator książki
     * @param count  liczba egzemplarzy, od 1 do {@value #MAX_COPIES_PER_REQUEST}
     * @return utworzone egzemplarze
     * @throws InvalidRequestException   jeśli liczba jest spoza dozwolonego zakresu
     * @throws ResourceNotFoundException jeśli książka nie istnieje
     */
    @Transactional
    public List<BookItemResponse> add(Long bookId, int count) {
        if (count < 1 || count > MAX_COPIES_PER_REQUEST) {
            throw new InvalidRequestException("Liczba egzemplarzy musi być z zakresu od 1 do "
                    + MAX_COPIES_PER_REQUEST + ", a podano " + count);
        }
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> bookNotFound(bookId));

        List<BookItem> items = IntStream.range(0, count)
                .mapToObj(index -> new BookItem(book))
                .toList();
        return bookItemRepository.saveAll(items).stream()
                .map(item -> BookItemResponse.from(item, bookId))
                .toList();
    }

    /**
     * Zwraca wszystkie egzemplarze książki posortowane rosnąco po identyfikatorze.
     *
     * @param bookId identyfikator książki
     * @return egzemplarze książki (pusta lista, jeśli książka nie ma żadnego)
     * @throws ResourceNotFoundException jeśli książka nie istnieje
     */
    @Transactional(readOnly = true)
    public List<BookItemResponse> list(Long bookId) {
        requireBookExists(bookId);
        return bookItemRepository.findAllByBookIdOrderByIdAsc(bookId).stream()
                .map(item -> BookItemResponse.from(item, bookId))
                .toList();
    }

    /**
     * Usuwa egzemplarz książki, który nigdy nie był wypożyczony. Egzemplarz z historią wypożyczeń
     * (także zakończonych) zostaje w bazie, bo historia musi pozostać kompletna.
     *
     * @param bookId identyfikator książki
     * @param itemId identyfikator egzemplarza
     * @throws ResourceNotFoundException jeśli książka nie istnieje albo egzemplarz nie należy do tej książki
     * @throws ConflictException         jeśli egzemplarz ma historię wypożyczeń
     */
    @Transactional
    public void delete(Long bookId, Long itemId) {
        requireBookExists(bookId);
        BookItem item = bookItemRepository.findByIdAndBookId(itemId, bookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nie znaleziono egzemplarza o id " + itemId + " w książce o id " + bookId));
        if (loanRepository.existsByBookItemId(itemId)) {
            throw new ConflictException("Nie można usunąć egzemplarza, który ma historię wypożyczeń "
                    + "(także zakończonych), bo historia musi pozostać kompletna");
        }
        bookItemRepository.delete(item);
    }

    private void requireBookExists(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw bookNotFound(bookId);
        }
    }

    private static ResourceNotFoundException bookNotFound(Long bookId) {
        return new ResourceNotFoundException("Nie znaleziono książki o id " + bookId);
    }
}