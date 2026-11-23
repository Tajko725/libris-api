package pl.tajko.library.api.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testy jednostkowe walidacji i zachowania encji {@link Reader}.
 */
class ReaderTest {

    private static Reader validReader() {
        return new Reader("Anna", "Nowak", "anna.nowak@example.com", "+48 123-456-789");
    }

    // ---- poprawne dane ----

    /**
     * Konstruktor zapisuje dane, ustawia czytelnika jako aktywnego i nadaje czas rejestracji.
     */
    @Test
    void shouldCreateActiveReaderWithRegistrationTime() {
        Instant before = Instant.now();

        Reader reader = validReader();

        assertThat(reader.getFirstName()).isEqualTo("Anna");
        assertThat(reader.getLastName()).isEqualTo("Nowak");
        assertThat(reader.getEmail()).isEqualTo("anna.nowak@example.com");
        assertThat(reader.getPhone()).isEqualTo("+48 123-456-789");
        assertThat(reader.isActive()).isTrue();
        assertThat(reader.getRegisteredAt()).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
    }

    /**
     * Przed zapisem do bazy czytelnik nie ma identyfikatora ani numeru karty.
     */
    @Test
    void shouldHaveNoIdAndCardNumberBeforePersisting() {
        Reader reader = validReader();

        assertThat(reader.getId()).isNull();
        assertThat(reader.getCardNumber()).isNull();
    }

    /**
     * Imię i nazwisko są przycinane, a e-mail zamieniany na małe litery.
     */
    @Test
    void shouldTrimNamesAndNormalizeEmail() {
        Reader reader = new Reader("  Jan ", " Kowalski  ", "  Jan.Kowalski@Example.COM ", null);

        assertThat(reader.getFirstName()).isEqualTo("Jan");
        assertThat(reader.getLastName()).isEqualTo("Kowalski");
        assertThat(reader.getEmail()).isEqualTo("jan.kowalski@example.com");
    }

    /**
     * Pusty lub brakujący telefon oznacza brak numeru.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldTreatBlankPhoneAsMissing(String phone) {
        assertThat(new Reader("Anna", "Nowak", "anna@example.com", phone).getPhone()).isNull();
    }

    /**
     * Akceptowane są granice długości telefonu (7 i 20 znaków) oraz typowe formaty.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1234567", "12345678901234567890", "+48 123 456 789", "123-456-789"})
    void shouldAcceptValidPhones(String phone) {
        assertThat(new Reader("Anna", "Nowak", "anna@example.com", phone).getPhone()).isEqualTo(phone);
    }

    /**
     * Imię i nazwisko o długości dokładnie 100 znaków są akceptowane.
     */
    @Test
    void shouldAcceptNamesOfMaximumLength() {
        String name = "a".repeat(Reader.MAX_NAME_LENGTH);

        Reader reader = new Reader(name, name, "anna@example.com", null);

        assertThat(reader.getFirstName()).hasSize(100);
        assertThat(reader.getLastName()).hasSize(100);
    }

    /**
     * Dezaktywację można cofnąć.
     */
    @Test
    void shouldDeactivateAndReactivateReader() {
        Reader reader = validReader();

        reader.setActive(false);
        assertThat(reader.isActive()).isFalse();

        reader.setActive(true);
        assertThat(reader.isActive()).isTrue();
    }

    // ---- niepoprawne dane ----

    /**
     * Puste lub zbyt długie imię jest odrzucane.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankFirstName(String firstName) {
        assertThatThrownBy(() -> new Reader(firstName, "Nowak", "anna@example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Imię");
    }

    /**
     * Zbyt długie imię jest odrzucane.
     */
    @Test
    void shouldRejectTooLongFirstName() {
        String tooLong = "a".repeat(Reader.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> new Reader(tooLong, "Nowak", "anna@example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    /**
     * Puste lub zbyt długie nazwisko jest odrzucane.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankLastName(String lastName) {
        assertThatThrownBy(() -> new Reader("Anna", lastName, "anna@example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nazwisko");
    }

    /**
     * Niepoprawne adresy e-mail są odrzucane.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "bez-malpy.example.com", "@example.com", "anna@", "an na@example.com",
            "anna@exa mple.com", "anna@@example.com"})
    void shouldRejectInvalidEmail(String email) {
        assertThatThrownBy(() -> new Reader("Anna", "Nowak", email, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Zbyt długi adres e-mail jest odrzucany.
     */
    @Test
    void shouldRejectTooLongEmail() {
        String tooLong = "a".repeat(Reader.MAX_EMAIL_LENGTH) + "@example.com";

        assertThatThrownBy(() -> new Reader("Anna", "Nowak", tooLong, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    /**
     * Niepoprawne numery telefonu są odrzucane.
     */
    @ParameterizedTest
    @ValueSource(strings = {"123456", "123456789012345678901", "12345abc678", "(48) 123 456 789", "tel. 123456789"})
    void shouldRejectInvalidPhone(String phone) {
        assertThatThrownBy(() -> new Reader("Anna", "Nowak", "anna@example.com", phone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("telefon");
    }

    /**
     * Setter z niepoprawną wartością zgłasza wyjątek i nie zmienia dotychczasowej.
     */
    @Test
    void shouldKeepPreviousValueWhenSetterRejectsNewOne() {
        Reader reader = validReader();

        assertThatThrownBy(() -> reader.setEmail("zly-adres")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.setFirstName(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.setPhone("12")).isInstanceOf(IllegalArgumentException.class);

        assertThat(reader.getEmail()).isEqualTo("anna.nowak@example.com");
        assertThat(reader.getFirstName()).isEqualTo("Anna");
        assertThat(reader.getPhone()).isEqualTo("+48 123-456-789");
    }

    // ---- equals, hashCode, toString ----

    /**
     * Dwóch niezapisanych czytelników nie jest równych, a ten sam identyfikator oznacza równość.
     */
    @Test
    void shouldCompareReadersById() {
        Reader first = validReader();
        Reader second = validReader();
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(first);

        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 1L);
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);

        ReflectionTestUtils.setField(second, "id", 2L);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(null).isNotEqualTo("tekst");
    }

    /**
     * Opis tekstowy nie zawiera danych osobowych.
     */
    @Test
    void shouldNotExposePersonalDataInToString() {
        assertThat(validReader().toString())
                .doesNotContain("Anna")
                .doesNotContain("Nowak")
                .doesNotContain("example.com")
                .doesNotContain("123");
    }
}