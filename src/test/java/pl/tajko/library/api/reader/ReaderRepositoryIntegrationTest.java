package pl.tajko.library.api.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import pl.tajko.library.api.AbstractIntegrationTest;

/**
 * Testy integracyjne repozytorium czytelników z prawdziwą bazą PostgreSQL: mapowanie,
 * numer karty z sekwencji, ograniczenia bazy i optymistyczne blokowanie.
 */
class ReaderRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern CARD_NUMBER = Pattern.compile("LB-\\d{6}");

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Czyści tabelę, żeby testy były od siebie niezależne. */
    @BeforeEach
    void cleanUp() {
        readerRepository.deleteAllInBatch();
    }

    private static Reader reader(String email) {
        return new Reader("Anna", "Nowak", email, "+48 123-456-789");
    }

    private static int cardSequenceNumber(Reader reader) {
        return Integer.parseInt(reader.getCardNumber().substring("LB-".length()));
    }

    // ---- zapis i odczyt ----

    /**
     * Zapisany czytelnik wraca z bazy ze wszystkimi polami, identyfikatorem i wersją 0.
     */
    @Test
    void shouldSaveAndLoadAllFields() {
        Reader saved = readerRepository.save(reader("anna.nowak@example.com"));

        Reader loaded = readerRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getFirstName()).isEqualTo("Anna");
        assertThat(loaded.getLastName()).isEqualTo("Nowak");
        assertThat(loaded.getEmail()).isEqualTo("anna.nowak@example.com");
        assertThat(loaded.getPhone()).isEqualTo("+48 123-456-789");
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getRegisteredAt()).isEqualTo(saved.getRegisteredAt());
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getCardNumber()).matches(CARD_NUMBER);
    }

    /**
     * Czytelnik bez telefonu jest zapisywany z pustą wartością.
     */
    @Test
    void shouldSaveReaderWithoutPhone() {
        Reader saved = readerRepository.save(new Reader("Jan", "Kowalski", "jan@example.com", null));

        assertThat(readerRepository.findById(saved.getId()).orElseThrow().getPhone()).isNull();
    }

    // ---- numer karty ----

    /**
     * Numer karty jest nadawany już przy zapisie, ma format LB-NNNNNN i rośnie dla kolejnych czytelników.
     */
    @Test
    void shouldAssignIncreasingCardNumbers() {
        Reader first = readerRepository.save(reader("pierwszy@example.com"));
        Reader second = readerRepository.save(reader("drugi@example.com"));

        assertThat(first.getCardNumber()).matches(CARD_NUMBER);
        assertThat(second.getCardNumber()).matches(CARD_NUMBER).isNotEqualTo(first.getCardNumber());
        assertThat(cardSequenceNumber(second)).isGreaterThan(cardSequenceNumber(first));
    }

    /**
     * Edycja czytelnika nie zmienia jego numeru karty.
     */
    @Test
    void shouldKeepCardNumberWhenUpdatingReader() {
        Reader saved = readerRepository.save(reader("anna@example.com"));
        String cardNumber = saved.getCardNumber();

        Reader loaded = readerRepository.findById(saved.getId()).orElseThrow();
        loaded.setLastName("Kowalska");
        readerRepository.saveAndFlush(loaded);

        Reader reloaded = readerRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLastName()).isEqualTo("Kowalska");
        assertThat(reloaded.getCardNumber()).isEqualTo(cardNumber);
    }

    /**
     * Wiersz dodany bezpośrednio w SQL też dostaje numer karty, status aktywny, datę rejestracji i wersję 0
     * (domyślne wartości kolumn).
     */
    @Test
    void shouldApplyDatabaseDefaultsForRowsInsertedWithSql() {
        jdbcTemplate.update("INSERT INTO reader (first_name, last_name, email) VALUES ('Jan', 'Kowalski', 'jan@example.com')");

        Reader loaded = readerRepository.findByEmail("jan@example.com").orElseThrow();

        assertThat(loaded.getCardNumber()).matches(CARD_NUMBER);
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getRegisteredAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
    }

    // ---- wyszukiwanie ----

    /**
     * Czytelnika można znaleźć po e-mailu i po numerze karty, a brakujące wartości dają pusty wynik.
     */
    @Test
    void shouldFindReaderByEmailAndCardNumber() {
        Reader saved = readerRepository.save(reader("anna@example.com"));

        assertThat(readerRepository.findByEmail("anna@example.com")).isPresent();
        assertThat(readerRepository.findByCardNumber(saved.getCardNumber()))
                .get().extracting(Reader::getId).isEqualTo(saved.getId());
        assertThat(readerRepository.existsByEmail("anna@example.com")).isTrue();

        assertThat(readerRepository.findByEmail("nie.ma@example.com")).isEmpty();
        assertThat(readerRepository.findByCardNumber("LB-999999")).isEmpty();
        assertThat(readerRepository.existsByEmail("nie.ma@example.com")).isFalse();
    }

    // ---- unikalność ----

    /**
     * Drugi czytelnik z tym samym e-mailem jest odrzucany przez bazę, nawet jeśli e-mail
     * podano wielkimi literami (encja zamienia go na małe).
     */
    @Test
    void shouldRejectDuplicateEmail() {
        readerRepository.save(reader("anna@example.com"));

        assertThatThrownBy(() -> readerRepository.saveAndFlush(reader("ANNA@Example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(readerRepository.count()).isEqualTo(1);
    }

    /**
     * Baza nie pozwala na dwóch czytelników z tym samym numerem karty.
     */
    @Test
    void shouldRejectDuplicateCardNumber() {
        Reader saved = readerRepository.save(reader("anna@example.com"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO reader (first_name, last_name, email, card_number) VALUES (?, ?, ?, ?)",
                "Jan", "Kowalski", "jan@example.com", saved.getCardNumber()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(readerRepository.count()).isEqualTo(1);
    }

    // ---- ograniczenia bazy ----

    static Stream<Arguments> invalidRows() {
        return Stream.of(
                Arguments.of("puste imię", "", "Nowak", "a@example.com", null),
                Arguments.of("nazwisko z samych spacji", "Anna", "   ", "a@example.com", null),
                Arguments.of("imię dłuższe niż 100 znaków", "a".repeat(101), "Nowak", "a@example.com", null),
                Arguments.of("e-mail wielkimi literami", "Anna", "Nowak", "Anna@Example.com", null),
                Arguments.of("e-mail bez małpy", "Anna", "Nowak", "anna.example.com", null),
                Arguments.of("e-mail ze spacją w środku", "Anna", "Nowak", "an na@example.com", null),
                Arguments.of("e-mail ze spacją na końcu", "Anna", "Nowak", "anna@example.com ", null),
                Arguments.of("za krótki telefon", "Anna", "Nowak", "a@example.com", "12345"),
                Arguments.of("telefon z literami", "Anna", "Nowak", "a@example.com", "12345abc678"));
    }

    /**
     * Baza odrzuca wiersze łamiące ograniczenia, nawet gdy ktoś omija encję i wstawia dane przez SQL.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRows")
    void shouldRejectRowsViolatingDatabaseConstraints(
            String caseName, String firstName, String lastName, String email, String phone) {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO reader (first_name, last_name, email, phone) VALUES (?, ?, ?, ?)",
                firstName, lastName, email, phone))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(readerRepository.count()).isZero();
    }

    // ---- status i wersja ----

    /**
     * Dezaktywacja czytelnika jest zapisywana w bazie.
     */
    @Test
    void shouldPersistDeactivation() {
        Reader saved = readerRepository.save(reader("anna@example.com"));

        Reader loaded = readerRepository.findById(saved.getId()).orElseThrow();
        loaded.setActive(false);
        readerRepository.saveAndFlush(loaded);

        assertThat(readerRepository.findById(saved.getId()).orElseThrow().isActive()).isFalse();
    }

    /**
     * Każda zmiana zwiększa wersję o 1.
     */
    @Test
    void shouldIncrementVersionOnUpdate() {
        Reader saved = readerRepository.save(reader("anna@example.com"));

        Reader loaded = readerRepository.findById(saved.getId()).orElseThrow();
        loaded.setFirstName("Ania");
        readerRepository.saveAndFlush(loaded);

        assertThat(readerRepository.findById(saved.getId()).orElseThrow().getVersion()).isEqualTo(1);
    }

    /**
     * Zapis na podstawie nieaktualnej wersji (równoczesna edycja) jest odrzucany.
     */
    @Test
    void shouldRejectStaleUpdate() {
        Reader saved = readerRepository.save(reader("anna@example.com"));
        Reader firstCopy = readerRepository.findById(saved.getId()).orElseThrow();
        Reader secondCopy = readerRepository.findById(saved.getId()).orElseThrow();

        firstCopy.setFirstName("Ania");
        readerRepository.saveAndFlush(firstCopy);

        secondCopy.setFirstName("Hania");
        assertThatThrownBy(() -> readerRepository.saveAndFlush(secondCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(readerRepository.findById(saved.getId()).orElseThrow().getFirstName()).isEqualTo("Ania");
    }
}