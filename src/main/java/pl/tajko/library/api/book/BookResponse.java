package pl.tajko.library.api.book;

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
 * @param version         wersja książki; trzeba ją odesłać przy aktualizacji
 */
public record BookResponse(
        Long id,
        String title,
        String author,
        int publicationYear,
        String isbn,
        Genre genre,
        long copiesCount,
        long version) {

    /**
     * Tworzy odpowiedź na podstawie encji.
     *
     * @param book        książka
     * @param copiesCount liczba egzemplarzy książki
     * @return odpowiedź API
     */
    public static BookResponse from(Book book, long copiesCount) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublicationYear(),
                book.getIsbn(),
                book.getGenre(),
                copiesCount,
                book.getVersion());
    }
}