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
import java.time.Year;

/**
 * Sprawdza, że rok wydania mieści się w zakresie od {@link Book#MIN_PUBLICATION_YEAR} do bieżącego roku.
 * Wartość {@code null} jest uznawana za poprawną: obecność pola kontroluje {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = PublicationYear.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicationYear {

    /** Komunikat błędu. */
    String message() default "Rok wydania musi być z zakresu od 1450 do bieżącego roku";

    /** Grupy walidacji (wymagane przez specyfikację). */
    Class<?>[] groups() default {};

    /** Dodatkowe dane (wymagane przez specyfikację). */
    Class<? extends Payload>[] payload() default {};

    /**
     * Implementacja walidacji zakresu roku wydania.
     */
    class Validator implements ConstraintValidator<PublicationYear, Integer> {

        /**
         * Sprawdza rok wydania.
         *
         * @param value   sprawdzany rok
         * @param context kontekst walidacji
         * @return {@code true}, jeśli rok jest {@code null} lub mieści się w dopuszczalnym zakresie
         */
        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return value == null || (value >= Book.MIN_PUBLICATION_YEAR && value <= Year.now().getValue());
        }
    }
}