package pl.tajko.library.api.reader;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repozytorium czytelników.
 */
public interface ReaderRepository extends JpaRepository<Reader, Long>, JpaSpecificationExecutor<Reader> {

    /**
     * Szuka czytelnika po adresie e-mail (zapisanym małymi literami).
     *
     * @param email adres e-mail
     * @return czytelnik albo pusty wynik
     */
    Optional<Reader> findByEmail(String email);

    /**
     * Sprawdza, czy istnieje czytelnik z podanym adresem e-mail.
     *
     * @param email adres e-mail zapisany małymi literami
     * @return {@code true}, jeśli adres jest już zajęty
     */
    boolean existsByEmail(String email);

    /**
     * Szuka czytelnika po numerze karty bibliotecznej.
     *
     * @param cardNumber numer karty, np. {@code LB-000001}
     * @return czytelnik albo pusty wynik
     */
    Optional<Reader> findByCardNumber(String cardNumber);

    /**
     * Pobiera czytelnika z blokadą zapisu na jego wierszu do końca transakcji. Dzięki temu dwa równoczesne
     * wypożyczenia tego samego czytelnika wykonują się jedno po drugim i nie da się obejść limitu wypożyczeń.
     * Wywołuj tylko w transakcji.
     *
     * @param id identyfikator czytelnika
     * @return czytelnik albo pusty wynik
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reader r where r.id = :id")
    Optional<Reader> findByIdForUpdate(@Param("id") Long id);
}
