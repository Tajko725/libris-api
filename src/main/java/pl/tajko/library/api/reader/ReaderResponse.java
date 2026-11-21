package pl.tajko.library.api.reader;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Czytelnik zwracany przez API.
 *
 * @param id           identyfikator czytelnika
 * @param firstName    imię
 * @param lastName     nazwisko
 * @param email        adres e-mail zapisany małymi literami
 * @param phone        numer telefonu albo {@code null}
 * @param cardNumber   numer karty bibliotecznej
 * @param registeredAt moment rejestracji
 * @param active       czy czytelnik może wypożyczać książki
 * @param version      wersja czytelnika; trzeba ją odesłać przy aktualizacji
 */
@Schema(description = "Czytelnik biblioteki")
public record ReaderResponse(
        @Schema(description = "Identyfikator czytelnika", example = "1")
        Long id,

        @Schema(description = "Imię czytelnika", example = "Anna")
        String firstName,

        @Schema(description = "Nazwisko czytelnika", example = "Nowak")
        String lastName,

        @Schema(description = "Adres e-mail zapisany małymi literami", example = "anna.nowak@example.com")
        String email,

        @Schema(description = "Numer telefonu albo null, jeśli go nie podano", example = "+48 123 456 789")
        String phone,

        @Schema(description = "Numer karty bibliotecznej nadany przez system", example = "LB-000001")
        String cardNumber,

        @Schema(description = "Moment rejestracji czytelnika", example = "2026-11-21T03:54:23.012Z")
        Instant registeredAt,

        @Schema(description = "Czy czytelnik może wypożyczać książki", example = "true")
        boolean active,

        @Schema(description = "Wersja czytelnika; trzeba ją odesłać w żądaniu aktualizacji", example = "0")
        long version) {

    /**
     * Tworzy odpowiedź na podstawie encji.
     *
     * @param reader czytelnik
     * @return odpowiedź API
     */
    public static ReaderResponse from(Reader reader) {
        return new ReaderResponse(
                reader.getId(),
                reader.getFirstName(),
                reader.getLastName(),
                reader.getEmail(),
                reader.getPhone(),
                reader.getCardNumber(),
                reader.getRegisteredAt(),
                reader.isActive(),
                reader.getVersion());
    }
}