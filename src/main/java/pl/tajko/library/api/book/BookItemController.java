package pl.tajko.library.api.book;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.tajko.library.api.common.ProblemResponse;

/**
 * Punkty końcowe REST do zarządzania egzemplarzami książki pod adresem
 * {@code /api/v1/books/{bookId}/items}.
 */
@RestController
@RequestMapping("/api/v1/books/{bookId}/items")
@Tag(name = "Egzemplarze", description = "Fizyczne egzemplarze książki: dodawanie, lista i usuwanie")
public class BookItemController {

    private final BookItemService bookItemService;

    /**
     * Tworzy kontroler z serwisem egzemplarzy.
     *
     * @param bookItemService serwis egzemplarzy
     */
    public BookItemController(BookItemService bookItemService) {
        this.bookItemService = bookItemService;
    }

    /**
     * Dodaje egzemplarze do książki. Odpowiada kodem 201, nagłówkiem {@code Location}
     * z adresem listy egzemplarzy i listą utworzonych sztuk.
     *
     * @param bookId  identyfikator książki
     * @param request liczba egzemplarzy do dodania
     * @return utworzone egzemplarze
     */
    @Operation(summary = "Dodaj egzemplarze",
            description = "Dodaje do książki podaną liczbę egzemplarzy (od 1 do 100 w jednym żądaniu). "
                    + "Kolejne wywołania dodają kolejne sztuki do już istniejących.")
    @ApiResponse(responseCode = "201", description = "Egzemplarze zostały dodane")
    @ApiResponse(responseCode = "400",
            description = "Niepoprawna liczba egzemplarzy lub identyfikator książki",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Książka o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @PostMapping
    public ResponseEntity<List<BookItemResponse>> add(
            @Parameter(description = "Identyfikator książki", example = "1") @PathVariable Long bookId,
            @Valid @RequestBody BookItemRequest request) {
        List<BookItemResponse> created = bookItemService.add(bookId, request.count());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Zwraca wszystkie egzemplarze książki.
     *
     * @param bookId identyfikator książki
     * @return egzemplarze książki posortowane po identyfikatorze
     */
    @Operation(summary = "Lista egzemplarzy",
            description = "Zwraca wszystkie egzemplarze książki posortowane rosnąco po identyfikatorze.")
    @ApiResponse(responseCode = "200", description = "Lista egzemplarzy (może być pusta)")
    @ApiResponse(responseCode = "400",
            description = "Identyfikator książki nie jest liczbą",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "404",
            description = "Książka o podanym identyfikatorze nie istnieje",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @GetMapping
    public List<BookItemResponse> list(
            @Parameter(description = "Identyfikator książki", example = "1") @PathVariable Long bookId) {
        return bookItemService.list(bookId);
    }

    /**
     * Usuwa egzemplarz książki.
     *
     * @param bookId identyfikator książki
     * @param itemId identyfikator egzemplarza
     */
    @Operation(summary = "Usuń egzemplarz",
            description = "Usuwa jeden egzemplarz książki. Egzemplarz musi należeć do książki z adresu "
                    + "i nie może mieć historii wypożyczeń (także zakończonych).")
    @ApiResponse(responseCode = "204", description = "Egzemplarz został usunięty")
    @ApiResponse(responseCode = "404",
            description = "Książka nie istnieje albo egzemplarz nie należy do tej książki",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Egzemplarz ma historię wypożyczeń, więc nie można go usunąć",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemResponse.class)))
    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Identyfikator książki", example = "1") @PathVariable Long bookId,
            @Parameter(description = "Identyfikator egzemplarza", example = "7") @PathVariable Long itemId) {
        bookItemService.delete(bookId, itemId);
    }
}