package pl.tajko.library.api.book;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Egzemplarz książki zwracany przez API.
 *
 * @param id      identyfikator egzemplarza
 * @param bookId  identyfikator książki, do której należy egzemplarz
 * @param version wersja egzemplarza
 */
@Schema(description = "Fizyczny egzemplarz książki")
public record BookItemResponse(
        @Schema(description = "Identyfikator egzemplarza", example = "7")
        Long id,

        @Schema(description = "Identyfikator książki, do której należy egzemplarz", example = "1")
        Long bookId,

        @Schema(description = "Wersja egzemplarza", example = "0")
        long version) {

    /**
     * Tworzy odpowiedź na podstawie encji.
     *
     * @param item   egzemplarz
     * @param bookId identyfikator książki, do której należy egzemplarz
     * @return odpowiedź API
     */
    public static BookItemResponse from(BookItem item, Long bookId) {
        return new BookItemResponse(item.getId(), bookId, item.getVersion());
    }
}