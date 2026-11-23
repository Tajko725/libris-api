package pl.tajko.library.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testy jednostkowe reguł encji {@link AppUser}: nazwa użytkownika, hash hasła, rola i status konta.
 */
class AppUserTest {

    /** Prawdziwy hash w formacie, jaki wytwarza aplikacja: {@code {bcrypt}$2a$10$...}. */
    static final String VALID_HASH =
            PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("Tajne-haslo-123");

    private static AppUser user() {
        return new AppUser("jan.kowalski", VALID_HASH, Role.LIBRARIAN);
    }

    // ---- tworzenie ----

    /**
     * Nowe konto jest aktywne, ma podaną rolę, datę utworzenia i nie ma identyfikatora.
     */
    @Test
    void shouldCreateEnabledUser() {
        AppUser user = user();

        assertThat(user.getId()).isNull();
        assertThat(user.getUsername()).isEqualTo("jan.kowalski");
        assertThat(user.getPasswordHash()).isEqualTo(VALID_HASH);
        assertThat(user.getRole()).isEqualTo(Role.LIBRARIAN);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    /**
     * Nazwa użytkownika jest przycinana i zapisywana małymi literami.
     */
    @Test
    void shouldNormalizeUsername() {
        AppUser user = new AppUser("  Anna_Nowak-1  ", VALID_HASH, Role.ADMIN);

        assertThat(user.getUsername()).isEqualTo("anna_nowak-1");
    }

    /**
     * Normalizacja zachowuje się tak samo dla wartości pustej: zwraca {@code null}.
     */
    @Test
    void shouldReturnNullWhenNormalizingNull() {
        assertThat(AppUser.normalizeUsername(null)).isNull();
    }

    /**
     * Nazwy za krótkie, za długie, z niedozwolonymi znakami lub puste są odrzucane.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "ab", "jan kowalski", "jan@kowalski", "zażółć"})
    void shouldRejectInvalidUsername(String username) {
        assertThatThrownBy(() -> new AppUser(username, VALID_HASH, Role.LIBRARIAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nazwa użytkownika");
    }

    /**
     * Nazwa dłuższa niż 50 znaków jest odrzucana.
     */
    @Test
    void shouldRejectTooLongUsername() {
        String tooLong = "a".repeat(51);

        assertThatThrownBy(() -> new AppUser(tooLong, VALID_HASH, Role.LIBRARIAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nazwa użytkownika");
    }

    /**
     * Granice długości nazwy (3 i 50 znaków) są dozwolone.
     */
    @Test
    void shouldAcceptUsernameLengthBoundaries() {
        assertThat(new AppUser("abc", VALID_HASH, Role.LIBRARIAN).getUsername()).hasSize(3);
        assertThat(new AppUser("a".repeat(50), VALID_HASH, Role.LIBRARIAN).getUsername()).hasSize(50);
    }

    /**
     * Hasło podane jawnym tekstem albo hash w innym formacie niż bcrypt są odrzucane.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "Tajne-haslo-123", "{noop}Tajne-haslo-123", "{bcrypt}nie-hash",
            "$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234"})
    void shouldRejectPasswordThatIsNotBcryptHash(String hash) {
        assertThatThrownBy(() -> new AppUser("jan.kowalski", hash, Role.LIBRARIAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash bcrypt");
    }

    /**
     * Pusta rola jest odrzucana.
     */
    @Test
    void shouldRejectMissingRole() {
        assertThatThrownBy(() -> new AppUser("jan.kowalski", VALID_HASH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rola");
    }

    // ---- zmiany ----

    /**
     * Rolę, status konta i hash hasła można zmienić.
     */
    @Test
    void shouldChangeRoleEnabledAndPasswordHash() {
        AppUser user = user();
        String newHash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("Inne-haslo-456");

        user.setRole(Role.ADMIN);
        user.setEnabled(false);
        user.changePasswordHash(newHash);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo(newHash);
    }

    /**
     * Niepoprawna zmiana hasha lub roli jest odrzucana i zostawia poprzednie wartości.
     */
    @Test
    void shouldKeepOldValuesWhenChangeIsRejected() {
        AppUser user = user();

        assertThatThrownBy(() -> user.changePasswordHash("jawne-haslo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.setRole(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getPasswordHash()).isEqualTo(VALID_HASH);
        assertThat(user.getRole()).isEqualTo(Role.LIBRARIAN);
    }

    // ---- equals, hashCode, toString ----

    /**
     * Dwa niezapisane konta nie są równe, a ten sam identyfikator oznacza równość.
     */
    @Test
    void shouldCompareUsersById() {
        AppUser first = user();
        AppUser second = user();
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
     * Opis tekstowy nie zawiera hasha hasła, więc nie trafi do logów.
     */
    @Test
    void shouldNotExposePasswordHashInToString() {
        String text = user().toString();

        assertThat(text).contains("jan.kowalski").contains("LIBRARIAN");
        assertThat(text).doesNotContain("bcrypt").doesNotContain(VALID_HASH);
    }
}
