package pl.tajko.library.api.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nowe hasło ustawiane przez administratora (bez znajomości starego).
 *
 * @param newPassword nowe hasło: od 12 znaków, najwyżej 72 bajty
 */
@Schema(description = "Nowe hasło konta ustawiane przez administratora")
public record PasswordResetRequest(
        @Schema(description = "Nowe hasło: od 12 znaków, najwyżej 72 bajty", example = "Nowe-dlugie-haslo-1")
        @NotBlank(message = "Nowe hasło nie może być puste")
        @Size(max = 200, message = "Hasło jest za długie")
        String newPassword) {
}
