package pl.tajko.library.api.book;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id) {
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
    @GetMapping
    public PageResponse<BookResponse> search(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "genre", required = false) Genre genre,
            @PageableDefault(sort = "title") Pageable pageable) {
        return bookService.search(search, genre, pageable);
    }

    /**
     * Aktualizuje książkę. Wymaga wersji, którą klient wcześniej pobrał.
     *
     * @param id      identyfikator książki
     * @param request nowe dane wraz z wersją
     * @return zaktualizowana książka
     */
    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookUpdateRequest request) {
        return bookService.update(id, request);
    }

    /**
     * Usuwa książkę, która nie ma egzemplarzy.
     *
     * @param id identyfikator książki
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        bookService.delete(id);
    }
}