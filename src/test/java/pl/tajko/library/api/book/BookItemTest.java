package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Testy encji {@link BookItem} (bez bazy danych).
 */
class BookItemTest {

    /**
     * Nowy egzemplarz zapamiętuje książkę i nie ma jeszcze identyfikatora.
     */
    @Test
    void shouldCreateItemForBook() {
        Book book = new Book("Tytuł", "Autor", 1990, "9780306406157", Genre.OTHER);

        BookItem item = new BookItem(book);

        assertThat(item.getBook()).isSameAs(book);
        assertThat(item.getId()).isNull();
        assertThat(item.getVersion()).isZero();
    }

    /**
     * Egzemplarz bez książki jest odrzucany.
     */
    @Test
    void shouldRejectNullBook() {
        assertThatThrownBy(() -> new BookItem(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Książka");
    }
}