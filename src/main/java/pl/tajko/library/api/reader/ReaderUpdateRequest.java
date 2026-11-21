package pl.tajko.library.api.reader;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dane potrzebne do zaktualizowania czytelnika. Pole {@code version} to wersja, którą klient
 * wcześniej pobrał; jeśli czytelnik zdążył się zmienić, aktualizacja jest odrzucana.
 *
 * @param firstName imię, niepuste, do 100 znaków
 * @param lastName  nazwisko, niepuste, do 100 znaków
 * @param email     adres e-mail, unikalny, zapisywany małymi literami
 * @param phone     numer telefonu (opcjonalny); brak wartości usuwa numer
 * @param active    czy czytelnik może wypożyczać książki
 * @param version   wersja czytelnika, na podstawie której klient wprowadził zmiany
 */
@Schema(description = "Nowe dane czytelnika razem z wersją, którą klient wcześniej pobrał")
public record ReaderUpdateRequest(
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

        @Schema(description = "Numer telefonu (opcjonalny): cyfry, spacje, plus i myślnik, od 7 do 20 znaków; "
                + "brak wartości usuwa numer", example = "+48 123 456 789")
        @Pattern(regexp = Reader.PHONE_REGEX,
                message = "Niepoprawny numer telefonu (cyfry, spacje, plus i myślnik, od 7 do 20 znaków)")
        String phone,

        @Schema(description = "Czy czytelnik może wypożyczać książki (false dezaktywuje konto)", example = "true")
        @NotNull(message = "Status aktywności jest wymagany")
        Boolean active,

        @Schema(description = "Wersja czytelnika z ostatniego odczytu. Jeśli ktoś zmienił czytelnika w międzyczasie, "
                + "serwer odpowie kodem 409", example = "0")
        @NotNull(message = "Wersja jest wymagana")
        Long version) {
}