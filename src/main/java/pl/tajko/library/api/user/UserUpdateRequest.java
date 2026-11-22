package pl.tajko.library.api.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Dane potrzebne do zmiany roli i statusu konta. Pole {@code version} to wersja, którą klient wcześniej
 * pobrał; jeśli konto zdążyło się zmienić, aktualizacja jest odrzucana.
 *
 * @param role    nowa rola
 * @param enabled czy konto ma być aktywne
 * @param version wersja konta, na podstawie której klient wprowadził zmiany
 */
@Schema(description = "Nowa rola i status konta razem z wersją, którą klient wcześniej pobrał")
public record UserUpdateRequest(
        @Schema(description = "Rola konta", example = "ADMIN")
        @NotNull(message = "Rola jest wymagana")
        Role role,

        @Schema(description = "Czy konto może się logować (false wyłącza konto)", example = "true")
        @NotNull(message = "Status konta jest wymagany")
        Boolean enabled,

        @Schema(description = "Wersja konta z ostatniego odczytu. Jeśli ktoś zmienił konto w międzyczasie, "
                + "serwer odpowie kodem 409", example = "0")
        @NotNull(message = "Wersja jest wymagana")
        Long version) {
}
