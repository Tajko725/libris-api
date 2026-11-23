package pl.tajko.library.api.reader;

import org.springframework.data.jpa.domain.Specification;
import pl.tajko.library.api.common.SearchPatterns;

/**
 * Warunki wyszukiwania czytelników składane w zapytanie (Specification).
 */
final class ReaderSpecifications {

    /** Prywatny konstruktor: klasa zawiera tylko metody statyczne i nie powinna być tworzona. */
    private ReaderSpecifications() {
    }

    /**
     * Warunek: czytelnik ma podany status aktywności.
     *
     * @param active szukany status
     * @return warunek wyszukiwania
     */
    static Specification<Reader> hasActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    /**
     * Warunek: imię, nazwisko, "imię nazwisko", e-mail lub numer karty zawiera podany tekst, bez rozróżniania
     * wielkości liter. Znaki specjalne wyrażenia LIKE ({@code %}, {@code _}) są traktowane dosłownie.
     *
     * @param text szukany fragment, niepusty
     * @return warunek wyszukiwania
     */
    static Specification<Reader> matchesText(String text) {
        String pattern = SearchPatterns.contains(text);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.<String>get("firstName")), pattern, SearchPatterns.ESCAPE),
                cb.like(cb.lower(root.<String>get("lastName")), pattern, SearchPatterns.ESCAPE),
                cb.like(cb.lower(cb.concat(cb.concat(root.<String>get("firstName"), " "),
                        root.<String>get("lastName"))), pattern, SearchPatterns.ESCAPE),
                cb.like(cb.lower(root.<String>get("email")), pattern, SearchPatterns.ESCAPE),
                cb.like(cb.lower(root.<String>get("cardNumber")), pattern, SearchPatterns.ESCAPE));
    }
}