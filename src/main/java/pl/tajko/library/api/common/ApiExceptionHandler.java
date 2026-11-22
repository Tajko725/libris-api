package pl.tajko.library.api.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Globalna obsługa błędów API. Wszystkie błędy są zwracane w formacie Problem Details
 * ({@code application/problem+json}), a standardowe błędy Spring MVC (np. niepoprawny JSON,
 * zły typ parametru, nieznany adres) obsługuje klasa bazowa.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Opis błędu walidacji jednego pola żądania.
     *
     * @param field   nazwa pola, którego dotyczy błąd
     * @param message komunikat dla użytkownika
     */
    public record FieldViolation(String field, String message) {
    }

    /**
     * Zamienia brak zasobu na odpowiedź 404.
     *
     * @param ex zgłoszony wyjątek
     * @return opis błędu
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Nie znaleziono zasobu", ex.getMessage());
    }

    /**
     * Zamienia konflikt stanu danych na odpowiedź 409.
     *
     * @param ex zgłoszony wyjątek
     * @return opis błędu
     */
    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Konflikt danych", ex.getMessage());
    }

    /**
     * Zamienia konflikt wersji wykryty przez Hibernate (równoczesny zapis) na odpowiedź 409.
     *
     * @param ex zgłoszony wyjątek
     * @return opis błędu
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Konflikt wersji",
                "Dane zostały w międzyczasie zmienione przez kogoś innego. Pobierz je ponownie i spróbuj jeszcze raz.");
    }

    /**
     * Zamienia niepoprawne żądanie na odpowiedź 400.
     *
     * @param ex zgłoszony wyjątek
     * @return opis błędu
     */
    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Niepoprawne żądanie", ex.getMessage());
    }

    /**
     * Zamienia naruszenie ograniczenia bazy (np. duplikat wykryty dopiero przy zapisie przy równoczesnych
     * żądaniach) na odpowiedź 409. Treść błędu bazy trafia do logu bez wartości, bo mogłaby zawierać dane osobowe.
     *
     * @param ex zgłoszony wyjątek
     * @return opis błędu
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        LOG.warn("Naruszenie ograniczenia bazy danych ({})", ex.getMostSpecificCause().getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Konflikt danych",
                "Dane naruszają ograniczenia bazy (np. taki rekord już istnieje). Sprawdź dane i spróbuj ponownie.");
    }

    /**
     * Zamienia nieudane logowanie na odpowiedź 401. Komunikat jest taki sam dla każdej przyczyny
     * (zła nazwa, złe hasło, konto wyłączone), żeby nie ujawniać, które konta istnieją. Nazwy
     * użytkownika nie zapisuję w logu, bo ktoś mógł wpisać w to pole hasło.
     *
     * @param ex zgłoszony wyjątek
     * @return opis błędu
     */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        LOG.info("Nieudana próba logowania ({})", ex.getClass().getSimpleName());
        return problem(HttpStatus.UNAUTHORIZED, "Nieudane logowanie", "Niepoprawna nazwa użytkownika lub hasło.");
    }

    /**
     * Ostatnia linia obrony: nieoczekiwany błąd jest logowany, a użytkownik dostaje ogólny komunikat
     * bez szczegółów technicznych.
     *
     * @param ex zgłoszony wyjątek
     * @return opis błędu 500
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Nieoczekiwany błąd w obsłudze żądania", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Błąd serwera",
                "Wystąpił nieoczekiwany błąd. Spróbuj ponownie później.");
    }

    /**
     * Zamienia błędy walidacji ciała żądania na odpowiedź 400 z listą błędnych pól.
     *
     * @param ex      wyjątek z wynikami walidacji
     * @param headers nagłówki odpowiedzi
     * @param status  status odpowiedzi
     * @param request bieżące żądanie
     * @return odpowiedź z opisem błędów
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Błąd walidacji", "Dane żądania są nieprawidłowe.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}