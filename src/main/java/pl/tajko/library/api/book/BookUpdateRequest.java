package pl.tajko.library.api.book;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dane potrzebne do zaktualizowania książki. Pole {@code version} to wersja, którą klient
 * wcześniej pobrał; jeśli książka zdążyła się zmienić, aktualizacja jest odrzucana.
 *
 * @param title           tytuł, niepusty, do 255 znaków
 * @param author          autor, niepusty, do 255 znaków
 * @param publicationYear rok wydania, od 1450 do bieżącego roku
 * @param isbn            numer ISBN-10 lub ISBN-13 (zapisywany jako ISBN-13)
 * @param genre           gatunek
 * @param version         wersja książki, na podstawie której klient wprowadził zmiany
 */
@Schema(description = "Nowe dane książki razem z wersją, którą klient wcześniej pobrał")
public record BookUpdateRequest(
        @Schema(description = "Tytuł książki", example = "Czysty kod. Wydanie 2")
        @NotBlank(message = "Tytuł nie może być pusty")
        @Size(max = Book.MAX_TEXT_LENGTH, message = "Tytuł nie może przekraczać 255 znaków")
        String title,

        @Schema(description = "Autor książki", example = "Robert C. Martin")
        @NotBlank(message = "Autor nie może być pusty")
        @Size(max = Book.MAX_TEXT_LENGTH, message = "Autor nie może przekraczać 255 znaków")
        String author,

        @Schema(description = "Rok wydania, od 1450 do bieżącego roku", example = "2008")
        @NotNull(message = "Rok wydania jest wymagany")
        @PublicationYear
        Integer publicationYear,

        @Schema(description = "Numer ISBN-10 lub ISBN-13 z poprawną cyfrą kontrolną (myślniki są dozwolone). "
                + "Zapisywany jako ISBN-13 bez myślników", example = "9780132350884")
        @NotBlank(message = "Numer ISBN nie może być pusty")
        @Isbn
        String isbn,

        @Schema(description = "Gatunek książki", example = "SCIENCE")
        @NotNull(message = "Gatunek jest wymagany")
        Genre genre,

        @Schema(description = "Wersja książki z ostatniego odczytu. Jeśli ktoś zmienił książkę w międzyczasie, "
                + "serwer odpowie kodem 409", example = "0")
        @NotNull(message = "Wersja jest wymagana")
        Long version) {
}