package pl.tajko.library.api.common;

import java.util.Locale;

/**
 * Pomocnicze metody do budowania wzorców wyszukiwania w wyrażeniach LIKE.
 */
public final class SearchPatterns {

    /** Znak ucieczki używany w wyrażeniach LIKE. */
    public static final char ESCAPE = '\\';

    /** Prywatny konstruktor: klasa zawiera tylko metody statyczne i nie powinna być tworzona. */
    private SearchPatterns() {
    }

    /**
     * Buduje wzorzec "zawiera tekst" bez rozróżniania wielkości liter: tekst jest przycinany,
     * zamieniany na małe litery, a jego znaki specjalne są traktowane dosłownie.
     *
     * @param text szukany fragment, niepusty
     * @return wzorzec do użycia z {@code LIKE ... ESCAPE} i porównaniem z małymi literami
     */
    public static String contains(String text) {
        return "%" + escape(text.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    /**
     * Zabezpiecza znaki specjalne wyrażenia LIKE, żeby wyszukiwanie "100%" szukało dosłownie "100%".
     *
     * @param text tekst użytkownika
     * @return tekst z poprzedzonymi znakami {@code \}, {@code %} i {@code _}
     */
    public static String escape(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}