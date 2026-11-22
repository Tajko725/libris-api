package pl.tajko.library.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Konto pracownika biblioteki, który loguje się do API. Przechowuje wyłącznie hash hasła (bcrypt z prefiksem
 * algorytmu), nigdy samo hasło. Nazwa użytkownika jest zapisywana małymi literami i nie można jej zmienić.
 * Dane są weryfikowane w konstruktorze i setterach jako druga linia obrony (pierwsza to walidacja żądań API,
 * ostatnia to ograniczenia w bazie).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    /** Dozwolony format nazwy użytkownika: małe litery, cyfry oraz kropka, podkreślnik i myślnik, 3-50 znaków. */
    public static final String USERNAME_REGEX = "^[a-z0-9._-]{3,50}$";

    /** Dozwolony format hasha: bcrypt z prefiksem {@code {bcrypt}}, w postaci wytwarzanej przez Spring Security. */
    public static final String PASSWORD_HASH_REGEX = "^\\{bcrypt\\}\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$";

    private static final Pattern USERNAME_PATTERN = Pattern.compile(USERNAME_REGEX);

    private static final Pattern PASSWORD_HASH_PATTERN = Pattern.compile(PASSWORD_HASH_REGEX);

    /** Identyfikator nadawany przez bazę. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nazwa użytkownika zapisana małymi literami, unikalna, niezmienna. */
    @Column(nullable = false, updatable = false, length = 50)
    private String username;

    /** Hash hasła z prefiksem algorytmu. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** Rola użytkownika. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Czy konto może się logować. */
    @Column(nullable = false)
    private boolean enabled;

    /** Moment utworzenia konta. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Wersja do optymistycznego blokowania równoczesnej edycji. */
    @Version
    private long version;

    /** Konstruktor bez argumentów wymagany przez JPA; nie używać w kodzie aplikacji. */
    protected AppUser() {
    }

    /**
     * Tworzy nowe, aktywne konto.
     *
     * @param username     nazwa użytkownika, zostanie zapisana małymi literami (3-50 znaków: a-z, 0-9, . _ -)
     * @param passwordHash hash hasła w formacie {@code {bcrypt}$2a$10$...}, a nie samo hasło
     * @param role         rola użytkownika
     * @throws IllegalArgumentException jeśli któreś z pól jest niepoprawne
     */
    public AppUser(String username, String passwordHash, Role role) {
        String normalized = normalizeUsername(username);
        if (normalized == null || !USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Nazwa użytkownika musi mieć 3-50 znaków: małe litery, cyfry oraz . _ -");
        }
        this.username = normalized;
        changePasswordHash(passwordHash);
        setRole(role);
        this.enabled = true;
        // Baza przechowuje mikrosekundy, więc obcinamy do nich, żeby zapis i odczyt dawały ten sam moment.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Sprowadza nazwę użytkownika do postaci zapisywanej w bazie: bez białych znaków na brzegach i małymi
     * literami. Dzięki temu {@code Anna} i {@code anna} to ta sama nazwa.
     *
     * @param username nazwa podana przez użytkownika, może być {@code null}
     * @return nazwa w postaci kanonicznej albo {@code null}, jeśli podano {@code null}
     */
    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
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
     * Zwraca nazwę użytkownika (małymi literami).
     *
     * @return nazwa użytkownika
     */
    public String getUsername() {
        return username;
    }

    /**
     * Zwraca hash hasła z prefiksem algorytmu.
     *
     * @return hash hasła
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Zastępuje hash hasła (zmiana hasła).
     *
     * @param passwordHash nowy hash w formacie {@code {bcrypt}$2a$10$...}
     * @throws IllegalArgumentException jeśli podana wartość nie wygląda na hash bcrypt (np. jest jawnym hasłem)
     */
    public void changePasswordHash(String passwordHash) {
        if (passwordHash == null || !PASSWORD_HASH_PATTERN.matcher(passwordHash).matches()) {
            throw new IllegalArgumentException("Hasło musi być zapisane jako hash bcrypt, a nie jawnym tekstem");
        }
        this.passwordHash = passwordHash;
    }

    /**
     * Zwraca rolę użytkownika.
     *
     * @return rola
     */
    public Role getRole() {
        return role;
    }

    /**
     * Zmienia rolę użytkownika.
     *
     * @param role nowa rola
     * @throws IllegalArgumentException jeśli rola jest pusta
     */
    public void setRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Rola nie może być pusta");
        }
        this.role = role;
    }

    /**
     * Sprawdza, czy konto może się logować.
     *
     * @return {@code true}, jeśli konto jest aktywne
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Włącza albo blokuje konto.
     *
     * @param enabled {@code false} blokuje logowanie
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Zwraca moment utworzenia konta.
     *
     * @return data i godzina utworzenia
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Zwraca wersję używaną do wykrywania równoczesnej edycji.
     *
     * @return numer wersji
     */
    public long getVersion() {
        return version;
    }

    /**
     * Porównuje konta po identyfikatorze; dwa jeszcze niezapisane obiekty nigdy nie są równe.
     *
     * @param other porównywany obiekt
     * @return {@code true}, jeśli to to samo konto
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppUser that)) {
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
     * Zwraca opis konta bez hasha hasła, żeby nie trafił do logów.
     *
     * @return identyfikator, nazwa, rola i status konta
     */
    @Override
    public String toString() {
        return "AppUser{id=" + id + ", username=" + username + ", role=" + role + ", enabled=" + enabled + "}";
    }
}
