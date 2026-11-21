package pl.tajko.library.api.book;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Książka zwracana przez API.
 *
 * @param id              identyfikator książki
 * @param title           tytuł
 * @param author          autor
 * @param publicationYear rok wydania
 * @param isbn            numer ISBN-13 bez myślników
 * @param genre           gatunek
 * @param copiesCount     liczba fizycznych egzemplarzy tej książki
 * @param availableCopies liczba egzemplarzy, które nie są teraz wypożyczone
 * @param version         wersja książki; trzeba ją odesłać przy aktualizacji
 */
@Schema(description = "Książka (tytuł) wraz z liczbą egzemplarzy")
public record BookResponse(
        @Schema(description = "Identyfikator książki", example = "1")
        Long id,

        @Schema(description = "Tytuł książki", example = "Czysty kod")
        String title,

        @Schema(description = "Autor książki", example = "Robert C. Martin")
        String author,

        @Schema(description = "Rok wydania", example = "2008")
        int publicationYear,

        @Schema(description = "Numer ISBN-13 bez myślników", example = "9780132350884")
        String isbn,

        @Schema(description = "Gatunek książki", example = "SCIENCE")
        Genre genre,

        @Schema(description = "Liczba fizycznych egzemplarzy tej książki", example = "2")
        long copiesCount,

        @Schema(description = "Liczba egzemplarzy, które nie są teraz wypożyczone", example = "1")
        long availableCopies,

        @Schema(description = "Wersja książki; trzeba ją odesłać w żądaniu aktualizacji", example = "0")
        long version) {

    /**
     * Tworzy odpowiedź na podstawie encji.
     *
     * @param book        książka
     * @param copiesCount     liczba egzemplarzy książki
     * @param availableCopies liczba egzemplarzy niewypożyczonych w tej chwili
     * @return odpowiedź API
     */
    public static BookResponse from(Book book, long copiesCount, long availableCopies) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublicationYear(),
                book.getIsbn(),
                book.getGenre(),
                copiesCount,
                availableCopies,
                book.getVersion());
    }
}