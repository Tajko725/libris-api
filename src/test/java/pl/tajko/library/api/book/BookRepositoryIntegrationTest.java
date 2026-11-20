package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import pl.tajko.library.api.AbstractIntegrationTest;

/**
 * Testy integracyjne repozytorium książek na prawdziwej bazie PostgreSQL:
 * zapis, odczyt, ograniczenia schematu i optymistyczne blokowanie.
 */
class BookRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String ISBN_A = "9780132350884";
    private static final String ISBN_B = "9780134685991";

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

    /**
     * Zapisana książka dostaje id i wersję 0, a po odczycie ma te same dane
     * (ISBN-10 został zapisany jako ISBN-13).
     */
    @Test
    void shouldSaveAndLoadBook() {
        Book saved = bookRepository.save(new Book("Czysty kod", "Robert C. Martin", 2008, "0132350882", Genre.SCIENCE));

        Book loaded = bookRepository.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(loaded.getTitle()).isEqualTo("Czysty kod");
        assertThat(loaded.getAuthor()).isEqualTo("Robert C. Martin");
        assertThat(loaded.getPublicationYear()).isEqualTo(2008);
        assertThat(loaded.getIsbn()).isEqualTo(ISBN_A);
        assertThat(loaded.getGenre()).isEqualTo(Genre.SCIENCE);
        assertThat(loaded.getVersion()).isZero();
    }

    /**
     * Książkę można znaleźć po ISBN-13, a nieistniejący numer nic nie zwraca.
     */
    @Test
    void shouldFindBookByIsbn() {
        bookRepository.save(new Book("Czysty kod", "Robert C. Martin", 2008, ISBN_A, Genre.SCIENCE));

        assertThat(bookRepository.findByIsbn(ISBN_A)).isPresent();
        assertThat(bookRepository.existsByIsbn(ISBN_A)).isTrue();
        assertThat(bookRepository.findByIsbn(ISBN_B)).isEmpty();
        assertThat(bookRepository.existsByIsbn(ISBN_B)).isFalse();
    }

    /**
     * Ta sama książka podana raz jako ISBN-10, a raz jako ISBN-13 jest wykryta jako duplikat,
     * bo oba zapisy sprowadzamy do ISBN-13.
     */
    @Test
    void shouldRejectDuplicateIsbnGivenInDifferentFormats() {
        bookRepository.save(new Book("Książka A", "Autor A", 1990, "0306406152", Genre.OTHER));
        Book duplicate = new Book("Książka B", "Autor B", 2000, "978-0-306-40615-7", Genre.OTHER);

        assertThatThrownBy(() -> bookRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Każda wartość enuma {@link Genre} jest akceptowana przez ograniczenie CHECK w bazie,
     * więc lista w migracji jest zgodna z kodem.
     */
    @ParameterizedTest
    @EnumSource(Genre.class)
    void shouldStoreEveryGenre(Genre genre) {
        Book saved = bookRepository.save(new Book("Tytuł", "Autor", 2000, ISBN_A, genre));

        Book loaded = bookRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getGenre()).isEqualTo(genre);
    }

    /**
     * Baza sama odrzuca wiersze łamiące ograniczenia, nawet gdy ominiemy walidację w Javie.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("rowsViolatingConstraints")
    void shouldRejectRowViolatingConstraintInDatabase(
            String caseName, String title, String author, int year, String isbn, String genre) {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO book (title, author, publication_year, isbn, genre) VALUES (?, ?, ?, ?, ?)",
                title, author, year, isbn, genre))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<Arguments> rowsViolatingConstraints() {
        return Stream.of(
                arguments("pusty tytuł", "   ", "Autor", 2000, ISBN_A, "OTHER"),
                arguments("pusty autor", "Tytuł", "", 2000, ISBN_A, "OTHER"),
                arguments("rok przed 1450", "Tytuł", "Autor", 1449, ISBN_A, "OTHER"),
                arguments("ISBN o złej długości", "Tytuł", "Autor", 2000, "12345", "OTHER"),
                arguments("ISBN z literą", "Tytuł", "Autor", 2000, "978030640615X", "OTHER"),
                arguments("nieznany gatunek", "Tytuł", "Autor", 2000, ISBN_A, "POEM"));
    }

    /**
     * Zmiana zapisanej książki zwiększa jej wersję o 1.
     */
    @Test
    void shouldIncrementVersionOnUpdate() {
        Book saved = bookRepository.save(new Book("Czysty kod", "Robert C. Martin", 2008, ISBN_A, Genre.SCIENCE));
        Book loaded = bookRepository.findById(saved.getId()).orElseThrow();

        loaded.setTitle("Czysty kod. Wydanie poprawione");
        bookRepository.save(loaded);

        Book reloaded = bookRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Czysty kod. Wydanie poprawione");
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    /**
     * Gdy dwóch użytkowników pobierze tę samą książkę, drugi zapis ze starą wersją jest odrzucony
     * i nie nadpisuje zmian pierwszego (optymistyczne blokowanie).
     */
    @Test
    void shouldRejectStaleUpdate() {
        Book saved = bookRepository.save(new Book("Czysty kod", "Robert C. Martin", 2008, ISBN_A, Genre.SCIENCE));
        Book first = bookRepository.findById(saved.getId()).orElseThrow();
        Book second = bookRepository.findById(saved.getId()).orElseThrow();

        first.setTitle("Zmiana pierwszego użytkownika");
        bookRepository.save(first);

        second.setTitle("Zmiana drugiego użytkownika");
        assertThatThrownBy(() -> bookRepository.save(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        Book stored = bookRepository.findById(saved.getId()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Zmiana pierwszego użytkownika");
        assertThat(stored.getVersion()).isEqualTo(1);
    }
}