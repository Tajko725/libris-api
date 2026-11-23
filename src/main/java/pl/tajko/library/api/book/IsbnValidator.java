package pl.tajko.library.api.book;

import java.util.Locale;

/**
 * Walidator i normalizator numerów ISBN (International Standard Book Number).
 * <p>
 * Obsługuje ISBN-10 (ostatni znak może być cyfrą lub literą X) oraz ISBN-13.
 * Poprawność sprawdzana jest na podstawie cyfry kontrolnej. Myślniki i spacje są ignorowane.
 * Numery ISBN-10 można zamienić na ISBN-13, dzięki czemu ta sama książka ma zawsze jeden zapis.
 */
public final class IsbnValidator {

    /** Długość numeru ISBN-10 (bez myślników). */
    private static final int ISBN_10_LENGTH = 10;

    /** Długość numeru ISBN-13 (bez myślników). */
    private static final int ISBN_13_LENGTH = 13;

    /** Liczba cyfr ISBN-13 poprzedzających cyfrę kontrolną. */
    private static final int ISBN_13_BODY_LENGTH = 12;

    /** Prefiks dopisywany do ISBN-10 przy zamianie na ISBN-13. */
    private static final String ISBN_13_PREFIX = "978";

    /** Prywatny konstruktor: klasa zawiera tylko metody statyczne i nie powinna być tworzona. */
    private IsbnValidator() {
    }

    /**
     * Normalizuje numer ISBN: usuwa spacje i myślniki oraz zamienia litery na wielkie.
     *
     * @param isbn numer ISBN w dowolnym zapisie, może być {@code null}
     * @return znormalizowany numer lub {@code null}, jeśli przekazano {@code null}
     */
    public static String normalize(String isbn) {
        if (isbn == null) {
            return null;
        }
        return isbn.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Sprawdza, czy podany numer jest poprawnym ISBN-10 lub ISBN-13.
     *
     * @param isbn numer ISBN w dowolnym zapisie, może być {@code null}
     * @return {@code true}, jeśli długość i cyfra kontrolna są poprawne
     */
    public static boolean isValid(String isbn) {
        String normalized = normalize(isbn);
        if (normalized == null) {
            return false;
        }
        return switch (normalized.length()) {
            case ISBN_10_LENGTH -> isValidIsbn10(normalized);
            case ISBN_13_LENGTH -> isValidIsbn13(normalized);
            default -> false;
        };
    }

    /**
     * Zwraca numer w postaci ISBN-13 bez myślników. ISBN-10 jest zamieniany na ISBN-13
     * (prefiks 978, pierwsze 9 cyfr oraz nowa cyfra kontrolna), a ISBN-13 jest tylko normalizowany.
     *
     * @param isbn poprawny numer ISBN-10 lub ISBN-13 w dowolnym zapisie
     * @return numer ISBN-13 złożony z 13 cyfr
     * @throws IllegalArgumentException jeśli numer jest niepoprawny lub {@code null}
     */
    public static String toIsbn13(String isbn) {
        if (!isValid(isbn)) {
            throw new IllegalArgumentException("Niepoprawny numer ISBN: " + isbn);
        }
        String normalized = normalize(isbn);
        if (normalized.length() == ISBN_13_LENGTH) {
            return normalized;
        }
        String body = ISBN_13_PREFIX + normalized.substring(0, ISBN_10_LENGTH - 1);
        return body + isbn13CheckDigit(body);
    }

    /**
     * Sprawdza cyfrę kontrolną ISBN-10 (suma ważona modulo 11).
     *
     * @param isbn znormalizowany numer o długości 10 znaków
     * @return {@code true}, jeśli numer jest poprawny
     */
    private static boolean isValidIsbn10(String isbn) {
        int sum = 0;
        for (int i = 0; i < ISBN_10_LENGTH - 1; i++) {
            char c = isbn.charAt(i);
            if (!isAsciiDigit(c)) {
                return false;
            }
            sum += (c - '0') * (ISBN_10_LENGTH - i);
        }
        char last = isbn.charAt(ISBN_10_LENGTH - 1);
        if (last == 'X') {
            sum += 10;
        } else if (isAsciiDigit(last)) {
            sum += last - '0';
        } else {
            return false;
        }
        return sum % 11 == 0;
    }

    /**
     * Sprawdza cyfrę kontrolną ISBN-13 (wagi 1 i 3 na przemian, modulo 10).
     *
     * @param isbn znormalizowany numer o długości 13 znaków
     * @return {@code true}, jeśli numer jest poprawny
     */
    private static boolean isValidIsbn13(String isbn) {
        for (int i = 0; i < ISBN_13_LENGTH; i++) {
            if (!isAsciiDigit(isbn.charAt(i))) {
                return false;
            }
        }
        String body = isbn.substring(0, ISBN_13_BODY_LENGTH);
        return isbn13CheckDigit(body) == isbn.charAt(ISBN_13_BODY_LENGTH) - '0';
    }

    /**
     * Oblicza cyfrę kontrolną ISBN-13 dla pierwszych 12 cyfr.
     *
     * @param body 12 cyfr numeru bez cyfry kontrolnej
     * @return cyfra kontrolna od 0 do 9
     */
    private static int isbn13CheckDigit(String body) {
        int sum = 0;
        for (int i = 0; i < ISBN_13_BODY_LENGTH; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - sum % 10) % 10;
    }

    /**
     * Sprawdza, czy znak jest cyfrą od 0 do 9 (bez cyfr z innych alfabetów).
     *
     * @param c sprawdzany znak
     * @return {@code true}, jeśli znak to cyfra ASCII
     */
    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}