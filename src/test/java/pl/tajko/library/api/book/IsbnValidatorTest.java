package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testy walidacji, normalizacji i konwersji numerów ISBN.
 */
class IsbnValidatorTest {

    /**
     * Poprawne numery ISBN-10 i ISBN-13 (także z myślnikami i małą literą x) są akceptowane.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "9780306406157", "978-0-306-40615-7", "0306406152", "0-306-40615-2",
            "080442957X", "0-8044-2957-x", "9780132350884", "9780134685991"})
    void shouldAcceptValidIsbn(String isbn) {
        assertThat(IsbnValidator.isValid(isbn)).isTrue();
    }

    /**
     * Niepoprawna cyfra kontrolna, zła długość, litery i pusta wartość są odrzucane.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ", "9780306406158", "0306406153", "12345", "978030640615",
            "97803064061577", "97803064061X7", "03064061X2"})
    void shouldRejectInvalidIsbn(String isbn) {
        assertThat(IsbnValidator.isValid(isbn)).isFalse();
    }

    /**
     * Normalizacja usuwa myślniki i spacje oraz zamienia litery na wielkie.
     */
    @Test
    void shouldNormalizeIsbn() {
        assertThat(IsbnValidator.normalize(" 0-8044-2957-x ")).isEqualTo("080442957X");
        assertThat(IsbnValidator.normalize(null)).isNull();
    }

    /**
     * ISBN-10 jest zamieniany na ISBN-13 (także z cyfrą kontrolną X), a ISBN-13 jest tylko normalizowany.
     */
    @ParameterizedTest
    @CsvSource({
            "0306406152,9780306406157",
            "0-306-40615-2,9780306406157",
            "080442957X,9780804429573",
            "9780306406157,9780306406157",
            "978-0-306-40615-7,9780306406157"})
    void shouldConvertToIsbn13(String input, String expected) {
        assertThat(IsbnValidator.toIsbn13(input)).isEqualTo(expected);
    }

    /**
     * Konwersja niepoprawnego numeru kończy się wyjątkiem.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"9780306406158", "0306406153", "abc"})
    void shouldRejectConversionOfInvalidIsbn(String isbn) {
        assertThatThrownBy(() -> IsbnValidator.toIsbn13(isbn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN");
    }
}