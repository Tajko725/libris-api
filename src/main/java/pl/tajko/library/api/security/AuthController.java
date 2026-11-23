package pl.tajko.library.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.tajko.library.api.common.ProblemResponse;
import pl.tajko.library.api.user.AppUser;
import pl.tajko.library.api.user.AppUserService;

/**
 * Logowanie pracowników pod adresem {@code /api/v1/auth/login}. Poprawne dane dają token JWT
 * ważny przez czas ustawiony w {@code libris.security.jwt.expiration}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Uwierzytelnianie", description = "Logowanie pracowników i wydawanie tokenów dostępu")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AppUserService userService;

    /**
     * Tworzy kontroler.
     *
     * @param authenticationManager menedżer uwierzytelniania sprawdzający hasło
     * @param tokenService          serwis wystawiający tokeny
     * @param userService           serwis kont (zmiana własnego hasła)
     */
    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService,
                          AppUserService userService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    /**
     * Loguje pracownika. Każda przyczyna odmowy (zła nazwa, złe hasło, konto wyłączone) daje taką samą
     * odpowiedź 401, żeby nie ujawniać, które konta istnieją.
     *
     * @param request nazwa użytkownika i hasło
     * @return token dostępu
     */
    @Operation(summary = "Zaloguj się",
            description = "Sprawdza nazwę użytkownika i hasło oraz wydaje token JWT. Token wysyłam w nagłówku "
                    + "Authorization: Bearer ... Ten endpoint jest publiczny.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Zalogowano, w odpowiedzi jest token dostępu")
    @ApiResponse(responseCode = "400",
            description = "Brak nazwy użytkownika lub hasła",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "401",
            description = "Niepoprawna nazwa użytkownika lub hasło albo konto jest wyłączone",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String username = AppUser.normalizeUsername(request.username());
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        return LoginResponse.from(tokenService.issue(authentication));
    }

    /**
     * Zmienia hasło zalogowanego użytkownika po sprawdzeniu aktualnego hasła.
     *
     * @param authentication zalogowany użytkownik
     * @param request        aktualne i nowe hasło
     */
    @Operation(summary = "Zmień własne hasło",
            description = "Zmienia hasło zalogowanego użytkownika. Wymaga podania aktualnego hasła. Nowe hasło ma "
                    + "od 12 znaków, najwyżej 72 bajty i musi różnić się od obecnego. Już wydane tokeny nadal "
                    + "działają do wygaśnięcia.")
    @ApiResponse(responseCode = "204", description = "Hasło zostało zmienione")
    @ApiResponse(responseCode = "400",
            description = "Aktualne hasło jest niepoprawne albo nowe nie spełnia zasad",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) {
        userService.changeOwnPassword(authentication.getName(), request.currentPassword(), request.newPassword());
    }
}
