package pl.tajko.library.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testy jednostkowe walidacji ustawień JWT: aplikacja nie może wystartować ze słabym sekretem.
 */
class JwtPropertiesTest {

    private static final String SECRET_32 = "x".repeat(32);

    /**
     * Sekret o długości 32 bajtów (granica) i poprawne pozostałe ustawienia są przyjmowane.
     */
    @Test
    void shouldAcceptValidSettings() {
        JwtProperties properties = new JwtProperties(SECRET_32, Duration.ofMinutes(60), "libris-api");

        assertThat(properties.secret()).isEqualTo(SECRET_32);
        assertThat(properties.expiration()).isEqualTo(Duration.ofMinutes(60));
        assertThat(properties.issuer()).isEqualTo("libris-api");
    }

    /**
     * Długość sekretu liczona jest w bajtach: 16 polskich liter (2 bajty każda) to dokładnie 32 bajty.
     */
    @Test
    void shouldCountSecretLengthInBytes() {
        assertThat(new JwtProperties("ą".repeat(16), Duration.ofMinutes(1), "libris-api")).isNotNull();
        assertThatThrownBy(() -> new JwtProperties("ą".repeat(15), Duration.ofMinutes(1), "libris-api"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Brak sekretu, pusty albo krótszy niż 32 bajty jest odrzucany, a komunikat nie zawiera sekretu.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "krotki-sekret", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void shouldRejectWeakSecret(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, Duration.ofMinutes(60), "libris-api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bajty")
                .hasMessageContaining("LIBRIS_JWT_SECRET");
    }

    /**
     * Czas ważności musi być dodatni.
     */
    @Test
    void shouldRejectNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtProperties(SECRET_32, Duration.ZERO, "libris-api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dodatni");
        assertThatThrownBy(() -> new JwtProperties(SECRET_32, Duration.ofMinutes(-5), "libris-api"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET_32, null, "libris-api"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Wystawca nie może być pusty.
     */
    @Test
    void shouldRejectBlankIssuer() {
        assertThatThrownBy(() -> new JwtProperties(SECRET_32, Duration.ofMinutes(60), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wystawca");
        assertThatThrownBy(() -> new JwtProperties(SECRET_32, Duration.ofMinutes(60), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Sekret deweloperski jest rozpoznawany po prefiksie, a własny sekret nie.
     */
    @Test
    void shouldRecognizeDevelopmentSecret() {
        String dev = JwtProperties.DEV_SECRET_PREFIX + "reszta-sekretu-0123456789";

        assertThat(new JwtProperties(dev, Duration.ofMinutes(60), "libris-api").usesDevelopmentSecret()).isTrue();
        assertThat(new JwtProperties(SECRET_32, Duration.ofMinutes(60), "libris-api").usesDevelopmentSecret())
                .isFalse();
    }

    /**
     * Opis tekstowy nie zawiera sekretu, więc nie trafi do logów.
     */
    @Test
    void shouldNotExposeSecretInToString() {
        String text = new JwtProperties("tajny-sekret-tajny-sekret-tajny-1234", Duration.ofMinutes(60), "libris-api")
                .toString();

        assertThat(text).doesNotContain("tajny-sekret").contains("issuer=libris-api");
    }
}
