package pl.tajko.library.api.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Opis błędu zwracanego przez API w formacie Problem Details (RFC 9457). Rekord służy wyłącznie
 * do dokumentacji OpenAPI: pokazuje dokładnie te pola, które widzi klient w odpowiedzi.
 *
 * @param title  krótki tytuł błędu
 * @param status kod HTTP
 * @param detail opis błędu dla użytkownika
 * @param instance adres żądania, którego dotyczy błąd
 * @param errors lista błędnych pól (tylko przy błędzie walidacji 400)
 */
@Schema(name = "Problem", description = "Opis błędu w formacie Problem Details (RFC 9457)")
public record ProblemResponse(
        @Schema(description = "Krótki tytuł błędu", example = "Nie znaleziono zasobu")
        String title,

        @Schema(description = "Kod statusu HTTP", example = "404")
        int status,

        @Schema(description = "Opis błędu dla użytkownika", example = "Nie znaleziono książki o id 999")
        String detail,

        @Schema(description = "Adres żądania, którego dotyczy błąd", example = "/api/v1/books/999")
        String instance,

        @Schema(description = "Lista błędnych pól; występuje tylko przy błędzie walidacji (400)")
        List<ApiExceptionHandler.FieldViolation> errors) {
}