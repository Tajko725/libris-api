package pl.tajko.library.api.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dane logowania.
 *
 * @param username nazwa użytkownika (wielkość liter nie ma znaczenia)
 * @param password hasło
 */
@Schema(description = "Dane logowania pracownika biblioteki")
public record LoginRequest(
        @Schema(description = "Nazwa użytkownika (wielkość liter nie ma znaczenia)", example = "admin")
        @NotBlank(message = "Nazwa użytkownika nie może być pusta")
        @Size(max = 100, message = "Nazwa użytkownika jest za długa")
        String username,

        @Schema(description = "Hasło", example = "Haslo-startowe-123")
        @NotBlank(message = "Hasło nie może być puste")
        @Size(max = 200, message = "Hasło jest za długie")
        String password) {
}
