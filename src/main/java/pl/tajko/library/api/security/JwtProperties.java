package pl.tajko.library.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ustawienia tokenów JWT (klucz {@code libris.security.jwt}). Tokeny podpisuję algorytmem HS256,
 * więc sekret musi mieć co najmniej 256 bitów (32 bajty). Aplikacja nie uruchomi się z za krótkim
 * sekretem ani z niedodatnim czasem ważności.
 *
 * @param secret     sekret do podpisywania tokenów, co najmniej 32 bajty (zmienna {@code LIBRIS_JWT_SECRET})
 * @param expiration czas ważności tokenu dostępu (domyślnie 60 minut)
 * @param issuer     wystawca zapisywany w tokenie i sprawdzany przy odczycie (domyślnie {@code libris-api})
 */
@ConfigurationProperties("libris.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("60m") Duration expiration,
        @DefaultValue("libris-api") String issuer) {

    /** Minimalna długość sekretu w bajtach (256 bitów wymagane przez HS256). */
    public static final int MIN_SECRET_BYTES = 32;

    /** Początek sekretu deweloperskiego z {@code application.yml}; służy do ostrzeżenia przy starcie. */
    static final String DEV_SECRET_PREFIX = "libris-dev-only-";

    /**
     * Sprawdza poprawność ustawień.
     *
     * @throws IllegalArgumentException gdy sekret jest pusty lub krótszy niż 32 bajty, czas ważności
     *                                  nie jest dodatni albo wystawca jest pusty
     */
    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("Sekret JWT (libris.security.jwt.secret, zmienna LIBRIS_JWT_SECRET) "
                    + "musi mieć co najmniej " + MIN_SECRET_BYTES + " bajty (256 bitów)");
        }
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("Czas ważności tokenu (libris.security.jwt.expiration) musi być dodatni");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("Wystawca tokenu (libris.security.jwt.issuer) nie może być pusty");
        }
    }

    /**
     * Mówi, czy używany jest sekret deweloperski z {@code application.yml}, który nie nadaje się na produkcję.
     *
     * @return {@code true}, gdy sekret zaczyna się od prefiksu sekretu deweloperskiego
     */
    public boolean usesDevelopmentSecret() {
        return secret.startsWith(DEV_SECRET_PREFIX);
    }

    @Override
    public String toString() {
        return "JwtProperties[expiration=" + expiration + ", issuer=" + issuer + ", secret=***]";
    }
}
