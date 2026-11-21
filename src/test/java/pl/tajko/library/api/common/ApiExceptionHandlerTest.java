package pl.tajko.library.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Testy jednostkowe zamiany wyjątków na odpowiedzi Problem Details.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    /**
     * Naruszenie ograniczenia bazy daje 409 i nie ujawnia treści błędu bazy.
     */
    @Test
    void shouldMapDataIntegrityViolationTo409WithoutLeakingDetails() {
        ProblemDetail problem = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key value (email)=(jan@example.com)"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Konflikt danych");
        assertThat(problem.getDetail()).doesNotContain("jan@example.com").doesNotContain("duplicate key");
    }

    /**
     * Brak zasobu daje 404 z komunikatem wyjątku.
     */
    @Test
    void shouldMapNotFoundTo404() {
        ProblemDetail problem = handler.handleNotFound(new ResourceNotFoundException("Nie znaleziono X"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Nie znaleziono X");
    }

    /**
     * Konflikty (własny wyjątek i konflikt wersji z Hibernate) dają 409.
     */
    @Test
    void shouldMapConflictsTo409() {
        assertThat(handler.handleConflict(new ConflictException("duplikat")).getStatus()).isEqualTo(409);
        assertThat(handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException(String.class, 1L))
                .getStatus()).isEqualTo(409);
    }

    /**
     * Niepoprawne żądanie daje 400.
     */
    @Test
    void shouldMapInvalidRequestTo400() {
        assertThat(handler.handleInvalidRequest(new InvalidRequestException("zły sort")).getStatus())
                .isEqualTo(400);
    }

    /**
     * Nieoczekiwany błąd daje 500 i ogólny komunikat bez szczegółów technicznych.
     */
    @Test
    void shouldHideDetailsOfUnexpectedErrors() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("tajne szczegóły"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("tajne");
    }
}