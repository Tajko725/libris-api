package pl.tajko.library.api.security;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Odpowiedź na poprawne logowanie.
 *
 * @param accessToken token JWT, który wysyłam w nagłówku {@code Authorization: Bearer ...}
 * @param tokenType   typ tokenu, zawsze {@code Bearer}
 * @param expiresIn   liczba sekund, przez które token jest ważny
 */
@Schema(description = "Token dostępu wydany po poprawnym logowaniu")
public record LoginResponse(
        @Schema(description = "Token JWT do wysyłania w nagłówku Authorization: Bearer ...")
        String accessToken,

        @Schema(description = "Typ tokenu", example = "Bearer")
        String tokenType,

        @Schema(description = "Liczba sekund ważności tokenu", example = "3600")
        long expiresIn) {

    /**
     * Buduje odpowiedź z wystawionego tokenu.
     *
     * @param token wystawiony token
     * @return odpowiedź logowania
     */
    static LoginResponse from(TokenService.IssuedToken token) {
        return new LoginResponse(token.value(), "Bearer", token.expiresIn());
    }
}
