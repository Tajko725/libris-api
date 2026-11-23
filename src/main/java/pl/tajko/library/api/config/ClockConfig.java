package pl.tajko.library.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguracja zegara. Serwisy pobierają bieżący czas z wstrzykniętego {@link Clock}, a nie przez
 * {@code Instant.now()}, dzięki czemu testy mogą ustawić dowolny moment. Zegar działa w UTC, tak jak
 * ograniczenia w bazie, które liczą dzień wypożyczenia w UTC.
 */
@Configuration
public class ClockConfig {

    /**
     * Zegar systemowy w strefie UTC.
     *
     * @return zegar używany przez aplikację
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
