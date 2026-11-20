package pl.tajko.library.api.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Year;

/**
 * Tytuł książki (opis bibliograficzny) zapisany w tabeli {@code book}.
 * <p>
 * Jeden wiersz odpowiada jednemu numerowi ISBN. Fizyczne egzemplarze tego tytułu
 * to osobne obiekty {@link BookItem}. Numer ISBN jest zawsze przechowywany jako ISBN-13.
 * <p>
 * Pole {@code version} służy do optymistycznego blokowania: jeśli dwóch użytkowników
 * edytuje tę samą książkę, drugi zapis ze starą wersją kończy się wyjątkiem.
 * <p>
 * Dwie książki są równe, gdy mają ten sam, niepusty identyfikator.
 */
@Entity
@Table(name = "book")
public class Book {

    /** Najwcześniejszy dopuszczalny rok wydania (początek druku w Europie). */
    public static final int MIN_PUBLICATION_YEAR = 1450;

    /** Maksymalna długość tytułu i autora, zgodna z kolumnami w bazie. */
    public static final int MAX_TEXT_LENGTH = 255;

    /** Identyfikator nadawany przez bazę danych; {@code null}, dopóki książka nie jest zapisana. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tytuł książki. */
    @Column(name = "title", nullable = false, length = MAX_TEXT_LENGTH)
    private String title;

    /** Autor książki. */
    @Column(name = "author", nullable = false, length = MAX_TEXT_LENGTH)
    private String author;

    /** Rok wydania. */
    @Column(name = "publication_year", nullable = false)
    private int publicationYear;

    /** Numer ISBN-13 bez myślników. */
    @Column(name = "isbn", nullable = false, length = 13)
    private String isbn;

    /** Gatunek książki, zapisywany w bazie jako nazwa stałej. */
    @Enumerated(EnumType.STRING)
    @Column(name = "genre", nullable = false, length = 32)
    private Genre genre;

    /** Wersja wiersza do optymistycznego blokowania, zwiększana przez Hibernate przy każdej zmianie. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Konstruktor bez argumentów wymagany przez JPA; nie jest przeznaczony do użycia w kodzie. */
    protected Book() {
    }

    /**
     * Tworzy nową, jeszcze niezapisaną książkę.
     *
     * @param title           tytuł, niepusty, do 255 znaków
     * @param author          autor, niepusty, do 255 znaków
     * @param publicationYear rok wydania, od {@link #MIN_PUBLICATION_YEAR} do bieżącego roku
     * @param isbn            poprawny numer ISBN-10 lub ISBN-13 (zapisywany jako ISBN-13)
     * @param genre           gatunek, nie może być {@code null}
     * @throws IllegalArgumentException jeśli którykolwiek argument jest niepoprawny
     */
    public Book(String title, String author, int publicationYear, String isbn, Genre genre) {
        this.title = requireText(title, "Tytuł");
        this.author = requireText(author, "Autor");
        this.publicationYear = requireValidYear(publicationYear);
        this.isbn = IsbnValidator.toIsbn13(isbn);
        this.genre = requireGenre(genre);
    }

    /**
     * Zwraca identyfikator nadany przez bazę.
     *
     * @return identyfikator lub {@code null}, jeśli książka nie została jeszcze zapisana
     */
    public Long getId() {
        return id;
    }

    /**
     * Zwraca tytuł książki.
     *
     * @return tytuł
     */
    public String getTitle() {
        return title;
    }

    /**
     * Ustawia nowy tytuł. Wartość jest przycinana z białych znaków na początku i końcu.
     *
     * @param title nowy tytuł, niepusty, do 255 znaków
     * @throws IllegalArgumentException jeśli tytuł jest pusty lub za długi
     */
    public void setTitle(String title) {
        this.title = requireText(title, "Tytuł");
    }

    /**
     * Zwraca autora książki.
     *
     * @return autor
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Ustawia nowego autora. Wartość jest przycinana z białych znaków na początku i końcu.
     *
     * @param author nowy autor, niepusty, do 255 znaków
     * @throws IllegalArgumentException jeśli autor jest pusty lub za długi
     */
    public void setAuthor(String author) {
        this.author = requireText(author, "Autor");
    }

    /**
     * Zwraca rok wydania.
     *
     * @return rok wydania
     */
    public int getPublicationYear() {
        return publicationYear;
    }

    /**
     * Ustawia nowy rok wydania.
     *
     * @param publicationYear rok od {@link #MIN_PUBLICATION_YEAR} do bieżącego roku
     * @throws IllegalArgumentException jeśli rok jest poza dopuszczalnym zakresem
     */
    public void setPublicationYear(int publicationYear) {
        this.publicationYear = requireValidYear(publicationYear);
    }

    /**
     * Zwraca numer ISBN-13 bez myślników.
     *
     * @return numer ISBN-13 złożony z 13 cyfr
     */
    public String getIsbn() {
        return isbn;
    }

    /**
     * Ustawia nowy numer ISBN. Numer jest sprawdzany i zamieniany na ISBN-13.
     *
     * @param isbn poprawny numer ISBN-10 lub ISBN-13
     * @throws IllegalArgumentException jeśli numer jest niepoprawny
     */
    public void setIsbn(String isbn) {
        this.isbn = IsbnValidator.toIsbn13(isbn);
    }

    /**
     * Zwraca gatunek książki.
     *
     * @return gatunek
     */
    public Genre getGenre() {
        return genre;
    }

    /**
     * Ustawia nowy gatunek.
     *
     * @param genre nowy gatunek, nie może być {@code null}
     * @throws IllegalArgumentException jeśli gatunek jest {@code null}
     */
    public void setGenre(Genre genre) {
        this.genre = requireGenre(genre);
    }

    /**
     * Zwraca wersję wiersza używaną do wykrywania równoczesnych edycji.
     *
     * @return numer wersji, 0 dla świeżo zapisanej książki
     */
    public long getVersion() {
        return version;
    }

    /**
     * Porównuje książki na podstawie identyfikatora. Książki jeszcze niezapisane
     * (bez identyfikatora) są równe tylko samym sobie.
     *
     * @param o porównywany obiekt
     * @return {@code true}, jeśli obiekt to ta sama książka
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Book other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    /**
     * Zwraca kod skrótu stały dla klasy. Identyfikator nie nadaje się do skrótu,
     * bo zmienia się przy pierwszym zapisie, a skrót obiektu w zbiorze nie może się zmieniać.
     *
     * @return kod skrótu
     */
    @Override
    public int hashCode() {
        return Book.class.hashCode();
    }

    /**
     * Zwraca czytelny opis książki, przydatny w logach i podczas debugowania.
     *
     * @return tekst z polami książki
     */
    @Override
    public String toString() {
        return "Book{id=" + id
                + ", title='" + title + '\''
                + ", author='" + author + '\''
                + ", publicationYear=" + publicationYear
                + ", isbn='" + isbn + '\''
                + ", genre=" + genre
                + ", version=" + version
                + '}';
    }

    /**
     * Sprawdza, że tekst nie jest pusty ani za długi, i zwraca go bez białych znaków na brzegach.
     *
     * @param value     sprawdzany tekst
     * @param fieldName nazwa pola użyta w komunikacie błędu
     * @return przycięty tekst
     * @throws IllegalArgumentException jeśli tekst jest {@code null}, pusty lub dłuższy niż 255 znaków
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " nie może być pusty");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " nie może przekraczać " + MAX_TEXT_LENGTH + " znaków");
        }
        return trimmed;
    }

    /**
     * Sprawdza, że rok mieści się w dopuszczalnym zakresie.
     *
     * @param year sprawdzany rok
     * @return ten sam rok, jeśli jest poprawny
     * @throws IllegalArgumentException jeśli rok jest poza zakresem
     */
    private static int requireValidYear(int year) {
        int maxYear = Year.now().getValue();
        if (year < MIN_PUBLICATION_YEAR || year > maxYear) {
            throw new IllegalArgumentException(
                    "Rok wydania musi mieścić się w zakresie " + MIN_PUBLICATION_YEAR + "-" + maxYear);
        }
        return year;
    }

    /**
     * Sprawdza, że gatunek został podany.
     *
     * @param genre sprawdzany gatunek
     * @return ten sam gatunek
     * @throws IllegalArgumentException jeśli gatunek jest {@code null}
     */
    private static Genre requireGenre(Genre genre) {
        if (genre == null) {
            throw new IllegalArgumentException("Gatunek nie może być pusty");
        }
        return genre;
    }
}