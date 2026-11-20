package pl.tajko.library.api.common;

/**
 * Wyjątek zgłaszany, gdy żądany zasób nie istnieje. API zamienia go na odpowiedź 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Tworzy wyjątek z komunikatem przeznaczonym dla użytkownika API.
     *
     * @param message opis tego, czego nie znaleziono
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}