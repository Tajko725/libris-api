package pl.tajko.library.api.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dane potrzebne do utworzenia konta pracownika.
 *
 * @param username nazwa użytkownika: 3-50 znaków (małe litery, cyfry, kropka, podkreślenie, myślnik)
 * @param password hasło: od 12 znaków, najwyżej 72 bajty
 * @param role     rola konta
 */
@Schema(description = "Dane nowego konta pracownika")
public record UserRequest(
        @Schema(description = "Nazwa użytkownika: 3-50 znaków (małe litery, cyfry, kropka, podkreślenie, myślnik). "
                + "Wielkie litery są zamieniane na małe", example = "jan.kowalski")
        @NotBlank(message = "Nazwa użytkownika nie może być pusta")
        @Size(max = 100, message = "Nazwa użytkownika jest za długa")
        String username,

        @Schema(description = "Hasło: od 12 znaków, najwyżej 72 bajty (polskie litery liczą się po 2 bajty)",
                example = "Bardzo-dlugie-haslo-1")
        @NotBlank(message = "Hasło nie może być puste")
        @Size(max = 200, message = "Hasło jest za długie")
        String password,

        @Schema(description = "Rola konta", example = "LIBRARIAN")
        @NotNull(message = "Rola jest wymagana")
        Role role) {
}
