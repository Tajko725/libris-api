package pl.tajko.library.api.loan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Dane potrzebne do wypożyczenia książki. Termin zwrotu wylicza serwer z konfiguracji.
 *
 * @param bookItemId identyfikator wypożyczanego egzemplarza
 * @param readerId   identyfikator czytelnika
 */
@Schema(description = "Dane wypożyczenia: który egzemplarz wypożycza który czytelnik")
public record LoanRequest(
        @Schema(description = "Identyfikator egzemplarza (nie książki), który ma być wypożyczony", example = "1")
        @NotNull(message = "Identyfikator egzemplarza jest wymagany")
        @Positive(message = "Identyfikator egzemplarza musi być dodatni")
        Long bookItemId,

        @Schema(description = "Identyfikator czytelnika", example = "1")
        @NotNull(message = "Identyfikator czytelnika jest wymagany")
        @Positive(message = "Identyfikator czytelnika musi być dodatni")
        Long readerId) {
}
