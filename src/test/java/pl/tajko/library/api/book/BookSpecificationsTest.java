package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Testy zabezpieczania znaków specjalnych w wyszukiwaniu.
 */
class BookSpecificationsTest {

    /**
     * Znaki specjalne LIKE i ukośnik są poprzedzane znakiem ucieczki.
     */
    @Test
    void shouldEscapeLikeSpecialCharacters() {
        assertThat(BookSpecifications.escapeLike("100%_\\")).isEqualTo("100\\%\\_\\\\");
    }

    /**
     * Zwykły tekst pozostaje bez zmian.
     */
    @Test
    void shouldLeaveOrdinaryTextUntouched() {
        assertThat(BookSpecifications.escapeLike("wiedźmin")).isEqualTo("wiedźmin");
    }
}