package pl.tajko.library.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Testy budowania wzorców wyszukiwania.
 */
class SearchPatternsTest {

    /**
     * Znaki specjalne LIKE i ukośnik są poprzedzane znakiem ucieczki.
     */
    @Test
    void shouldEscapeLikeSpecialCharacters() {
        assertThat(SearchPatterns.escape("100%_\\")).isEqualTo("100\\%\\_\\\\");
    }

    /**
     * Zwykły tekst pozostaje bez zmian.
     */
    @Test
    void shouldLeaveOrdinaryTextUntouched() {
        assertThat(SearchPatterns.escape("wiedźmin")).isEqualTo("wiedźmin");
    }

    /**
     * Wzorzec "zawiera" przycina tekst, zamienia na małe litery i otacza znakami %.
     */
    @Test
    void shouldBuildCaseInsensitiveContainsPattern() {
        assertThat(SearchPatterns.contains("  KoWal ")).isEqualTo("%kowal%");
    }

    /**
     * Znak % wpisany przez użytkownika nie jest traktowany jako symbol wieloznaczny.
     */
    @Test
    void shouldEscapeUserInputInContainsPattern() {
        assertThat(SearchPatterns.contains(" 100% ")).isEqualTo("%100\\%%");
    }
}