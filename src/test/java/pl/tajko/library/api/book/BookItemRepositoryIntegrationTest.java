package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.tajko.library.api.AbstractIntegrationTest;

/**
 * Testy integracyjne repozytorium egzemplarzy: powiązanie z tytułem i klucz obcy.
 */
class BookItemRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookItemRepository bookItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Czyści tabele przed każdym testem, żeby testy były od siebie niezależne. */
    @BeforeEach
    void cleanDatabase() {
        bookItemRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
    }

    private Book saveBook(String isbn) {
        return bookRepository.save(new Book("Tytuł", "Autor", 2000, isbn, Genre.OTHER));
    }

    /**
     * Trzy egzemplarze jednego tytułu są liczone i wyszukiwane po identyfikatorze tytułu,
     * a inny tytuł ich nie ma.
     */
    @Test
    void shouldSaveAndCountItemsOfBook() {
        Book book = saveBook("9780132350884");
        Book otherBook = saveBook("9780134685991");

        bookItemRepository.save(new BookItem(book));
        bookItemRepository.save(new BookItem(book));
        bookItemRepository.save(new BookItem(book));

        List<BookItem> items = bookItemRepository.findAllByBookId(book.getId());
        assertThat(items).hasSize(3);
        assertThat(bookItemRepository.countByBookId(book.getId())).isEqualTo(3);
        assertThat(bookItemRepository.countByBookId(otherBook.getId())).isZero();
        assertThat(bookItemRepository.findAllByBookId(otherBook.getId())).isEmpty();
    }

    /**
     * Zapisany egzemplarz dostaje identyfikator i wersję 0.
     */
    @Test
    void shouldAssignIdAndInitialVersion() {
        Book book = saveBook("9780132350884");

        BookItem saved = bookItemRepository.save(new BookItem(book));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }

    /**
     * Baza odrzuca egzemplarz wskazujący na nieistniejący tytuł (klucz obcy).
     */
    @Test
    void shouldRejectItemOfNonexistentBook() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO book_item (book_id) VALUES (?)", 999_999L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Tytułu, który ma egzemplarze, nie da się usunąć; po usunięciu egzemplarzy jest to możliwe.
     */
    @Test
    void shouldNotDeleteBookWithItemsUntilItemsAreRemoved() {
        Book book = saveBook("9780132350884");
        BookItem item = bookItemRepository.save(new BookItem(book));

        assertThatThrownBy(() -> bookRepository.deleteById(book.getId()))
                .isInstanceOf(DataAccessException.class);
        assertThat(bookRepository.findById(book.getId())).isPresent();

        bookItemRepository.deleteById(item.getId());
        bookRepository.deleteById(book.getId());

        assertThat(bookRepository.findById(book.getId())).isEmpty();
    }
}