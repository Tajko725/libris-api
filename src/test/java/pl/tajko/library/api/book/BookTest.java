package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Year;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testy reguł biznesowych encji {@link Book} (bez bazy danych).
 */
class BookTest {

    private static final String ISBN_13 = "9780306406157";

    private Book validBook() {
        return new Book("Wiedźmin", "Andrzej Sapkowski", 1993, ISBN_13, Genre.FANTASY);
    }

    /**
     * Konstruktor przycina teksty i zapisuje ISBN bez myślników; nowa książka nie ma jeszcze id.
     */
    @Test
    void shouldCreateBookWithTrimmedTextAndNormalizedIsbn() {
        Book book = new Book("  Wiedźmin ", " Andrzej Sapkowski  ", 1993, "978-0-306-40615-7", Genre.FANTASY);

        assertThat(book.getTitle()).isEqualTo("Wiedźmin");
        assertThat(book.getAuthor()).isEqualTo("Andrzej Sapkowski");
        assertThat(book.getPublicationYear()).isEqualTo(1993);
        assertThat(book.getIsbn()).isEqualTo(ISBN_13);
        assertThat(book.getGenre()).isEqualTo(Genre.FANTASY);
        assertThat(book.getId()).isNull();
        assertThat(book.getVersion()).isZero();
    }

    /**
     * ISBN-10 jest przechowywany jako ISBN-13.
     */
    @Test
    void shouldStoreIsbn10AsIsbn13() {
        Book book = new Book("Tytuł", "Autor", 1990, "0-306-40615-2", Genre.OTHER);

        assertThat(book.getIsbn()).isEqualTo(ISBN_13);
    }

    /**
     * Rok 1450 i bieżący rok są dopuszczalne (wartości graniczne).
     */
    @Test
    void shouldAcceptBoundaryYears() {
        int currentYear = Year.now().getValue();

        assertThat(new Book("Tytuł", "Autor", 1450, ISBN_13, Genre.OTHER).getPublicationYear()).isEqualTo(1450);
        assertThat(new Book("Tytuł", "Autor", currentYear, ISBN_13, Genre.OTHER).getPublicationYear())
                .isEqualTo(currentYear);
    }

    /**
     * Pusty tytuł (null, pusty, same spacje) jest odrzucany.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankTitle(String title) {
        assertThatThrownBy(() -> new Book(title, "Autor", 1990, ISBN_13, Genre.OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tytuł");
    }

    /**
     * Pusty autor (null, pusty, same spacje) jest odrzucany.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankAuthor(String author) {
        assertThatThrownBy(() -> new Book("Tytuł", author, 1990, ISBN_13, Genre.OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Autor");
    }

    /**
     * Tytuł dłuższy niż 255 znaków jest odrzucany, a dokładnie 255 znaków jest akceptowane.
     */
    @Test
    void shouldRejectTooLongTitleButAcceptMaximumLength() {
        String maxTitle = "a".repeat(Book.MAX_TEXT_LENGTH);

        assertThat(new Book(maxTitle, "Autor", 1990, ISBN_13, Genre.OTHER).getTitle()).isEqualTo(maxTitle);
        assertThatThrownBy(() -> new Book(maxTitle + "a", "Autor", 1990, ISBN_13, Genre.OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    /**
     * Rok przed 1450 i rok z przyszłości są odrzucane.
     */
    @Test
    void shouldRejectYearOutsideAllowedRange() {
        int nextYear = Year.now().getValue() + 1;

        assertThatThrownBy(() -> new Book("Tytuł", "Autor", 1449, ISBN_13, Genre.OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rok wydania");
        assertThatThrownBy(() -> new Book("Tytuł", "Autor", nextYear, ISBN_13, Genre.OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rok wydania");
    }

    /**
     * Niepoprawny lub pusty ISBN jest odrzucany.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"9780306406158", "12345"})
    void shouldRejectInvalidIsbn(String isbn) {
        assertThatThrownBy(() -> new Book("Tytuł", "Autor", 1990, isbn, Genre.OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN");
    }

    /**
     * Brak gatunku jest odrzucany.
     */
    @Test
    void shouldRejectNullGenre() {
        assertThatThrownBy(() -> new Book("Tytuł", "Autor", 1990, ISBN_13, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gatunek");
    }

    /**
     * Settery zmieniają pola i stosują te same reguły co konstruktor.
     */
    @Test
    void shouldUpdateFieldsThroughSetters() {
        Book book = validBook();

        book.setTitle("  Krew elfów ");
        book.setAuthor("A. Sapkowski");
        book.setPublicationYear(1994);
        book.setIsbn("0306406152");
        book.setGenre(Genre.NOVEL);

        assertThat(book.getTitle()).isEqualTo("Krew elfów");
        assertThat(book.getAuthor()).isEqualTo("A. Sapkowski");
        assertThat(book.getPublicationYear()).isEqualTo(1994);
        assertThat(book.getIsbn()).isEqualTo(ISBN_13);
        assertThat(book.getGenre()).isEqualTo(Genre.NOVEL);
    }

    /**
     * Setter, który odrzuca wartość, nie zmienia dotychczasowego stanu książki.
     */
    @Test
    void shouldKeepPreviousValueWhenSetterRejectsInput() {
        Book book = validBook();

        assertThatThrownBy(() -> book.setTitle("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> book.setIsbn("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> book.setPublicationYear(1000)).isInstanceOf(IllegalArgumentException.class);

        assertThat(book.getTitle()).isEqualTo("Wiedźmin");
        assertThat(book.getIsbn()).isEqualTo(ISBN_13);
        assertThat(book.getPublicationYear()).isEqualTo(1993);
    }

    /**
     * Dwie niezapisane książki nie są równe, a książka jest równa sama sobie.
     */
    @Test
    void shouldNotTreatDifferentUnsavedBooksAsEqual() {
        Book first = validBook();
        Book second = validBook();

        assertThat(first).isEqualTo(first);
        assertThat(first).isNotEqualTo(second);
    }
}