package pl.tajko.library.api.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/**
 * Testy jednostkowe {@link SortValidator}: dozwolone i niedozwolone pola sortowania.
 */
class SortValidatorTest {

    private static final Set<String> ALLOWED = Set.of("id", "title");

    /**
     * Sortowanie po dozwolonych polach, w tym po kilku naraz, jest przyjmowane.
     */
    @Test
    void shouldAcceptAllowedProperties() {
        assertThatCode(() -> SortValidator.requireAllowed(Sort.by("title").and(Sort.by("id").descending()), ALLOWED))
                .doesNotThrowAnyException();
    }

    /**
     * Brak sortowania jest przyjmowany.
     */
    @Test
    void shouldAcceptUnsorted() {
        assertThatCode(() -> SortValidator.requireAllowed(Sort.unsorted(), ALLOWED)).doesNotThrowAnyException();
    }

    /**
     * Niedozwolone pole jest odrzucane, a komunikat podaje to pole i listę dozwolonych.
     */
    @Test
    void shouldRejectDisallowedProperty() {
        assertThatThrownBy(() -> SortValidator.requireAllowed(Sort.by("version"), ALLOWED))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("version")
                .hasMessageContaining("[id, title]");
    }

    /**
     * Wystarczy jedno niedozwolone pole spośród kilku, żeby sortowanie zostało odrzucone.
     */
    @Test
    void shouldRejectWhenOnlyOneOfManyPropertiesIsDisallowed() {
        assertThatThrownBy(() -> SortValidator.requireAllowed(Sort.by("title", "password"), ALLOWED))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("password");
    }
}
