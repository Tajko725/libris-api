package pl.tajko.library.api.book;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repozytorium fizycznych egzemplarzy książek.
 */
public interface BookItemRepository extends JpaRepository<BookItem, Long> {

    /**
     * Zwraca wszystkie egzemplarze danego tytułu.
     *
     * @param bookId identyfikator tytułu
     * @return lista egzemplarzy, pusta jeśli tytuł nie ma żadnego egzemplarza
     */
    List<BookItem> findAllByBookId(Long bookId);

    /**
     * Zlicza egzemplarze danego tytułu.
     *
     * @param bookId identyfikator tytułu
     * @return liczba egzemplarzy
     */
    long countByBookId(Long bookId);
}