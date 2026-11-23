package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.ResourceNotFoundException;
import pl.tajko.library.api.loan.LoanRepository;

/**
 * Testy jednostkowe logiki {@link BookItemService} z zamockowanymi repozytoriami.
 */
@ExtendWith(MockitoExtension.class)
class BookItemServiceTest {

    private static final String ISBN_13 = "9780306406157";

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookItemRepository bookItemRepository;

    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private BookItemService bookItemService;

    private static Book bookWithId(long id) {
        Book book = new Book("Tytuł", "Autor", 2000, ISBN_13, Genre.OTHER);
        ReflectionTestUtils.setField(book, "id", id);
        return book;
    }

    private static BookItem itemWithId(Book book, long id) {
        BookItem item = new BookItem(book);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    // ---- add ----

    /**
     * Serwis zapisuje dokładnie tyle egzemplarzy, ile zażądano, i zwraca je z identyfikatorem książki.
     */
    @Test
    void shouldAddRequestedNumberOfItems() {
        Book book = bookWithId(5L);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(bookItemRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<BookItem> items = invocation.getArgument(0);
            long nextId = 100L;
            for (BookItem item : items) {
                ReflectionTestUtils.setField(item, "id", nextId++);
            }
            return items;
        });

        List<BookItemResponse> response = bookItemService.add(5L, 3);

        assertThat(response).hasSize(3);
        assertThat(response).extracting(BookItemResponse::id).containsExactly(100L, 101L, 102L);
        assertThat(response).extracting(BookItemResponse::bookId).containsOnly(5L);
        assertThat(response).extracting(BookItemResponse::version).containsOnly(0L);
    }

    /**
     * Granice dozwolonego zakresu (1 i 100) są przyjmowane.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, BookItemService.MAX_COPIES_PER_REQUEST})
    void shouldAcceptBoundaryCounts(int count) {
        when(bookRepository.findById(5L)).thenReturn(Optional.of(bookWithId(5L)));
        when(bookItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(bookItemService.add(5L, count)).hasSize(count);
    }

    /**
     * Liczba spoza zakresu jest odrzucana, zanim serwis dotknie bazy.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, 1000})
    void shouldRejectCountOutsideRange(int count) {
        assertThatThrownBy(() -> bookItemService.add(5L, count))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(String.valueOf(count));
        verifyNoInteractions(bookRepository, bookItemRepository);
    }

    /**
     * Dodawanie do nieistniejącej książki zgłasza wyjątek "nie znaleziono" i nic nie zapisuje.
     */
    @Test
    void shouldFailAddingItemsToMissingBook() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookItemService.add(404L, 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
        verify(bookItemRepository, never()).saveAll(anyList());
    }

    // ---- list ----

    /**
     * Lista zwraca egzemplarze książki w kolejności z repozytorium.
     */
    @Test
    void shouldListItemsOfBook() {
        Book book = bookWithId(5L);
        when(bookRepository.existsById(5L)).thenReturn(true);
        when(bookItemRepository.findAllByBookIdOrderByIdAsc(5L))
                .thenReturn(List.of(itemWithId(book, 1L), itemWithId(book, 2L)));

        List<BookItemResponse> response = bookItemService.list(5L);

        assertThat(response).extracting(BookItemResponse::id).containsExactly(1L, 2L);
        assertThat(response).extracting(BookItemResponse::bookId).containsOnly(5L);
    }

    /**
     * Książka bez egzemplarzy daje pustą listę, a nie błąd.
     */
    @Test
    void shouldReturnEmptyListForBookWithoutItems() {
        when(bookRepository.existsById(5L)).thenReturn(true);
        when(bookItemRepository.findAllByBookIdOrderByIdAsc(5L)).thenReturn(List.of());

        assertThat(bookItemService.list(5L)).isEmpty();
    }

    /**
     * Lista egzemplarzy nieistniejącej książki zgłasza wyjątek "nie znaleziono".
     */
    @Test
    void shouldFailListingItemsOfMissingBook() {
        when(bookRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> bookItemService.list(404L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(bookItemRepository);
    }

    // ---- delete ----

    /**
     * Egzemplarz należący do książki jest usuwany.
     */
    @Test
    void shouldDeleteItemOfBook() {
        BookItem item = itemWithId(bookWithId(5L), 7L);
        when(bookRepository.existsById(5L)).thenReturn(true);
        when(bookItemRepository.findByIdAndBookId(7L, 5L)).thenReturn(Optional.of(item));

        bookItemService.delete(5L, 7L);

        verify(bookItemRepository).delete(item);
    }

    /**
     * Egzemplarz z historią wypożyczeń (także zakończonych) nie jest usuwany, a odpowiedź to konflikt.
     */
    @Test
    void shouldNotDeleteItemWithLoanHistory() {
        BookItem item = itemWithId(bookWithId(5L), 7L);
        when(bookRepository.existsById(5L)).thenReturn(true);
        when(bookItemRepository.findByIdAndBookId(7L, 5L)).thenReturn(Optional.of(item));
        when(loanRepository.existsByBookItemId(7L)).thenReturn(true);

        assertThatThrownBy(() -> bookItemService.delete(5L, 7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("historię wypożyczeń");
        verify(bookItemRepository, never()).delete(org.mockito.ArgumentMatchers.any(BookItem.class));
    }

    /**
     * Egzemplarz innej książki (lub nieistniejący) nie jest usuwany.
     */
    @Test
    void shouldNotDeleteItemOfAnotherBook() {
        when(bookRepository.existsById(5L)).thenReturn(true);
        when(bookItemRepository.findByIdAndBookId(7L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookItemService.delete(5L, 7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("7")
                .hasMessageContaining("5");
        verify(bookItemRepository, never()).delete(org.mockito.ArgumentMatchers.any(BookItem.class));
    }

    /**
     * Usuwanie egzemplarza nieistniejącej książki zgłasza wyjątek "nie znaleziono".
     */
    @Test
    void shouldFailDeletingItemOfMissingBook() {
        when(bookRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> bookItemService.delete(404L, 7L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(bookItemRepository, loanRepository);
    }
}