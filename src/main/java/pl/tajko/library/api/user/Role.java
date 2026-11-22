package pl.tajko.library.api.user;

/**
 * Rola pracownika biblioteki. Zapisywana w bazie jako tekst (nazwa stałej), więc kolejności stałych
 * nie trzeba pilnować.
 */
public enum Role {

    /** Obsługa biblioteki: odczyt, dodawanie i zmiany danych oraz wypożyczenia, ale bez usuwania danych. */
    LIBRARIAN,

    /** Administrator: wszystko, co bibliotekarz, a ponadto usuwanie danych i zarządzanie użytkownikami. */
    ADMIN
}
