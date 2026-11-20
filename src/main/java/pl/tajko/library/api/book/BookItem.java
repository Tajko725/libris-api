package pl.tajko.library.api.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Fizyczny egzemplarz książki stojący na półce, zapisany w tabeli {@code book_item}.
 * <p>
 * Każdy egzemplarz należy do dokładnie jednego tytułu ({@link Book}). Trzy sztuki tej samej
 * książki to trzy wiersze {@code book_item} wskazujące na jeden wiersz {@code book}.
 * Informacja, czy egzemplarz jest wypożyczony, nie jest tu przechowywana:
 * będzie wynikać z aktywnego wypożyczenia.
 */
@Entity
@Table(name = "book_item")
public class BookItem {

    /** Identyfikator nadawany przez bazę danych; {@code null}, dopóki egzemplarz nie jest zapisany. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tytuł, którego egzemplarzem jest ten obiekt; ładowany leniwie, dopiero gdy jest potrzebny. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /** Wersja wiersza do optymistycznego blokowania. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Konstruktor bez argumentów wymagany przez JPA; nie jest przeznaczony do użycia w kodzie. */
    protected BookItem() {
    }

    /**
     * Tworzy nowy, jeszcze niezapisany egzemplarz wskazanego tytułu.
     *
     * @param book tytuł, którego egzemplarz powstaje, nie może być {@code null}
     * @throws IllegalArgumentException jeśli książka jest {@code null}
     */
    public BookItem(Book book) {
        if (book == null) {
            throw new IllegalArgumentException("Książka nie może być pusta");
        }
        this.book = book;
    }

    /**
     * Zwraca identyfikator nadany przez bazę.
     *
     * @return identyfikator lub {@code null}, jeśli egzemplarz nie został jeszcze zapisany
     */
    public Long getId() {
        return id;
    }

    /**
     * Zwraca tytuł, którego egzemplarzem jest ten obiekt. Dane tytułu są ładowane leniwie,
     * więc odczyt poza transakcją może zakończyć się wyjątkiem.
     *
     * @return książka
     */
    public Book getBook() {
        return book;
    }

    /**
     * Zwraca wersję wiersza używaną do wykrywania równoczesnych edycji.
     *
     * @return numer wersji
     */
    public long getVersion() {
        return version;
    }

    /**
     * Porównuje egzemplarze na podstawie identyfikatora. Egzemplarze jeszcze niezapisane
     * są równe tylko samym sobie.
     *
     * @param o porównywany obiekt
     * @return {@code true}, jeśli obiekt to ten sam egzemplarz
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookItem other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    /**
     * Zwraca kod skrótu stały dla klasy (identyfikator zmienia się przy pierwszym zapisie).
     *
     * @return kod skrótu
     */
    @Override
    public int hashCode() {
        return BookItem.class.hashCode();
    }

    /**
     * Zwraca czytelny opis egzemplarza bez ładowania książki.
     *
     * @return tekst z identyfikatorem i wersją
     */
    @Override
    public String toString() {
        return "BookItem{id=" + id + ", version=" + version + '}';
    }
}