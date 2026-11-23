package pl.tajko.library.api.common;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Sort;

/**
 * Sprawdzanie, czy lista jest sortowana tylko po dozwolonych polach. Bez tego klient mógłby podać nazwę
 * dowolnego pola encji (np. wersji) albo nieistniejącą, co kończy się błędem bazy zamiast jasnego komunikatu 400.
 */
public final class SortValidator {

    /** Prywatny konstruktor: klasa zawiera tylko metody statyczne i nie powinna być tworzona. */
    private SortValidator() {
    }

    /**
     * Odrzuca sortowanie po polu spoza dozwolonej listy.
     *
     * @param sort              żądane sortowanie
     * @param allowedProperties dozwolone nazwy pól encji
     * @throws InvalidRequestException jeśli sortowanie dotyczy niedozwolonego pola
     */
    public static void requireAllowed(Sort sort, Set<String> allowedProperties) {
        for (Sort.Order order : sort) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new InvalidRequestException("Niedozwolone pole sortowania: " + order.getProperty()
                        + ". Dozwolone: " + new TreeSet<>(allowedProperties));
            }
        }
    }
}
