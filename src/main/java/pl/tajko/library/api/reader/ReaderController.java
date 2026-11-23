package pl.tajko.library.api.reader;

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
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Punkty końcowe REST do zarządzania czytelnikami pod adresem {@code /api/v1/readers}.
 */
@RestController
@RequestMapping("/api/v1/readers")
@Tag(name = "Czytelnicy", description = "Zarządzanie czytelnikami: rejestracja, odczyt, wyszukiwanie, edycja, "
        + "dezaktywacja i usuwanie")
public class ReaderController {

    private final ReaderService readerService;

    /**
     * Tworzy kontroler z serwisem czytelników.
     *
     * @param readerService serwis czytelników
     */
    public ReaderController(ReaderService readerService) {
        this.readerService = readerService;
    }

    /**
     * Rejestruje czytelnika. Odpowiada kodem 201 i nagłówkiem {@code Location} z adresem nowego czytelnika.
     *
     * @param request dane czytelnika
     * @return zarejestrowany czytelnik
     */
    @Operation(summary = "Zarejestruj czytelnika",
            description = "Tworzy nowego, aktywnego czytelnika. Numer karty bibliotecznej (LB-000001) nadaje system, "
                    + "a adres e-mail jest zapisywany małymi literami. Adres nowego czytelnika jest w nagłówku Location.")
    @ApiResponse(responseCode = "201", description = "Czytelnik został zarejestrowany")
    @ApiResponse(responseCode = "400",
            description = "Niepoprawne dane. Lista błędnych pól jest we właściwości errors",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Czytelnik z takim adresem e-mail już istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PostMapping
    public ResponseEntity<ReaderResponse> create(@Valid @RequestBody ReaderRequest request) {
        ReaderResponse created = readerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Zwraca czytelnika o podanym identyfikatorze.
     *
     * @param id identyfikator czytelnika
     * @return czytelnik
     */
    @Operation(summary = "Pobierz czytelnika",
            description = "Zwraca czytelnika o podanym identyfikatorze.")
    @ApiResponse(responseCode = "200", description = "Czytelnik został znaleziony")
    @ApiResponse(responseCode = "400",
            description = "Identyfikator nie jest liczbą",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Czytelnik o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping("/{id}")
    public ReaderResponse get(
            @Parameter(description = "Identyfikator czytelnika", example = "1") @PathVariable Long id) {
        return readerService.get(id);
    }

    /**
     * Zwraca stronę czytelników. Parametry: {@code page}, {@code size} (maks. 100),
     * {@code sort} (np. {@code lastName,desc}), {@code search} i {@code active}.
     *
     * @param search   fragment imienia, nazwiska, e-maila lub numeru karty, opcjonalny
     * @param active   filtr statusu aktywności, opcjonalny
     * @param pageable strona i sortowanie (domyślnie według nazwiska i imienia)
     * @return strona czytelników
     */
    @Operation(summary = "Wyszukaj czytelników",
            description = "Zwraca stronę czytelników. Można wyszukiwać po fragmencie imienia, nazwiska, "
                    + "\"imię nazwisko\", adresu e-mail lub numeru karty (bez rozróżniania wielkości liter) "
                    + "i filtrować po statusie aktywności. Sortować wolno po polach: id, lastName, firstName, "
                    + "email, cardNumber, registeredAt. Maksymalny rozmiar strony to 100, domyślny 20.")
    @ApiResponse(responseCode = "200", description = "Strona czytelników (może być pusta)")
    @ApiResponse(responseCode = "400",
            description = "Niedozwolone pole sortowania lub niepoprawny parametr",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping
    public PageResponse<ReaderResponse> search(
            @Parameter(description = "Fragment imienia, nazwiska, e-maila lub numeru karty, "
                    + "bez rozróżniania wielkości liter", example = "nowak")
            @RequestParam(name = "search", required = false) String search,
            @Parameter(description = "Filtr statusu aktywności: true albo false", example = "true")
            @RequestParam(name = "active", required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = {"lastName", "firstName"}) Pageable pageable) {
        return readerService.search(search, active, pageable);
    }

    /**
     * Aktualizuje czytelnika (w tym aktywuje lub dezaktywuje). Wymaga wersji, którą klient wcześniej pobrał.
     *
     * @param id      identyfikator czytelnika
     * @param request nowe dane wraz z wersją
     * @return zaktualizowany czytelnik
     */
    @Operation(summary = "Zaktualizuj czytelnika",
            description = "Zastępuje dane czytelnika. Pole active = false dezaktywuje konto. W polu version trzeba "
                    + "podać wersję z ostatniego odczytu. Jeśli ktoś zmienił czytelnika w międzyczasie, odpowiedź "
                    + "to 409 i zmiany nie są zapisywane. Numeru karty i daty rejestracji nie można zmienić.")
    @ApiResponse(responseCode = "200", description = "Czytelnik został zaktualizowany, wersja jest zwiększona")
    @ApiResponse(responseCode = "400",
            description = "Niepoprawne dane. Lista błędnych pól jest we właściwości errors",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Czytelnik o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Nieaktualna wersja albo adres e-mail należy do innego czytelnika",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PutMapping("/{id}")
    public ReaderResponse update(
            @Parameter(description = "Identyfikator czytelnika", example = "1") @PathVariable Long id,
            @Valid @RequestBody ReaderUpdateRequest request) {
        return readerService.update(id, request);
    }

    /**
     * Usuwa czytelnika.
     *
     * @param id identyfikator czytelnika
     */
    @Operation(summary = "Usuń czytelnika",
            description = "Usuwa czytelnika, który nigdy nie wypożyczał książek. Czytelnika z historią "
                    + "wypożyczeń można tylko dezaktywować (PUT z active = false).")
    @ApiResponse(responseCode = "204", description = "Czytelnik został usunięty")
    @ApiResponse(responseCode = "404",
            description = "Czytelnik o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Czytelnik ma historię wypożyczeń, więc nie można go usunąć",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Identyfikator czytelnika", example = "1") @PathVariable Long id) {
        readerService.delete(id);
    }
}