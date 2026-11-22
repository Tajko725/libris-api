package pl.tajko.library.api.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dane do zmiany własnego hasła.
 *
 * @param currentPassword aktualne hasło
 * @param newPassword     nowe hasło: od 12 znaków, najwyżej 72 bajty, inne niż obecne
 */
@Schema(description = "Zmiana własnego hasła zalogowanego użytkownika")
public record PasswordChangeRequest(
        @Schema(description = "Aktualne hasło", example = "Stare-dlugie-haslo-1")
        @NotBlank(message = "Aktualne hasło nie może być puste")
        @Size(max = 200, message = "Hasło jest za długie")
        String currentPassword,

        @Schema(description = "Nowe hasło: od 12 znaków, najwyżej 72 bajty, inne niż obecne",
                example = "Nowe-dlugie-haslo-1")
        @NotBlank(message = "Nowe hasło nie może być puste")
        @Size(max = 200, message = "Hasło jest za długie")
        String newPassword) {
}
