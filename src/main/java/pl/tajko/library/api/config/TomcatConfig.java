package pl.tajko.library.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// Jeśli IntelliJ nie zna importu, użyj Alt+Enter na TomcatConnectorCustomizer.
// Spodziewana klasa: org.springframework.boot.tomcat.TomcatConnectorCustomizer
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;

/**
 * Konfiguracja wbudowanego serwera Tomcat.
 */
@Configuration
public class TomcatConfig {

    /**
     * Maksymalna liczba języków przyjmowana z nagłówka {@code Accept-Language}.
     * Domyślny limit Tomcata 11.0.27 wynosi 10, a niektóre przeglądarki (np. Opera)
     * wysyłają więcej języków, co kończyło się błędem 500.
     */
    static final int MAX_LOCALE_COUNT = 50;

    /**
     * Podnosi limit języków w nagłówku {@code Accept-Language} na konektorze Tomcata.
     *
     * @return konfigurator konektora stosowany automatycznie przez Spring Boot
     */
    @Bean
    TomcatConnectorCustomizer maxLocaleCountCustomizer() {
        return connector -> connector.setMaxLocaleCount(MAX_LOCALE_COUNT);
    }
}