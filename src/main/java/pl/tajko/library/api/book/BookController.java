package pl.tajko.library.api.book;

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
import pl.tajko.library.api.common.ProblemResponse;
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

/**
 * Punkty końcowe REST do zarządzania książkami (tytułami) pod adresem {@code /api/v1/books}.
 */
@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Książki", description = "Zarządzanie książkami (tytułami): dodawanie, odczyt, wyszukiwanie, edycja i usuwanie")
public class BookController {

    private final BookService bookService;

    /**
     * Tworzy kontroler z serwisem książek.
     *
     * @param bookService serwis książek
     */
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * Dodaje książkę. Odpowiada kodem 201 i nagłówkiem {@code Location} z adresem nowej książki.
     *
     * @param request dane książki
     * @return utworzona książka
     */
    @Operation(summary = "Dodaj książkę",
            description = "Tworzy nową książkę bez egzemplarzy. Numer ISBN-10 jest zapisywany jako ISBN-13. "
                    + "Adres nowej książki jest w nagłówku Location.")
    @ApiResponse(responseCode = "201", description = "Książka została dodana")
    @ApiResponse(responseCode = "400",
            description = "Niepoprawne dane. Lista błędnych pól jest we właściwości errors",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Książka o tym numerze ISBN już istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Zwraca książkę o podanym identyfikatorze.
     *
     * @param id identyfikator książki
     * @return książka
     */
    @Operation(summary = "Pobierz książkę",
            description = "Zwraca książkę o podanym identyfikatorze wraz z liczbą jej egzemplarzy.")
    @ApiResponse(responseCode = "200", description = "Książka została znaleziona")
    @ApiResponse(responseCode = "400",
            description = "Identyfikator nie jest liczbą",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Książka o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping("/{id}")
    public BookResponse get(
            @Parameter(description = "Identyfikator książki", example = "1") @PathVariable Long id) {
        return bookService.get(id);
    }

    /**
     * Zwraca stronę książek. Parametry: {@code page}, {@code size} (maks. 100),
     * {@code sort} (np. {@code title,desc}), {@code search} (tytuł lub autor) i {@code genre}.
     *
     * @param search   fragment tytułu lub autora, opcjonalny
     * @param genre    gatunek, opcjonalny
     * @param pageable strona i sortowanie (domyślnie według tytułu)
     * @return strona książek
     */
    @Operation(summary = "Wyszukaj książki",
            description = "Zwraca stronę książek. Można wyszukiwać po fragmencie tytułu lub autora "
                    + "(bez rozróżniania wielkości liter) i filtrować po gatunku. "
                    + "Sortować wolno po polach: id, title, author, publicationYear, isbn, genre. "
                    + "Maksymalny rozmiar strony to 100, domyślny 20.")
    @ApiResponse(responseCode = "200", description = "Strona książek (może być pusta)")
    @ApiResponse(responseCode = "400",
            description = "Niedozwolone pole sortowania, nieznany gatunek lub niepoprawny parametr",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping
    public PageResponse<BookResponse> search(
            @Parameter(description = "Fragment tytułu lub autora, bez rozróżniania wielkości liter", example = "kod")
            @RequestParam(name = "search", required = false) String search,
            @Parameter(description = "Gatunek do filtrowania", example = "SCIENCE")
            @RequestParam(name = "genre", required = false) Genre genre,
            @ParameterObject @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return bookService.search(search, genre, pageable);
    }

    /**
     * Aktualizuje książkę. Wymaga wersji, którą klient wcześniej pobrał.
     *
     * @param id      identyfikator książki
     * @param request nowe dane wraz z wersją
     * @return zaktualizowana książka
     */
    @Operation(summary = "Zaktualizuj książkę",
            description = "Zastępuje dane książki. W polu version trzeba podać wersję z ostatniego odczytu. "
                    + "Jeśli ktoś zmienił książkę w międzyczasie, odpowiedź to 409 i zmiany nie są zapisywane.")
    @ApiResponse(responseCode = "200", description = "Książka została zaktualizowana, wersja jest zwiększona")
    @ApiResponse(responseCode = "400",
            description = "Niepoprawne dane. Lista błędnych pól jest we właściwości errors",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Książka o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Nieaktualna wersja albo numer ISBN należy do innej książki",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PutMapping("/{id}")
    public BookResponse update(
            @Parameter(description = "Identyfikator książki", example = "1") @PathVariable Long id,
            @Valid @RequestBody BookUpdateRequest request) {
        return bookService.update(id, request);
    }

    /**
     * Usuwa książkę, która nie ma egzemplarzy.
     *
     * @param id identyfikator książki
     */
    @Operation(summary = "Usuń książkę",
            description = "Usuwa książkę, która nie ma żadnych egzemplarzy.")
    @ApiResponse(responseCode = "204", description = "Książka została usunięta")
    @ApiResponse(responseCode = "404",
            description = "Książka o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Książka ma egzemplarze, więc nie można jej usunąć",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Identyfikator książki", example = "1") @PathVariable Long id) {
        bookService.delete(id);
    }
}