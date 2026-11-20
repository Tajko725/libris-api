package pl.tajko.library.api.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dane potrzebne do dodania nowej książki.
 *
 * @param title           tytuł, niepusty, do 255 znaków
 * @param author          autor, niepusty, do 255 znaków
 * @param publicationYear rok wydania, od 1450 do bieżącego roku
 * @param isbn            numer ISBN-10 lub ISBN-13 (zapisywany jako ISBN-13)
 * @param genre           gatunek
 */
public record BookRequest(
        @NotBlank(message = "Tytuł nie może być pusty")
        @Size(max = Book.MAX_TEXT_LENGTH, message = "Tytuł nie może przekraczać 255 znaków")
        String title,

        @NotBlank(message = "Autor nie może być pusty")
        @Size(max = Book.MAX_TEXT_LENGTH, message = "Autor nie może przekraczać 255 znaków")
        String author,

        @NotNull(message = "Rok wydania jest wymagany")
        @PublicationYear
        Integer publicationYear,

        @NotBlank(message = "Numer ISBN nie może być pusty")
        @Isbn
        String isbn,

        @NotNull(message = "Gatunek jest wymagany")
        Genre genre) {
}