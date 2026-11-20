package pl.tajko.library.api.book;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sprawdza, że tekst jest poprawnym numerem ISBN-10 lub ISBN-13 (z poprawną cyfrą kontrolną).
 * Wartość pusta jest uznawana za poprawną: obecność pola kontroluje {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = Isbn.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Isbn {

    /** Komunikat błędu. */
    String message() default "Niepoprawny numer ISBN (oczekiwany ISBN-10 lub ISBN-13 z poprawną cyfrą kontrolną)";

    /** Grupy walidacji (wymagane przez specyfikację). */
    Class<?>[] groups() default {};

    /** Dodatkowe dane (wymagane przez specyfikację). */
    Class<? extends Payload>[] payload() default {};

    /**
     * Implementacja walidacji oparta na {@link IsbnValidator}.
     */
    class Validator implements ConstraintValidator<Isbn, String> {

        /**
         * Sprawdza numer ISBN.
         *
         * @param value   sprawdzany tekst
         * @param context kontekst walidacji
         * @return {@code true}, jeśli tekst jest pusty lub jest poprawnym numerem ISBN
         */
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || IsbnValidator.isValid(value);
        }
    }
}