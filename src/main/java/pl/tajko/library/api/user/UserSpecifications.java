package pl.tajko.library.api.user;

import org.springframework.data.jpa.domain.Specification;
import pl.tajko.library.api.common.SearchPatterns;

/**
 * Warunki wyszukiwania użytkowników składane w zapytanie (Specification).
 */
final class UserSpecifications {

    /** Prywatny konstruktor: klasa zawiera tylko metody statyczne i nie powinna być tworzona. */
    private UserSpecifications() {
    }

    /**
     * Warunek: konto ma podaną rolę.
     *
     * @param role szukana rola
     * @return warunek wyszukiwania
     */
    static Specification<AppUser> hasRole(Role role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    /**
     * Warunek: konto ma podany status (aktywne albo wyłączone).
     *
     * @param enabled szukany status
     * @return warunek wyszukiwania
     */
    static Specification<AppUser> hasEnabled(boolean enabled) {
        return (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    /**
     * Warunek: nazwa użytkownika zawiera podany tekst, bez rozróżniania wielkości liter. Znaki specjalne
     * wyrażenia LIKE ({@code %}, {@code _}) są traktowane dosłownie.
     *
     * @param text szukany fragment, niepusty
     * @return warunek wyszukiwania
     */
    static Specification<AppUser> usernameContains(String text) {
        String pattern = SearchPatterns.contains(text);
        return (root, query, cb) -> cb.like(cb.lower(root.<String>get("username")), pattern, SearchPatterns.ESCAPE);
    }
}
