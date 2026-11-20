package pl.tajko.library.api.common;

/**
 * Wyjątek zgłaszany, gdy operacja koliduje ze stanem danych (np. duplikat lub konflikt wersji).
 * API zamienia go na odpowiedź 409.
 */
public class ConflictException extends RuntimeException {

    /**
     * Tworzy wyjątek z komunikatem przeznaczonym dla użytkownika API.
     *
     * @param message opis konfliktu
     */
    public ConflictException(String message) {
        super(message);
    }
}