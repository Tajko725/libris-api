package pl.tajko.library.api.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stabilna, własna reprezentacja jednej strony wyników w odpowiedzi API.
 *
 * @param content       elementy bieżącej strony
 * @param page          numer strony liczony od 0
 * @param size          rozmiar strony
 * @param totalElements łączna liczba elementów we wszystkich stronach
 * @param totalPages    łączna liczba stron
 * @param <T>           typ elementu strony
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /**
     * Tworzy odpowiedź na podstawie strony ze Spring Data.
     *
     * @param page strona wyników
     * @param <T>  typ elementu strony
     * @return odpowiedź z danymi strony
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}