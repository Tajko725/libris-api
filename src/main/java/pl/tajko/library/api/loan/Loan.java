package pl.tajko.library.api.loan;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import pl.tajko.library.api.book.BookItem;
import pl.tajko.library.api.reader.Reader;

/**
 * Wypożyczenie jednego egzemplarza książki przez czytelnika. Wypożyczenie jest aktywne, dopóki nie ma
 * daty zwrotu. Egzemplarz i czytelnik nie zmieniają się po utworzeniu, a termin zwrotu można przedłużyć
 * najwyżej {@value #MAX_RENEWALS} raz i tylko przed jego upływem. Reguły dotyczące wielu wypożyczeń naraz
 * (limit, zaległości, wolny egzemplarz) sprawdza serwis, a ostatnią linią obrony są ograniczenia w bazie.
 */
@Entity
@Table(name = "loan")
public class Loan {

    /** Największa liczba przedłużeń terminu zwrotu jednego wypożyczenia. */
    public static final int MAX_RENEWALS = 1;

    /** Identyfikator nadawany przez bazę. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Wypożyczony egzemplarz. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_item_id", nullable = false, updatable = false)
    private BookItem bookItem;

    /** Czytelnik, który wypożyczył egzemplarz. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false, updatable = false)
    private Reader reader;

    /** Moment wypożyczenia. */
    @Column(name = "borrowed_at", nullable = false, updatable = false)
    private Instant borrowedAt;

    /** Termin zwrotu: ostatni dzień, w którym zwrot nie jest opóźniony. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Moment zwrotu; {@code null}, dopóki egzemplarz nie wróci do biblioteki. */
    @Column(name = "returned_at")
    private Instant returnedAt;

    /** Liczba dotychczasowych przedłużeń terminu zwrotu. */
    @Column(nullable = false)
    private int renewals;

    /** Wersja do optymistycznego blokowania równoczesnej edycji. */
    @Version
    private long version;

    /** Konstruktor bez argumentów wymagany przez JPA; nie używać w kodzie aplikacji. */
    protected Loan() {
    }

    /**
     * Tworzy nowe, aktywne wypożyczenie.
     *
     * @param bookItem   wypożyczany egzemplarz
     * @param reader     czytelnik, musi być aktywny
     * @param borrowedAt moment wypożyczenia
     * @param dueDate    termin zwrotu, nie wcześniejszy niż dzień wypożyczenia (liczony w UTC)
     * @throws IllegalArgumentException jeśli któryś argument jest pusty, czytelnik jest nieaktywny
     *                                  albo termin zwrotu jest przed dniem wypożyczenia
     */
    public Loan(BookItem bookItem, Reader reader, Instant borrowedAt, LocalDate dueDate) {
        if (bookItem == null) {
            throw new IllegalArgumentException("Egzemplarz nie może być pusty");
        }
        if (reader == null) {
            throw new IllegalArgumentException("Czytelnik nie może być pusty");
        }
        if (!reader.isActive()) {
            throw new IllegalArgumentException("Nieaktywny czytelnik nie może wypożyczać książek");
        }
        if (borrowedAt == null) {
            throw new IllegalArgumentException("Data wypożyczenia nie może być pusta");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("Termin zwrotu nie może być pusty");
        }
        if (dueDate.isBefore(LocalDate.ofInstant(borrowedAt, ZoneOffset.UTC))) {
            throw new IllegalArgumentException("Termin zwrotu nie może być wcześniejszy niż dzień wypożyczenia");
        }
        this.bookItem = bookItem;
        this.reader = reader;
        // Baza przechowuje mikrosekundy, więc obcinamy do nich, żeby zapis i odczyt dawały ten sam moment.
        this.borrowedAt = borrowedAt.truncatedTo(ChronoUnit.MICROS);
        this.dueDate = dueDate;
    }

    /**
     * Zwraca identyfikator nadany przez bazę.
     *
     * @return identyfikator albo {@code null} przed pierwszym zapisem
     */
    public Long getId() {
        return id;
    }

    /**
     * Zwraca wypożyczony egzemplarz.
     *
     * @return egzemplarz
     */
    public BookItem getBookItem() {
        return bookItem;
    }

    /**
     * Zwraca czytelnika, który wypożyczył egzemplarz.
     *
     * @return czytelnik
     */
    public Reader getReader() {
        return reader;
    }

    /**
     * Zwraca moment wypożyczenia.
     *
     * @return data i godzina wypożyczenia
     */
    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    /**
     * Zwraca termin zwrotu.
     *
     * @return ostatni dzień, w którym zwrot nie jest opóźniony
     */
    public LocalDate getDueDate() {
        return dueDate;
    }

    /**
     * Zwraca moment zwrotu.
     *
     * @return data i godzina zwrotu albo {@code null}, jeśli egzemplarz nie wrócił
     */
    public Instant getReturnedAt() {
        return returnedAt;
    }

    /**
     * Zwraca liczbę przedłużeń terminu zwrotu.
     *
     * @return liczba przedłużeń
     */
    public int getRenewals() {
        return renewals;
    }

    /**
     * Zwraca wersję używaną do wykrywania równoczesnej edycji.
     *
     * @return numer wersji
     */
    public long getVersion() {
        return version;
    }

    /**
     * Sprawdza, czy egzemplarz został zwrócony.
     *
     * @return {@code true}, jeśli wypożyczenie ma datę zwrotu
     */
    public boolean isReturned() {
        return returnedAt != null;
    }

    /**
     * Sprawdza, czy wypożyczenie jest przeterminowane: niezwrócone i po terminie zwrotu.
     *
     * @param today dzisiejsza data
     * @return {@code true}, jeśli egzemplarz nie wrócił, a termin zwrotu już minął
     */
    public boolean isOverdue(LocalDate today) {
        return !isReturned() && today.isAfter(dueDate);
    }

    /**
     * Oznacza wypożyczenie jako zwrócone.
     *
     * @param returnedAt moment zwrotu, nie wcześniejszy niż moment wypożyczenia
     * @throws IllegalStateException    jeśli wypożyczenie zostało już zwrócone
     * @throws IllegalArgumentException jeśli moment zwrotu jest pusty lub wcześniejszy niż wypożyczenie
     */
    public void markReturned(Instant returnedAt) {
        if (isReturned()) {
            throw new IllegalStateException("Wypożyczenie zostało już zwrócone");
        }
        if (returnedAt == null) {
            throw new IllegalArgumentException("Data zwrotu nie może być pusta");
        }
        Instant truncated = returnedAt.truncatedTo(ChronoUnit.MICROS);
        if (truncated.isBefore(borrowedAt)) {
            throw new IllegalArgumentException("Data zwrotu nie może być wcześniejsza niż data wypożyczenia");
        }
        this.returnedAt = truncated;
    }

    /**
     * Przedłuża termin zwrotu. Dozwolone tylko dla niezwróconego wypożyczenia, najwyżej
     * {@value #MAX_RENEWALS} raz i tylko do dnia terminu włącznie (nie po terminie).
     *
     * @param today dzisiejsza data
     * @param days  o ile dni przedłużyć termin, co najmniej 1
     * @throws IllegalStateException    jeśli wypożyczenie jest zwrócone, było już przedłużone
     *                                  albo termin zwrotu minął
     * @throws IllegalArgumentException jeśli liczba dni jest mniejsza niż 1
     */
    public void renew(LocalDate today, int days) {
        if (isReturned()) {
            throw new IllegalStateException("Nie można przedłużyć zwróconego wypożyczenia");
        }
        if (renewals >= MAX_RENEWALS) {
            throw new IllegalStateException(
                    "Wypożyczenie zostało już przedłużone (limit przedłużeń: " + MAX_RENEWALS + ")");
        }
        if (today.isAfter(dueDate)) {
            throw new IllegalStateException("Nie można przedłużyć wypożyczenia po terminie zwrotu");
        }
        if (days < 1) {
            throw new IllegalArgumentException("Liczba dni przedłużenia musi wynosić co najmniej 1");
        }
        this.dueDate = dueDate.plusDays(days);
        this.renewals++;
    }

    /**
     * Porównuje wypożyczenia po identyfikatorze; dwa jeszcze niezapisane obiekty nigdy nie są równe.
     *
     * @param other porównywany obiekt
     * @return {@code true}, jeśli to to samo wypożyczenie
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Loan that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    /**
     * Zwraca stałą wartość zależną od klasy, żeby hash nie zmieniał się po nadaniu identyfikatora.
     *
     * @return wartość skrótu
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Zwraca opis wypożyczenia bez danych osobowych i bez dostępu do powiązanych obiektów
     * (które mogą być jeszcze niezaładowane).
     *
     * @return identyfikator, termin zwrotu i informacja o zwrocie
     */
    @Override
    public String toString() {
        return "Loan{id=" + id + ", dueDate=" + dueDate + ", returned=" + isReturned() + "}";
    }
}