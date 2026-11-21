package pl.tajko.library.api.loan;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repozytorium wypożyczeń. Metody z {@code ReturnedAtIsNull} dotyczą wypożyczeń aktywnych (niezwróconych).
 */
public interface LoanRepository extends JpaRepository<Loan, Long> {

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
}