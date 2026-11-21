package pl.tajko.library.api.loan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Testy wiązania i walidacji zasad wypożyczeń z konfiguracji ({@link LoanProperties}), bez bazy danych.
 */
class LoanPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    /** Konfiguracja testowa włączająca wiązanie właściwości. */
    @Configuration
    @EnableConfigurationProperties(LoanProperties.class)
    static class TestConfig {
    }

    /**
     * Bez żadnych ustawień obowiązują wartości domyślne: 30 dni, limit 5, przedłużenie o 14 dni.
     */
    @Test
    void shouldUseDefaultsWhenNothingConfigured() {
        runner.run(context -> {
            LoanProperties properties = context.getBean(LoanProperties.class);
            assertThat(properties.periodDays()).isEqualTo(30);
            assertThat(properties.maxActivePerReader()).isEqualTo(5);
            assertThat(properties.renewalDays()).isEqualTo(14);
        });
    }

    /**
     * Wartości z konfiguracji zastępują domyślne.
     */
    @Test
    void shouldBindConfiguredValues() {
        runner.withPropertyValues(
                        "libris.loans.period-days=21",
                        "libris.loans.max-active-per-reader=3",
                        "libris.loans.renewal-days=7")
                .run(context -> {
                    LoanProperties properties = context.getBean(LoanProperties.class);
                    assertThat(properties.periodDays()).isEqualTo(21);
                    assertThat(properties.maxActivePerReader()).isEqualTo(3);
                    assertThat(properties.renewalDays()).isEqualTo(7);
                });
    }

    /**
     * Wartość mniejsza niż 1 jest błędem konfiguracji i aplikacja się nie uruchamia.
     */
    @Test
    void shouldFailStartupWhenValueIsNotPositive() {
        runner.withPropertyValues("libris.loans.period-days=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("libris.loans.max-active-per-reader=-1")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("libris.loans.renewal-days=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
