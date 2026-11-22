package pl.tajko.library.api.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Konto pracownika zwracane przez API. Hasło ani jego hash nigdy nie są zwracane.
 *
 * @param id        identyfikator konta
 * @param username  nazwa użytkownika
 * @param role      rola konta
 * @param enabled   czy konto może się logować
 * @param createdAt moment utworzenia konta
 * @param version   wersja konta; trzeba ją odesłać przy aktualizacji
 */
@Schema(description = "Konto pracownika biblioteki (bez hasła)")
public record UserResponse(
        @Schema(description = "Identyfikator konta", example = "1")
        Long id,

        @Schema(description = "Nazwa użytkownika", example = "jan.kowalski")
        String username,

        @Schema(description = "Rola konta", example = "LIBRARIAN")
        Role role,

        @Schema(description = "Czy konto może się logować", example = "true")
        boolean enabled,

        @Schema(description = "Moment utworzenia konta", example = "2026-11-21T03:54:23.012Z")
        Instant createdAt,

        @Schema(description = "Wersja konta; trzeba ją odesłać w żądaniu aktualizacji", example = "0")
        long version) {

    /**
     * Tworzy odpowiedź na podstawie encji.
     *
     * @param user konto
     * @return odpowiedź API
     */
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isEnabled(),
                user.getCreatedAt(), user.getVersion());
    }
}
