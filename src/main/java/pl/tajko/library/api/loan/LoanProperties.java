package pl.tajko.library.api.loan;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Zasady wypożyczeń ustawiane w {@code application.yml} pod kluczem {@code libris.loans}.
 * Aplikacja nie uruchomi się, jeśli którakolwiek wartość jest mniejsza niż 1.
 *
 * @param periodDays         na ile dni wypożycza się książkę (od dnia wypożyczenia)
 * @param maxActivePerReader ile niezwróconych książek może mieć jednocześnie jeden czytelnik
 * @param renewalDays        o ile dni przedłuża się termin zwrotu przy jednym przedłużeniu
 */
@ConfigurationProperties(prefix = "libris.loans")
@Validated
public record LoanProperties(
        @DefaultValue("30") @Min(1) int periodDays,
        @DefaultValue("5") @Min(1) int maxActivePerReader,
        @DefaultValue("14") @Min(1) int renewalDays) {
}
