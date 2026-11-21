package pl.tajko.library.api.reader;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}