package pl.tajko.library.api.book;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repozytorium fizycznych egzemplarzy książek.
 */
public interface BookItemRepository extends JpaRepository<BookItem, Long> {

    /**
     * Zwraca wszystkie egzemplarze danego tytułu.
     *
     * @param bookId identyfikator tytułu
     * @return lista egzemplarzy, pusta, jeśli tytuł nie ma żadnego egzemplarza
     */
    List<BookItem> findAllByBookId(Long bookId);

    /**
     * Zlicza egzemplarze danego tytułu.
     *
     * @param bookId identyfikator tytułu
     * @return liczba egzemplarzy
     */
    long countByBookId(Long bookId);

    /**
     * Zlicza egzemplarze dla wielu tytułów jednym zapytaniem (unika zapytania osobno dla każdej książki).
     * Tytuły bez egzemplarzy nie występują w wyniku.
     *
     * @param bookIds identyfikatory tytułów
     * @return liczba egzemplarzy dla każdego tytułu, który ma co najmniej jeden
     */
    @Query("""
            select i.book.id as bookId, count(i) as itemCount
            from BookItem i
            where i.book.id in :bookIds
            group by i.book.id
            """)
    List<ItemCount> countByBookIds(@Param("bookIds") Collection<Long> bookIds);

    /**
     * Wynik zliczania egzemplarzy dla jednego tytułu.
     */
    interface ItemCount {

        /**
         * Zwraca identyfikator tytułu.
         *
         * @return identyfikator książki
         */
        Long getBookId();

        /**
         * Zwraca liczbę egzemplarzy tytułu.
         *
         * @return liczba egzemplarzy
         */
        long getItemCount();
    }
}