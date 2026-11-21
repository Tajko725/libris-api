package pl.tajko.library.api.loan;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repozytorium wypożyczeń. Metody z {@code ReturnedAtIsNull} dotyczą wypożyczeń aktywnych (niezwróconych).
 */
public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {

    /**
     * Zwraca stronę wypożyczeń razem z czytelnikiem, egzemplarzem i książką, ładowanymi tym samym zapytaniem
     * (bez dodatkowego zapytania dla każdego wypożyczenia na liście).
     *
     * @param specification warunki wyszukiwania
     * @param pageable      strona i sortowanie
     * @return strona wypożyczeń
     */
    @Override
    @EntityGraph(attributePaths = {"reader", "bookItem", "bookItem.book"})
    Page<Loan> findAll(Specification<Loan> specification, Pageable pageable);

    /**
     * Zwraca wypożyczenie razem z czytelnikiem, egzemplarzem i książką.
     *
     * @param id identyfikator wypożyczenia
     * @return wypożyczenie albo pusty wynik
     */
    @Override
    @EntityGraph(attributePaths = {"reader", "bookItem", "bookItem.book"})
    Optional<Loan> findById(Long id);

    /**
     * Szuka aktywnego wypożyczenia egzemplarza.
     *
     * @param bookItemId identyfikator egzemplarza
     * @return aktywne wypożyczenie albo pusty wynik, jeśli egzemplarz jest wolny
     */
    Optional<Loan> findByBookItemIdAndReturnedAtIsNull(Long bookItemId);

    /**
     * Sprawdza, czy egzemplarz jest teraz wypożyczony.
     *
     * @param bookItemId identyfikator egzemplarza
     * @return {@code true}, jeśli egzemplarz ma aktywne wypożyczenie
     */
    boolean existsByBookItemIdAndReturnedAtIsNull(Long bookItemId);

    /**
     * Sprawdza, czy egzemplarz ma jakiekolwiek wypożyczenie, także zakończone (historię).
     *
     * @param bookItemId identyfikator egzemplarza
     * @return {@code true}, jeśli egzemplarz ma historię wypożyczeń
     */
    boolean existsByBookItemId(Long bookItemId);

    /**
     * Sprawdza, czy czytelnik ma jakiekolwiek wypożyczenie, także zakończone (historię).
     *
     * @param readerId identyfikator czytelnika
     * @return {@code true}, jeśli czytelnik ma historię wypożyczeń
     */
    boolean existsByReaderId(Long readerId);

    /**
     * Zlicza aktywne wypożyczenia czytelnika (do sprawdzania limitu).
     *
     * @param readerId identyfikator czytelnika
     * @return liczba niezwróconych egzemplarzy
     */
    long countByReaderIdAndReturnedAtIsNull(Long readerId);

    /**
     * Sprawdza, czy czytelnik ma niezwrócone wypożyczenie z terminem wcześniejszym niż podana data.
     *
     * @param readerId identyfikator czytelnika
     * @param today    dzisiejsza data; wypożyczenie jest przeterminowane, gdy jego termin jest przed nią
     * @return {@code true}, jeśli czytelnik ma zaległość
     */
    boolean existsByReaderIdAndReturnedAtIsNullAndDueDateBefore(Long readerId, LocalDate today);

    /**
     * Zlicza aktywne wypożyczenia egzemplarzy jednej książki (tytułu).
     *
     * @param bookId identyfikator książki
     * @return liczba wypożyczonych w tej chwili egzemplarzy
     */
    @Query("""
            select count(l)
            from Loan l
            where l.bookItem.book.id = :bookId and l.returnedAt is null
            """)
    long countActiveByBookId(@Param("bookId") Long bookId);

    /**
     * Zlicza aktywne wypożyczenia dla wielu książek jednym zapytaniem. Książki bez aktywnych wypożyczeń
     * nie występują w wyniku.
     *
     * @param bookIds identyfikatory książek
     * @return liczba wypożyczonych egzemplarzy dla każdej książki, która ma co najmniej jeden
     */
    @Query("""
            select i.book.id as bookId, count(l) as loanCount
            from Loan l join l.bookItem i
            where l.returnedAt is null and i.book.id in :bookIds
            group by i.book.id
            """)
    List<ActiveLoanCount> countActiveByBookIds(@Param("bookIds") Collection<Long> bookIds);

    /**
     * Wynik zliczania aktywnych wypożyczeń dla jednej książki.
     */
    interface ActiveLoanCount {

        /**
         * Zwraca identyfikator książki.
         *
         * @return identyfikator książki
         */
        Long getBookId();

        /**
         * Zwraca liczbę wypożyczonych egzemplarzy książki.
         *
         * @return liczba aktywnych wypożyczeń
         */
        long getLoanCount();
    }
}
