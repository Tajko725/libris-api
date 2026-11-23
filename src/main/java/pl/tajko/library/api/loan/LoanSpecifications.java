package pl.tajko.library.api.loan;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Warunki wyszukiwania wypożyczeń składane w zapytanie (Specification).
 */
final class LoanSpecifications {

    /** Prywatny konstruktor: klasa zawiera tylko metody statyczne i nie powinna być tworzona. */
    private LoanSpecifications() {
    }

    /**
     * Warunek: wypożyczenie należy do podanego czytelnika.
     *
     * @param readerId identyfikator czytelnika
     * @return warunek wyszukiwania
     */
    static Specification<Loan> byReader(Long readerId) {
        return (root, query, cb) -> cb.equal(root.get("reader").get("id"), readerId);
    }

    /**
     * Warunek: wypożyczony egzemplarz należy do podanej książki (tytułu).
     *
     * @param bookId identyfikator książki
     * @return warunek wyszukiwania
     */
    static Specification<Loan> byBook(Long bookId) {
        return (root, query, cb) -> cb.equal(root.get("bookItem").get("book").get("id"), bookId);
    }

    /**
     * Warunek: wypożyczenie ma podany stan. Przeterminowane to niezwrócone z terminem przed dzisiejszą datą,
     * tak samo jak w {@link Loan#isOverdue(LocalDate)}.
     *
     * @param status stan wypożyczenia
     * @param today  dzisiejsza data
     * @return warunek wyszukiwania
     */
    static Specification<Loan> hasStatus(LoanStatus status, LocalDate today) {
        return (root, query, cb) -> switch (status) {
            case RETURNED -> cb.isNotNull(root.get("returnedAt"));
            case ACTIVE -> cb.and(
                    cb.isNull(root.get("returnedAt")),
                    cb.greaterThanOrEqualTo(root.<LocalDate>get("dueDate"), today));
            case OVERDUE -> cb.and(
                    cb.isNull(root.get("returnedAt")),
                    cb.lessThan(root.<LocalDate>get("dueDate"), today));
        };
    }
}
