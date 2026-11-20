package pl.tajko.library.api.book;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.PageResponse;
import pl.tajko.library.api.common.ResourceNotFoundException;

/**
 * Logika biznesowa książek (tytułów): dodawanie, odczyt, wyszukiwanie, edycja i usuwanie.
 * Metody działają w transakcjach, a błędy domenowe są zgłaszane wyjątkami zamienianymi
 * na odpowiedzi HTTP w {@code ApiExceptionHandler}.
 */
@Service
public class BookService {

    /** Pola encji, po których wolno sortować listę. */
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "title", "author", "publicationYear", "isbn", "genre");

    private final BookRepository bookRepository;
    private final BookItemRepository bookItemRepository;

    /**
     * Tworzy serwis z potrzebnymi repozytoriami.
     *
     * @param bookRepository     repozytorium tytułów
     * @param bookItemRepository repozytorium egzemplarzy
     */
    public BookService(BookRepository bookRepository, BookItemRepository bookItemRepository) {
        this.bookRepository = bookRepository;
        this.bookItemRepository = bookItemRepository;
    }

    /**
     * Dodaje nową książkę. Numer ISBN jest zapisywany jako ISBN-13.
     *
     * @param request dane książki
     * @return zapisana książka (bez egzemplarzy)
     * @throws ConflictException jeśli książka o tym numerze ISBN już istnieje
     */
    @Transactional
    public BookResponse create(BookRequest request) {
        String isbn = IsbnValidator.toIsbn13(request.isbn());
        if (bookRepository.existsByIsbn(isbn)) {
            throw new ConflictException("Książka o numerze ISBN " + isbn + " już istnieje");
        }
        Book saved = bookRepository.save(new Book(
                request.title(), request.author(), request.publicationYear(), isbn, request.genre()));
        return BookResponse.from(saved, 0);
    }

    /**
     * Zwraca książkę o podanym identyfikatorze.
     *
     * @param id identyfikator książki
     * @return książka z liczbą egzemplarzy
     * @throws ResourceNotFoundException jeśli książka nie istnieje
     */
    @Transactional(readOnly = true)
    public BookResponse get(Long id) {
        Book book = findOrThrow(id);
        return BookResponse.from(book, bookItemRepository.countByBookId(id));
    }

    /**
     * Zwraca stronę książek z opcjonalnym wyszukiwaniem i filtrem gatunku.
     *
     * @param search   fragment tytułu lub autora (bez rozróżniania wielkości liter), może być {@code null}
     * @param genre    gatunek do filtrowania, może być {@code null}
     * @param pageable strona i sortowanie
     * @return strona książek z liczbą egzemplarzy
     * @throws InvalidRequestException jeśli sortowanie dotyczy niedozwolonego pola
     */
    @Transactional(readOnly = true)
    public PageResponse<BookResponse> search(String search, Genre genre, Pageable pageable) {
        requireAllowedSort(pageable.getSort());

        Specification<Book> specification = Specification.unrestricted();
        if (genre != null) {
            specification = specification.and(BookSpecifications.hasGenre(genre));
        }
        if (search != null && !search.isBlank()) {
            specification = specification.and(BookSpecifications.titleOrAuthorContains(search));
        }

        Page<Book> page = bookRepository.findAll(specification, pageable);
        Map<Long, Long> counts = countItems(page.getContent());
        return PageResponse.from(page.map(book -> BookResponse.from(book, counts.getOrDefault(book.getId(), 0L))));
    }

    /**
     * Aktualizuje książkę. Zmiana jest przyjmowana tylko wtedy, gdy klient zmienia aktualną wersję.
     *
     * @param id      identyfikator książki
     * @param request nowe dane wraz z wersją, którą klient wcześniej pobrał
     * @return zaktualizowana książka
     * @throws ResourceNotFoundException jeśli książka nie istnieje
     * @throws ConflictException         jeśli wersja jest nieaktualna lub ISBN należy do innej książki
     */
    @Transactional
    public BookResponse update(Long id, BookUpdateRequest request) {
        Book book = findOrThrow(id);
        if (book.getVersion() != request.version()) {
            throw new ConflictException("Książka została w międzyczasie zmieniona przez kogoś innego (twoja wersja: "
                    + request.version() + ", aktualna: " + book.getVersion()
                    + "). Pobierz ją ponownie i spróbuj jeszcze raz");
        }

        String isbn = IsbnValidator.toIsbn13(request.isbn());
        boolean takenByOtherBook = bookRepository.findByIsbn(isbn)
                .filter(other -> !other.getId().equals(id))
                .isPresent();
        if (takenByOtherBook) {
            throw new ConflictException("Książka o numerze ISBN " + isbn + " już istnieje");
        }

        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setPublicationYear(request.publicationYear());
        book.setIsbn(isbn);
        book.setGenre(request.genre());

        Book saved = bookRepository.saveAndFlush(book);
        return BookResponse.from(saved, bookItemRepository.countByBookId(id));
    }

    /**
     * Usuwa książkę, która nie ma żadnych egzemplarzy.
     *
     * @param id identyfikator książki
     * @throws ResourceNotFoundException jeśli książka nie istnieje
     * @throws ConflictException         jeśli książka ma egzemplarze
     */
    @Transactional
    public void delete(Long id) {
        Book book = findOrThrow(id);
        if (bookItemRepository.countByBookId(id) > 0) {
            throw new ConflictException("Nie można usunąć książki, która ma egzemplarze. Najpierw usuń egzemplarze");
        }
        bookRepository.delete(book);
    }

    private Book findOrThrow(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono książki o id " + id));
    }

    private Map<Long, Long> countItems(List<Book> books) {
        if (books.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = books.stream().map(Book::getId).toList();
        return bookItemRepository.countByBookIds(ids).stream()
                .collect(Collectors.toMap(
                        BookItemRepository.ItemCount::getBookId,
                        BookItemRepository.ItemCount::getItemCount,
                        Long::sum,
                        java.util.HashMap::new));
    }

    private static void requireAllowedSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Niedozwolone pole sortowania: " + order.getProperty()
                        + ". Dozwolone: " + new TreeSet<>(ALLOWED_SORT_PROPERTIES));
            }
        }
    }
}