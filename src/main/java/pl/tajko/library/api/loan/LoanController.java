package pl.tajko.library.api.loan;

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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.tajko.library.api.common.PageResponse;
import pl.tajko.library.api.common.ProblemResponse;

/**
 * Punkty końcowe REST do obsługi wypożyczeń pod adresem {@code /api/v1/loans}.
 */
@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Wypożyczenia", description = "Wypożyczanie egzemplarzy, zwroty, przedłużanie terminu "
        + "oraz przeglądanie wypożyczeń i ich historii")
public class LoanController {

    private final LoanService loanService;

    /**
     * Tworzy kontroler z serwisem wypożyczeń.
     *
     * @param loanService serwis wypożyczeń
     */
    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    /**
     * Wypożycza egzemplarz czytelnikowi. Odpowiada kodem 201 i nagłówkiem {@code Location}.
     *
     * @param request egzemplarz i czytelnik
     * @return utworzone wypożyczenie
     */
    @Operation(summary = "Wypożycz książkę",
            description = "Wypożycza konkretny egzemplarz czytelnikowi. Termin zwrotu ustawia serwer "
                    + "(domyślnie dziś + 30 dni). Odmowa (409), gdy: egzemplarz jest już wypożyczony, "
                    + "czytelnik jest nieaktywny, ma książkę po terminie albo ma już 5 niezwróconych książek. "
                    + "Adres nowego wypożyczenia jest w nagłówku Location.")
    @ApiResponse(responseCode = "201", description = "Książka została wypożyczona")
    @ApiResponse(responseCode = "400",
            description = "Niepoprawne dane. Lista błędnych pól jest we właściwości errors",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Egzemplarz albo czytelnik nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Egzemplarz jest wypożyczony, czytelnik jest nieaktywny, ma zaległość albo osiągnął limit",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PostMapping
    public ResponseEntity<LoanResponse> borrow(@Valid @RequestBody LoanRequest request) {
        LoanResponse created = loanService.borrow(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Zwraca wypożyczenie o podanym identyfikatorze.
     *
     * @param id identyfikator wypożyczenia
     * @return wypożyczenie
     */
    @Operation(summary = "Pobierz wypożyczenie",
            description = "Zwraca wypożyczenie wraz z podstawowymi danymi czytelnika i książki.")
    @ApiResponse(responseCode = "200", description = "Wypożyczenie zostało znalezione")
    @ApiResponse(responseCode = "400",
            description = "Identyfikator nie jest liczbą",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Wypożyczenie o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping("/{id}")
    public LoanResponse get(
            @Parameter(description = "Identyfikator wypożyczenia", example = "1") @PathVariable Long id) {
        return loanService.get(id);
    }

    /**
     * Zwraca stronę wypożyczeń. Parametry: {@code page}, {@code size} (maks. 100), {@code sort},
     * {@code readerId}, {@code bookId} i {@code status}.
     *
     * @param readerId identyfikator czytelnika, opcjonalny
     * @param bookId   identyfikator książki, opcjonalny
     * @param status   stan wypożyczenia, opcjonalny
     * @param pageable strona i sortowanie (domyślnie od najnowszych)
     * @return strona wypożyczeń
     */
    @Operation(summary = "Wyszukaj wypożyczenia",
            description = "Zwraca stronę wypożyczeń (także zakończonych, czyli historię). Filtry: readerId, bookId "
                    + "(książka, nie egzemplarz) i status: ACTIVE (trwa), OVERDUE (po terminie), RETURNED (zwrócone). "
                    + "Sortować wolno po polach: id, borrowedAt, dueDate, returnedAt, renewals. "
                    + "Domyślnie od najnowszych. Maksymalny rozmiar strony to 100, domyślny 20.")
    @ApiResponse(responseCode = "200", description = "Strona wypożyczeń (może być pusta)")
    @ApiResponse(responseCode = "400",
            description = "Niedozwolone pole sortowania, nieznany status lub niepoprawny parametr",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping
    public PageResponse<LoanResponse> search(
            @Parameter(description = "Tylko wypożyczenia tego czytelnika", example = "1")
            @RequestParam(name = "readerId", required = false) Long readerId,
            @Parameter(description = "Tylko wypożyczenia egzemplarzy tej książki", example = "1")
            @RequestParam(name = "bookId", required = false) Long bookId,
            @Parameter(description = "Tylko wypożyczenia w tym stanie", example = "OVERDUE")
            @RequestParam(name = "status", required = false) LoanStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "borrowedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return loanService.search(readerId, bookId, status, pageable);
    }

    /**
     * Rejestruje zwrot książki.
     *
     * @param id identyfikator wypożyczenia
     * @return zakończone wypożyczenie
     */
    @Operation(summary = "Zwróć książkę",
            description = "Rejestruje zwrot. Egzemplarz od razu można wypożyczyć ponownie, a to wypożyczenie "
                    + "zostaje w historii. Nie ma kar za przetrzymanie.")
    @ApiResponse(responseCode = "200", description = "Zwrot został zarejestrowany")
    @ApiResponse(responseCode = "404",
            description = "Wypożyczenie o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Książka została już zwrócona",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PostMapping("/{id}/return")
    public LoanResponse giveBack(
            @Parameter(description = "Identyfikator wypożyczenia", example = "1") @PathVariable Long id) {
        return loanService.giveBack(id);
    }

    /**
     * Przedłuża termin zwrotu.
     *
     * @param id identyfikator wypożyczenia
     * @return wypożyczenie z nowym terminem
     */
    @Operation(summary = "Przedłuż termin zwrotu",
            description = "Przesuwa termin zwrotu o 14 dni. Można to zrobić tylko raz i tylko do dnia terminu "
                    + "włącznie, nie po terminie.")
    @ApiResponse(responseCode = "200", description = "Termin został przedłużony")
    @ApiResponse(responseCode = "404",
            description = "Wypożyczenie o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Wypożyczenie jest zwrócone, było już przedłużone albo termin zwrotu minął",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PostMapping("/{id}/renew")
    public LoanResponse renew(
            @Parameter(description = "Identyfikator wypożyczenia", example = "1") @PathVariable Long id) {
        return loanService.renew(id);
    }
}
