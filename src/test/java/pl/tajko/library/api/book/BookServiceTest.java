package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.PageResponse;
import pl.tajko.library.api.common.ResourceNotFoundException;

/**
 * Testy jednostkowe logiki {@link BookService} z zamockowanymi repozytoriami.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final String ISBN_13 = "9780306406157";
    private static final String OTHER_ISBN_13 = "9780132350884";

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookItemRepository bookItemRepository;

    @InjectMocks
    private BookService bookService;

    private static Book bookWithId(long id, String title, String isbn) {
        Book book = new Book(title, "Autor", 2000, isbn, Genre.OTHER);
        ReflectionTestUtils.setField(book, "id", id);
        return book;
    }

    private static BookItemRepository.ItemCount itemCount(long bookId, long count) {
        return new BookItemRepository.ItemCount() {
            @Override
            public Long getBookId() {
                return bookId;
            }

            @Override
            public long getItemCount() {
                return count;
            }
        };
    }

    private static BookUpdateRequest updateRequest(String isbn, long version) {
        return new BookUpdateRequest("Krew elfów", "A. Sapkowski", 1994, isbn, Genre.NOVEL, version);
    }

    // ---- create ----

    /**
     * Nowa książka jest zapisywana z ISBN-13 i przyciętymi tekstami, a odpowiedź nie ma egzemplarzy.
     */
    @Test
    void shouldCreateBookWithIsbn13() {
        when(bookRepository.existsByIsbn(ISBN_13)).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = bookService.create(
                new BookRequest("  Wiedźmin ", "Andrzej Sapkowski", 1993, "0-306-40615-2", Genre.FANTASY));

        assertThat(response.title()).isEqualTo("Wiedźmin");
        assertThat(response.isbn()).isEqualTo(ISBN_13);
        assertThat(response.copiesCount()).isZero();
        assertThat(response.version()).isZero();
        verify(bookRepository).save(any(Book.class));
    }

    /**
     * Książka z istniejącym numerem ISBN nie jest zapisywana.
     */
    @Test
    void shouldRejectCreatingBookWithExistingIsbn() {
        when(bookRepository.existsByIsbn(ISBN_13)).thenReturn(true);

        assertThatThrownBy(() -> bookService.create(
                new BookRequest("Wiedźmin", "Andrzej Sapkowski", 1993, "0306406152", Genre.FANTASY)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(ISBN_13);
        verify(bookRepository, never()).save(any());
    }

    // ---- get ----

    /**
     * Odczyt zwraca książkę z liczbą jej egzemplarzy.
     */
    @Test
    void shouldReturnBookWithItemsCount() {
        when(bookRepository.findById(5L)).thenReturn(Optional.of(bookWithId(5L, "Tytuł", ISBN_13)));
        when(bookItemRepository.countByBookId(5L)).thenReturn(3L);

        BookResponse response = bookService.get(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.copiesCount()).isEqualTo(3L);
    }

    /**
     * Odczyt nieistniejącej książki zgłasza wyjątek "nie znaleziono".
     */
    @Test
    void shouldFailWhenBookIsMissing() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    // ---- update ----

    /**
     * Aktualizacja z aktualną wersją zmienia pola, a ISBN-10 zostaje zapisany jako ISBN-13.
     */
    @Test
    void shouldUpdateBookWhenVersionMatches() {
        Book book = bookWithId(5L, "Wiedźmin", ISBN_13);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(bookRepository.findByIsbn(ISBN_13)).thenReturn(Optional.of(book));
        when(bookRepository.saveAndFlush(book)).thenReturn(book);
        when(bookItemRepository.countByBookId(5L)).thenReturn(2L);

        BookResponse response = bookService.update(5L, updateRequest("0306406152", 0L));

        assertThat(response.title()).isEqualTo("Krew elfów");
        assertThat(response.author()).isEqualTo("A. Sapkowski");
        assertThat(response.publicationYear()).isEqualTo(1994);
        assertThat(response.genre()).isEqualTo(Genre.NOVEL);
        assertThat(response.isbn()).isEqualTo(ISBN_13);
        assertThat(response.copiesCount()).isEqualTo(2L);
    }

    /**
     * Aktualizacja z nieaktualną wersją jest odrzucana i nic nie zapisuje.
     */
    @Test
    void shouldRejectUpdateWithStaleVersion() {
        Book book = bookWithId(5L, "Wiedźmin", ISBN_13);
        ReflectionTestUtils.setField(book, "version", 3L);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> bookService.update(5L, updateRequest(ISBN_13, 2L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("zmieniona");
        verify(bookRepository, never()).saveAndFlush(any());
        assertThat(book.getTitle()).isEqualTo("Wiedźmin");
    }

    /**
     * Aktualizacja nie może przypisać numeru ISBN należącego do innej książki.
     */
    @Test
    void shouldRejectUpdateWithIsbnOfAnotherBook() {
        Book book = bookWithId(5L, "Wiedźmin", ISBN_13);
        Book other = bookWithId(9L, "Czysty kod", OTHER_ISBN_13);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(bookRepository.findByIsbn(OTHER_ISBN_13)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> bookService.update(5L, updateRequest(OTHER_ISBN_13, 0L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(OTHER_ISBN_13);
        verify(bookRepository, never()).saveAndFlush(any());
    }

    /**
     * Aktualizacja nieistniejącej książki zgłasza wyjątek "nie znaleziono".
     */
    @Test
    void shouldFailUpdatingMissingBook() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.update(404L, updateRequest(ISBN_13, 0L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- delete ----

    /**
     * Książka bez egzemplarzy jest usuwana.
     */
    @Test
    void shouldDeleteBookWithoutItems() {
        Book book = bookWithId(5L, "Tytuł", ISBN_13);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(bookItemRepository.countByBookId(5L)).thenReturn(0L);

        bookService.delete(5L);

        verify(bookRepository).delete(book);
    }

    /**
     * Książka z egzemplarzami nie jest usuwana.
     */
    @Test
    void shouldRejectDeletingBookWithItems() {
        Book book = bookWithId(5L, "Tytuł", ISBN_13);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(bookItemRepository.countByBookId(5L)).thenReturn(2L);

        assertThatThrownBy(() -> bookService.delete(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("egzemplarze");
        verify(bookRepository, never()).delete(any(Book.class));
    }

    /**
     * Usuwanie nieistniejącej książki zgłasza wyjątek "nie znaleziono".
     */
    @Test
    void shouldFailDeletingMissingBook() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.delete(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- search ----

    /**
     * Lista łączy dane książek z liczbą egzemplarzy; tytuły bez egzemplarzy mają 0.
     */
    @Test
    void shouldReturnPageWithItemCounts() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("title"));
        Book first = bookWithId(1L, "Pierwsza", ISBN_13);
        Book second = bookWithId(2L, "Druga", OTHER_ISBN_13);
        when(bookRepository.findAll(ArgumentMatchers.<Specification<Book>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(bookItemRepository.countByBookIds(anyCollection())).thenReturn(List.of(itemCount(1L, 3L)));

        PageResponse<BookResponse> response = bookService.search("tytuł", Genre.OTHER, pageable);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).extracting(BookResponse::copiesCount).containsExactly(3L, 0L);
    }

    /**
     * Pusta strona nie wykonuje zapytania o liczbę egzemplarzy.
     */
    @Test
    void shouldNotCountItemsForEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(bookRepository.findAll(ArgumentMatchers.<Specification<Book>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<BookResponse> response = bookService.search(null, null, pageable);

        assertThat(response.content()).isEmpty();
        verifyNoInteractions(bookItemRepository);
    }

    /**
     * Sortowanie po niedozwolonym polu jest odrzucane, zanim dojdzie do zapytania do bazy.
     */
    @Test
    void shouldRejectSortingByDisallowedProperty() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("version"));

        assertThatThrownBy(() -> bookService.search(null, null, pageable))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("version");
        verifyNoInteractions(bookRepository, bookItemRepository);
    }
}