package pl.tajko.library.api.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repozytorium kont pracowników biblioteki.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

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
}
