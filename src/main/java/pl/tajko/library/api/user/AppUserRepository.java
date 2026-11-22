package pl.tajko.library.api.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Repozytorium kont pracowników biblioteki.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    /**
     * Szuka użytkownika po nazwie (zapisanej małymi literami).
     *
     * @param username nazwa użytkownika w postaci kanonicznej
     * @return użytkownik albo pusty wynik
     */
    Optional<AppUser> findByUsername(String username);

    /**
     * Sprawdza, czy istnieje użytkownik o podanej nazwie.
     *
     * @param username nazwa użytkownika w postaci kanonicznej
     * @return {@code true}, jeśli nazwa jest zajęta
     */
    boolean existsByUsername(String username);

    /**
     * Zlicza aktywne konta z podaną rolą (np. żeby nie dało się zablokować ostatniego administratora).
     *
     * @param role rola
     * @return liczba aktywnych kont z tą rolą
     */
    long countByRoleAndEnabledTrue(Role role);

    /**
     * Zwraca aktywnych administratorów, blokując ich wiersze do końca transakcji (blokada zapisu). Służy
     * do ochrony ostatniego administratora: dwie równoczesne zmiany ustawiają się w kolejce, więc druga widzi
     * już wynik pierwszej. Kolejność po identyfikatorze zapobiega zakleszczeniom.
     *
     * @return aktywni administratorzy posortowani po identyfikatorze
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.role = pl.tajko.library.api.user.Role.ADMIN "
            + "and u.enabled = true order by u.id")
    List<AppUser> findEnabledAdminsForUpdate();
}
