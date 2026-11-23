package pl.tajko.library.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.tajko.library.api.user.AppUser;

/**
 * Testy jednostkowe konfiguracji haszowania haseł: algorytm, sól i weryfikacja.
 */
class PasswordConfigTest {

    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    /**
     * Hash ma prefiks {@code {bcrypt}} i format akceptowany przez encję użytkownika.
     */
    @Test
    void shouldHashWithBcryptInFormatAcceptedByEntity() {
        String hash = encoder.encode("Tajne-haslo-123");

        assertThat(hash).startsWith("{bcrypt}").matches(AppUser.PASSWORD_HASH_REGEX);
        assertThat(hash).doesNotContain("Tajne-haslo-123");
    }

    /**
     * Poprawne hasło pasuje do hasha, błędne (także różniące się wielkością liter) nie.
     */
    @Test
    void shouldVerifyOnlyCorrectPassword() {
        String hash = encoder.encode("Tajne-haslo-123");

        assertThat(encoder.matches("Tajne-haslo-123", hash)).isTrue();
        assertThat(encoder.matches("tajne-haslo-123", hash)).isFalse();
        assertThat(encoder.matches("Tajne-haslo-124", hash)).isFalse();
        assertThat(encoder.matches("", hash)).isFalse();
    }

    /**
     * To samo hasło daje za każdym razem inny hash (losowa sól), a oba nadal pasują do hasła.
     */
    @Test
    void shouldUseRandomSalt() {
        String first = encoder.encode("Tajne-haslo-123");
        String second = encoder.encode("Tajne-haslo-123");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("Tajne-haslo-123", first)).isTrue();
        assertThat(encoder.matches("Tajne-haslo-123", second)).isTrue();
    }
}
