package pl.tajko.library.api.loan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import pl.tajko.library.api.book.Book;
import pl.tajko.library.api.book.BookItem;
import pl.tajko.library.api.reader.Reader;

/**
 * Wypożyczenie zwracane przez API, z podstawowymi danymi czytelnika i książki, żeby klient nie musiał
 * dopytywać o nie osobno.
 *
 * @param id         identyfikator wypożyczenia
 * @param status     stan wypożyczenia
 * @param reader     czytelnik, który wypożyczył książkę
 * @param book       wypożyczona książka i egzemplarz
 * @param borrowedAt moment wypożyczenia
 * @param dueDate    termin zwrotu
 * @param returnedAt moment zwrotu albo {@code null}
 * @param renewals   liczba przedłużeń terminu
 * @param version    wersja wypożyczenia
 */
@Schema(description = "Wypożyczenie egzemplarza książki przez czytelnika")
public record LoanResponse(
        @Schema(description = "Identyfikator wypożyczenia", example = "1")
        Long id,

        @Schema(description = "Stan wypożyczenia wyliczony na dziś")
        LoanStatus status,

        @Schema(description = "Czytelnik, który wypożyczył książkę")
        ReaderSummary reader,

        @Schema(description = "Wypożyczona książka i konkretny egzemplarz")
        BookSummary book,

        @Schema(description = "Moment wypożyczenia", example = "2026-11-21T10:15:30Z")
        Instant borrowedAt,

        @Schema(description = "Termin zwrotu: ostatni dzień, w którym zwrot nie jest opóźniony", example = "2026-12-21")
        LocalDate dueDate,

        @Schema(description = "Moment zwrotu albo null, jeśli książka jeszcze nie wróciła", example = "2026-11-25T12:00:00Z")
        Instant returnedAt,

        @Schema(description = "Ile razy przedłużono termin zwrotu (najwyżej raz)", example = "0")
        int renewals,

        @Schema(description = "Wersja wypożyczenia", example = "0")
        long version) {

    /**
     * Podstawowe dane czytelnika w wypożyczeniu.
     *
     * @param id         identyfikator czytelnika
     * @param firstName  imię
     * @param lastName   nazwisko
     * @param cardNumber numer karty bibliotecznej
     */
    @Schema(name = "LoanReader", description = "Podstawowe dane czytelnika")
    public record ReaderSummary(
            @Schema(description = "Identyfikator czytelnika", example = "1")
            Long id,

            @Schema(description = "Imię", example = "Anna")
            String firstName,

            @Schema(description = "Nazwisko", example = "Nowak")
            String lastName,

            @Schema(description = "Numer karty bibliotecznej", example = "LB-000001")
            String cardNumber) {
    }

    /**
     * Podstawowe dane książki i egzemplarza w wypożyczeniu.
     *
     * @param id         identyfikator książki (tytułu)
     * @param title      tytuł
     * @param bookItemId identyfikator wypożyczonego egzemplarza
     */
    @Schema(name = "LoanBook", description = "Podstawowe dane książki i egzemplarza")
    public record BookSummary(
            @Schema(description = "Identyfikator książki (tytułu)", example = "1")
            Long id,

            @Schema(description = "Tytuł", example = "Czysty kod")
            String title,

            @Schema(description = "Identyfikator wypożyczonego egzemplarza", example = "3")
            Long bookItemId) {
    }

    /**
     * Tworzy odpowiedź na podstawie encji. Wymaga załadowanego czytelnika, egzemplarza i książki,
     * więc trzeba ją wywołać wewnątrz transakcji.
     *
     * @param loan  wypożyczenie
     * @param today dzisiejsza data, do wyliczenia stanu
     * @return odpowiedź API
     */
    public static LoanResponse from(Loan loan, LocalDate today) {
        Reader reader = loan.getReader();
        BookItem item = loan.getBookItem();
        Book book = item.getBook();
        return new LoanResponse(
                loan.getId(),
                LoanStatus.of(loan, today),
                new ReaderSummary(reader.getId(), reader.getFirstName(), reader.getLastName(),
                        reader.getCardNumber()),
                new BookSummary(book.getId(), book.getTitle(), item.getId()),
                loan.getBorrowedAt(),
                loan.getDueDate(),
                loan.getReturnedAt(),
                loan.getRenewals(),
                loan.getVersion());
    }
}
