package pl.tajko.library.api.reader;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dane potrzebne do zarejestrowania nowego czytelnika. Nowy czytelnik jest zawsze aktywny,
 * a numer karty nadaje baza.
 *
 * @param firstName imię, niepuste, do 100 znaków
 * @param lastName  nazwisko, niepuste, do 100 znaków
 * @param email     adres e-mail, unikalny, zapisywany małymi literami
 * @param phone     numer telefonu (opcjonalny)
 */
@Schema(description = "Dane nowego czytelnika")
public record ReaderRequest(
        @Schema(description = "Imię czytelnika", example = "Anna")
        @NotBlank(message = "Imię nie może być puste")
        @Size(max = Reader.MAX_NAME_LENGTH, message = "Imię nie może przekraczać 100 znaków")
        String firstName,

        @Schema(description = "Nazwisko czytelnika", example = "Nowak")
        @NotBlank(message = "Nazwisko nie może być puste")
        @Size(max = Reader.MAX_NAME_LENGTH, message = "Nazwisko nie może przekraczać 100 znaków")
        String lastName,

        @Schema(description = "Adres e-mail (unikalny, zapisywany małymi literami)",
                example = "anna.nowak@example.com")
        @NotBlank(message = "Adres e-mail nie może być pusty")
        @Size(max = Reader.MAX_EMAIL_LENGTH, message = "Adres e-mail nie może przekraczać 255 znaków")
        @Pattern(regexp = Reader.EMAIL_REGEX, message = "Niepoprawny adres e-mail (oczekiwany format nazwa@domena)")
        String email,

        @Schema(description = "Numer telefonu (opcjonalny): cyfry, spacje, plus i myślnik, od 7 do 20 znaków",
                example = "+48 123 456 789")
        @Pattern(regexp = Reader.PHONE_REGEX,
                message = "Niepoprawny numer telefonu (cyfry, spacje, plus i myślnik, od 7 do 20 znaków)")
        String phone) {
}