package pl.tajko.library.api.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Dane pierwszego administratora ustawiane w konfiguracji (klucz {@code libris.security.admin}),
 * zwykle zmiennymi środowiskowymi {@code LIBRIS_ADMIN_USERNAME} i {@code LIBRIS_ADMIN_PASSWORD}.
 * Hasło nigdy nie jest zapisywane w repozytorium.
 *
 * @param username nazwa pierwszego administratora
 * @param password hasło pierwszego administratora; puste oznacza, że konto nie zostanie utworzone
 */
@ConfigurationProperties(prefix = "libris.security.admin")
public record AdminBootstrapProperties(
        @DefaultValue("admin") String username,
        String password) {

    /**
     * Sprawdza, czy podano hasło.
     *
     * @return {@code true}, jeśli hasło nie jest puste
     */
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
