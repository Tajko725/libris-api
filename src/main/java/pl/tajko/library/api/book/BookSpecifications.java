package pl.tajko.library.api.book;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import pl.tajko.library.api.common.SearchPatterns;
import pl.tajko.library.api.loan.Loan;

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

    /**
     * Warunek dostępności: książka ma (albo nie ma) co najmniej jeden egzemplarz, który nie jest teraz
     * wypożyczony. Książka bez żadnego egzemplarza jest niedostępna.
     *
     * @param available {@code true} dla książek z wolnym egzemplarzem, {@code false} dla pozostałych
     * @return warunek wyszukiwania
     */
    static Specification<Book> hasAvailableCopy(boolean available) {
        return (root, query, cb) -> {
            Subquery<Long> freeItem = query.subquery(Long.class);
            Root<BookItem> item = freeItem.from(BookItem.class);

            Subquery<Long> activeLoan = freeItem.subquery(Long.class);
            Root<Loan> loan = activeLoan.from(Loan.class);
            activeLoan.select(loan.<Long>get("id")).where(
                    cb.equal(loan.get("bookItem"), item),
                    cb.isNull(loan.get("returnedAt")));

            freeItem.select(item.<Long>get("id")).where(
                    cb.equal(item.get("book"), root),
                    cb.not(cb.exists(activeLoan)));

            Predicate hasFreeItem = cb.exists(freeItem);
            return available ? hasFreeItem : cb.not(hasFreeItem);
        };
    }
}
