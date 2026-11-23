package pl.tajko.library.api.book;

/**
 * Gatunek literacki książki.
 * <p>
 * Nazwy stałych są zapisywane w bazie jako tekst, a lista dozwolonych wartości
 * jest powtórzona w ograniczeniu {@code ck_book_genre} w migracji {@code V1}.
 * Test integracyjny pilnuje, żeby obie listy były zgodne.
 */
public enum Genre {

    /** Powieść. */
    NOVEL,

    /** Fantastyka. */
    FANTASY,

    /** Science fiction. */
    SCIENCE_FICTION,

    /** Kryminał i sensacja. */
    CRIME,

    /** Historia. */
    HISTORY,

    /** Biografia i autobiografia. */
    BIOGRAPHY,

    /** Literatura popularnonaukowa i naukowa. */
    SCIENCE,

    /** Literatura dziecięca i młodzieżowa. */
    CHILDREN,

    /** Pozostałe gatunki. */
    OTHER
}