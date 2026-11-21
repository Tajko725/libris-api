package pl.tajko.library.api.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Czytelnik biblioteki, czyli osoba, która może wypożyczać książki. Numer karty bibliotecznej
 * nadaje baza danych z sekwencji w chwili zapisu, więc przed pierwszym zapisem jest {@code null}
 * i nigdy nie można go zmienić. Dane są weryfikowane w konstruktorze i setterach jako druga linia
 * obrony (pierwsza to walidacja żądań API, ostatnia to ograniczenia w bazie).
 */
@Entity
@Table(name = "reader")
public class Reader {

    /** Maksymalna długość imienia i nazwiska. */
    public static final int MAX_NAME_LENGTH = 100;

    /** Maksymalna długość adresu e-mail. */
    public static final int MAX_EMAIL_LENGTH = 255;

    /** Dozwolony format adresu e-mail: coś, małpa, coś, bez białych znaków. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    /** Dozwolony format numeru telefonu: cyfry, spacje, plus i myślnik, od 7 do 20 znaków. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9 +-]{7,20}$");

    /** Identyfikator nadawany przez bazę. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Imię czytelnika. */
    @Column(name = "first_name", nullable = false, length = MAX_NAME_LENGTH)
    private String firstName;

    /** Nazwisko czytelnika. */
    @Column(name = "last_name", nullable = false, length = MAX_NAME_LENGTH)
    private String lastName;

    /** Adres e-mail zapisany małymi literami, unikalny. */
    @Column(nullable = false, length = MAX_EMAIL_LENGTH)
    private String email;

    /** Numer telefonu, opcjonalny. */
    @Column(length = 20)
    private String phone;

    /** Numer karty bibliotecznej nadany przez bazę przy zapisie; tylko do odczytu. */
    @Column(name = "card_number", insertable = false, updatable = false, length = 9)
    @Generated(event = EventType.INSERT)
    private String cardNumber;

    /** Moment rejestracji czytelnika. */
    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    /** Czy czytelnik może wypożyczać książki. */
    @Column(nullable = false)
    private boolean active;

    /** Wersja do optymistycznego blokowania równoczesnej edycji. */
    @Version
    private long version;

    /** Konstruktor bez argumentów wymagany przez JPA; nie używać w kodzie aplikacji. */
    protected Reader() {
    }

    /**
     * Tworzy nowego, aktywnego czytelnika zarejestrowanego w tej chwili.
     *
     * @param firstName imię, niepuste, do 100 znaków
     * @param lastName  nazwisko, niepuste, do 100 znaków
     * @param email     adres e-mail, zostanie zapisany małymi literami
     * @param phone     numer telefonu albo {@code null}
     * @throws IllegalArgumentException jeśli któreś z pól jest niepoprawne
     */
    public Reader(String firstName, String lastName, String email, String phone) {
        setFirstName(firstName);
        setLastName(lastName);
        setEmail(email);
        setPhone(phone);
        this.active = true;
        // Baza przechowuje mikrosekundy, więc obcinamy do nich, żeby zapis i odczyt dawały ten sam moment.
        this.registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Zwraca identyfikator nadany przez bazę.
     *
     * @return identyfikator albo {@code null} przed pierwszym zapisem
     */
    public Long getId() {
        return id;
    }

    /**
     * Zwraca imię czytelnika.
     *
     * @return imię
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Ustawia imię po przycięciu białych znaków.
     *
     * @param firstName imię, niepuste, do 100 znaków
     * @throws IllegalArgumentException jeśli imię jest puste lub za długie
     */
    public void setFirstName(String firstName) {
        this.firstName = requireText(firstName, "Imię", MAX_NAME_LENGTH);
    }

    /**
     * Zwraca nazwisko czytelnika.
     *
     * @return nazwisko
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Ustawia nazwisko po przycięciu białych znaków.
     *
     * @param lastName nazwisko, niepuste, do 100 znaków
     * @throws IllegalArgumentException jeśli nazwisko jest puste lub za długie
     */
    public void setLastName(String lastName) {
        this.lastName = requireText(lastName, "Nazwisko", MAX_NAME_LENGTH);
    }

    /**
     * Zwraca adres e-mail zapisany małymi literami.
     *
     * @return adres e-mail
     */
    public String getEmail() {
        return email;
    }

    /**
     * Ustawia adres e-mail po przycięciu białych znaków i zamianie na małe litery.
     *
     * @param email adres e-mail w formacie {@code nazwa@domena}
     * @throws IllegalArgumentException jeśli adres jest pusty, za długi lub ma zły format
     */
    public void setEmail(String email) {
        String normalized = requireText(email, "Adres e-mail", MAX_EMAIL_LENGTH).toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Niepoprawny adres e-mail (oczekiwany format nazwa@domena)");
        }
        this.email = normalized;
    }

    /**
     * Zwraca numer telefonu.
     *
     * @return numer telefonu albo {@code null}, jeśli go nie podano
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Ustawia numer telefonu. Wartość pusta oznacza brak numeru.
     *
     * @param phone numer (cyfry, spacje, plus i myślnik, od 7 do 20 znaków) albo {@code null}
     * @throws IllegalArgumentException jeśli numer ma niepoprawny format
     */
    public void setPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            this.phone = null;
            return;
        }
        String trimmed = phone.trim();
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Niepoprawny numer telefonu (dozwolone cyfry, spacje, plus i myślnik, od 7 do 20 znaków)");
        }
        this.phone = trimmed;
    }

    /**
     * Zwraca numer karty bibliotecznej.
     *
     * @return numer w formacie {@code LB-000001} albo {@code null} przed pierwszym zapisem
     */
    public String getCardNumber() {
        return cardNumber;
    }

    /**
     * Zwraca moment rejestracji czytelnika.
     *
     * @return data i godzina rejestracji
     */
    public Instant getRegisteredAt() {
        return registeredAt;
    }

    /**
     * Sprawdza, czy czytelnik może wypożyczać książki.
     *
     * @return {@code true} dla aktywnego czytelnika
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Aktywuje lub dezaktywuje czytelnika.
     *
     * @param active {@code false} blokuje możliwość wypożyczania
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Zwraca wersję używaną do wykrywania równoczesnej edycji.
     *
     * @return numer wersji
     */
    public long getVersion() {
        return version;
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Pole '" + label + "' nie może być puste");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Pole '" + label + "' nie może przekraczać " + maxLength + " znaków");
        }
        return trimmed;
    }

    /**
     * Porównuje czytelników po identyfikatorze; dwa jeszcze niezapisane obiekty nigdy nie są równe.
     *
     * @param other porównywany obiekt
     * @return {@code true}, jeśli to ten sam czytelnik
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Reader that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    /**
     * Zwraca stałą wartość zależną od klasy, żeby hash nie zmieniał się po nadaniu identyfikatora.
     *
     * @return wartość skrótu
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Zwraca opis czytelnika bez danych osobowych (do logów).
     *
     * @return identyfikator, numer karty i status
     */
    @Override
    public String toString() {
        return "Reader{id=" + id + ", cardNumber=" + cardNumber + ", active=" + active + "}";
    }
}