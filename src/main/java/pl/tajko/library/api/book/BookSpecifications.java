package pl.tajko.library.api.book;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Warunki wyszukiwania książek składane w zapytanie (Specification).
 */
final class BookSpecifications {

    /** Znak ucieczki w wyrażeniach LIKE. */
    private static final char ESCAPE = '\\';

    /** Prywatny konstruktor: klasa zawiera tylko metody statyczne i nie powinna być tworzona. */
    private BookSpecifications() {
    }

    /**
     * Warunek: książka ma podany gatunek.
     *
     * @param genre gatunek
     * @return warunek wyszukiwania
     */
    static Specification<Book> hasGenre(Genre genre) {
        return (root, query, cb) -> cb.equal(root.get("genre"), genre);
    }

    /**
     * Warunek: tytuł lub autor zawiera podany tekst, bez rozróżniania wielkości liter.
     * Znaki specjalne wyrażenia LIKE ({@code %}, {@code _}) są traktowane dosłownie.
     *
     * @param text szukany fragment, niepusty
     * @return warunek wyszukiwania
     */
    static Specification<Book> titleOrAuthorContains(String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.<String>get("title")), pattern, ESCAPE),
                cb.like(cb.lower(root.<String>get("author")), pattern, ESCAPE));
    }

    /**
     * Zabezpiecza znaki specjalne wyrażenia LIKE, żeby wyszukiwanie "100%" szukało dosłownie "100%".
     *
     * @param text tekst użytkownika
     * @return tekst z poprzedzonymi znakami {@code \}, {@code %} i {@code _}
     */
    static String escapeLike(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}