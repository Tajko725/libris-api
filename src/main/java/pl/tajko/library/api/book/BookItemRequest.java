package pl.tajko.library.api.book;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Dane potrzebne do dodania egzemplarzy książki.
 *
 * @param count liczba egzemplarzy do dodania, od 1 do {@link BookItemService#MAX_COPIES_PER_REQUEST}
 */
@Schema(description = "Liczba egzemplarzy do dodania do książki")
public record BookItemRequest(
        @Schema(description = "Liczba egzemplarzy do dodania (od 1 do 100)", example = "3")
        @NotNull(message = "Liczba egzemplarzy jest wymagana")
        @Min(value = 1, message = "Liczba egzemplarzy musi wynosić co najmniej 1")
        @Max(value = BookItemService.MAX_COPIES_PER_REQUEST,
                message = "Jednorazowo można dodać najwyżej 100 egzemplarzy")
        Integer count) {
}