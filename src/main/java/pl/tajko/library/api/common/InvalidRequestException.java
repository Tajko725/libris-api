package pl.tajko.library.api.common;

/**
 * Wyjątek zgłaszany, gdy żądanie jest niepoprawne w sposób, którego nie wychwyciła walidacja pól
 * (np. niedozwolone pole sortowania). API zamienia go na odpowiedź 400.
 */
public class InvalidRequestException extends RuntimeException {

    /**
     * Tworzy wyjątek z komunikatem przeznaczonym dla użytkownika API.
     *
     * @param message opis błędu w żądaniu
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}