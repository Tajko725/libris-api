package pl.tajko.library.api.book;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repozytorium tytułów książek. Spring Data tworzy implementację automatycznie
 * na podstawie nazw metod.
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Wyszukuje książkę po numerze ISBN-13.
     *
     * @param isbn numer ISBN-13 bez myślników
     * @return znaleziona książka lub pusty wynik
     */
    Optional<Book> findByIsbn(String isbn);

    /**
     * Sprawdza, czy istnieje książka o podanym numerze ISBN-13.
     *
     * @param isbn numer ISBN-13 bez myślników
     * @return {@code true}, jeśli taka książka istnieje
     */
    boolean existsByIsbn(String isbn);
}