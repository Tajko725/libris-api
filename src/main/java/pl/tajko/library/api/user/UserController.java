package pl.tajko.library.api.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.tajko.library.api.common.PageResponse;
import pl.tajko.library.api.common.ProblemResponse;

/**
 * Zarządzanie kontami pracowników pod adresem {@code /api/v1/users}. Wszystkie operacje są dostępne
 * tylko dla roli {@code ADMIN} (reguła w {@code SecurityConfig}). Kont nie usuwam, tylko wyłączam.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Użytkownicy", description = "Zarządzanie kontami pracowników (tylko administrator): tworzenie, "
        + "lista, zmiana roli i statusu, reset hasła")
public class UserController {

    private final AppUserService userService;

    /**
     * Tworzy kontroler z serwisem kont.
     *
     * @param userService serwis kont
     */
    public UserController(AppUserService userService) {
        this.userService = userService;
    }

    /**
     * Tworzy konto. Odpowiada kodem 201 i nagłówkiem {@code Location} z adresem nowego konta.
     *
     * @param request dane konta
     * @return utworzone konto
     */
    @Operation(summary = "Utwórz konto",
            description = "Tworzy aktywne konto pracownika z podaną rolą. Hasło ma od 12 znaków i najwyżej 72 bajty. "
                    + "Adres nowego konta jest w nagłówku Location.")
    @ApiResponse(responseCode = "201", description = "Konto zostało utworzone")
    @ApiResponse(responseCode = "400",
            description = "Niepoprawne dane (nazwa, hasło lub rola)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Konto o tej nazwie już istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        AppUser created = userService.createUser(request.username(), request.password(), request.role());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserResponse.from(created));
    }

    /**
     * Zwraca konto o podanym identyfikatorze.
     *
     * @param id identyfikator konta
     * @return konto
     */
    @Operation(summary = "Pobierz konto", description = "Zwraca konto o podanym identyfikatorze (bez hasła).")
    @ApiResponse(responseCode = "200", description = "Konto zostało znalezione")
    @ApiResponse(responseCode = "404",
            description = "Konto o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping("/{id}")
    public UserResponse get(@Parameter(description = "Identyfikator konta", example = "1") @PathVariable Long id) {
        return UserResponse.from(userService.get(id));
    }

    /**
     * Zwraca stronę kont. Parametry: {@code page}, {@code size} (maks. 100), {@code sort},
     * {@code search}, {@code role} i {@code enabled}.
     *
     * @param search   fragment nazwy użytkownika, opcjonalny
     * @param role     filtr roli, opcjonalny
     * @param enabled  filtr statusu konta, opcjonalny
     * @param pageable strona i sortowanie (domyślnie według nazwy użytkownika)
     * @return strona kont
     */
    @Operation(summary = "Wyszukaj konta",
            description = "Zwraca stronę kont. Można wyszukiwać po fragmencie nazwy użytkownika (bez rozróżniania "
                    + "wielkości liter) oraz filtrować po roli i statusie. Sortować wolno po polach: id, username, "
                    + "role, enabled, createdAt. Maksymalny rozmiar strony to 100, domyślny 20.")
    @ApiResponse(responseCode = "200", description = "Strona kont (może być pusta)")
    @ApiResponse(responseCode = "400",
            description = "Niedozwolone pole sortowania lub niepoprawny parametr",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping
    public PageResponse<UserResponse> search(
            @Parameter(description = "Fragment nazwy użytkownika, bez rozróżniania wielkości liter", example = "jan")
            @RequestParam(name = "search", required = false) String search,
            @Parameter(description = "Filtr roli: LIBRARIAN albo ADMIN", example = "ADMIN")
            @RequestParam(name = "role", required = false) Role role,
            @Parameter(description = "Filtr statusu konta: true albo false", example = "true")
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @ParameterObject @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return PageResponse.from(userService.search(search, role, enabled, pageable).map(UserResponse::from));
    }

    /**
     * Zmienia rolę i status konta. Wymaga wersji, którą klient wcześniej pobrał.
     *
     * @param id      identyfikator konta
     * @param request nowa rola i status wraz z wersją
     * @return zaktualizowane konto
     */
    @Operation(summary = "Zmień rolę lub status konta",
            description = "Ustawia rolę i status konta (enabled = false wyłącza konto, które od razu przestaje "
                    + "działać także z już wydanym tokenem). W polu version trzeba podać wersję z ostatniego odczytu. "
                    + "Nie wolno zdegradować ani wyłączyć ostatniego aktywnego administratora.")
    @ApiResponse(responseCode = "200", description = "Konto zostało zaktualizowane, wersja jest zwiększona")
    @ApiResponse(responseCode = "400",
            description = "Niepoprawne dane. Lista błędnych pól jest we właściwości errors",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Konto o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Nieaktualna wersja albo zmiana usunęłaby ostatniego aktywnego administratora",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PutMapping("/{id}")
    public UserResponse update(
            @Parameter(description = "Identyfikator konta", example = "1") @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        return UserResponse.from(userService.update(id, request.role(), request.enabled(), request.version()));
    }

    /**
     * Ustawia nowe hasło konta (reset przez administratora).
     *
     * @param id      identyfikator konta
     * @param request nowe hasło
     */
    @Operation(summary = "Zresetuj hasło konta",
            description = "Ustawia nowe hasło konta bez znajomości starego. Już wydane tokeny tego konta nadal "
                    + "działają do wygaśnięcia, więc przy podejrzeniu włamania dodatkowo wyłącz konto.")
    @ApiResponse(responseCode = "204", description = "Hasło zostało zmienione")
    @ApiResponse(responseCode = "400",
            description = "Hasło nie spełnia zasad (od 12 znaków, najwyżej 72 bajty)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Konto o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @Parameter(description = "Identyfikator konta", example = "1") @PathVariable Long id,
            @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.newPassword());
    }
}
