package pl.tajko.library.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tajko.library.api.common.ProblemResponse;
import pl.tajko.library.api.user.AppUser;

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

    /**
     * Tworzy kontroler.
     *
     * @param authenticationManager menedżer uwierzytelniania sprawdzający hasło
     * @param tokenService          serwis wystawiający tokeny
     */
    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
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
}
