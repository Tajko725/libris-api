package pl.tajko.library.api.book;

import org.springframework.data.jpa.domain.Specification;
import pl.tajko.library.api.common.SearchPatterns;

/**
 * Warunki wyszukiwania książek składane w zapytanie (Specification).
 */
final class BookSpecifications {

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
        String pattern = SearchPatterns.contains(text);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.<String>get("title")), pattern, SearchPatterns.ESCAPE),
                cb.like(cb.lower(root.<String>get("author")), pattern, SearchPatterns.ESCAPE));
    }
}