package pl.tajko.library.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Konfiguracja haszowania haseł. Używam {@code DelegatingPasswordEncoder}: nowe hasła są haszowane bcrypt,
 * a hash zaczyna się od prefiksu algorytmu ({@code {bcrypt}...}). Dzięki temu w przyszłości można przejść
 * na inny algorytm bez resetowania haseł istniejących użytkowników.
 */
@Configuration
public class PasswordConfig {

    /**
     * Koder haseł używany w całej aplikacji.
     *
     * @return koder z domyślnym algorytmem bcrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
