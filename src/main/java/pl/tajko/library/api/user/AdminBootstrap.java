package pl.tajko.library.api.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;

/**
 * Tworzy pierwszego administratora przy starcie aplikacji, ale tylko wtedy, gdy w bazie nie ma jeszcze
 * żadnego użytkownika i w konfiguracji podano hasło. Dzięki temu w repozytorium nie ma żadnego hasła,
 * a po pierwszym uruchomieniu da się zalogować i założyć kolejne konta. Jeśli użytkownicy już istnieją,
 * konfiguracja jest ignorowana, więc zmiana zmiennej środowiskowej nie zmieni hasła istniejącego konta.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository userRepository;
    private final AppUserService userService;
    private final AdminBootstrapProperties properties;

    /**
     * Tworzy komponent z potrzebnymi zależnościami.
     *
     * @param userRepository repozytorium kont
     * @param userService    serwis kont
     * @param properties     dane pierwszego administratora z konfiguracji
     */
    public AdminBootstrap(AppUserRepository userRepository, AppUserService userService,
                          AdminBootstrapProperties properties) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.properties = properties;
    }

    /**
     * Tworzy pierwszego administratora, jeśli to potrzebne.
     *
     * @param args argumenty uruchomienia aplikacji (nieużywane)
     * @throws IllegalStateException jeśli hasło lub nazwa administratora z konfiguracji są niepoprawne
     *                               (aplikacja nie uruchomi się, zamiast działać bez możliwości zalogowania)
     */
    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (!properties.hasPassword()) {
            LOG.warn("W bazie nie ma żadnego użytkownika i nie ustawiono libris.security.admin.password "
                    + "(zmienna LIBRIS_ADMIN_PASSWORD), więc nikt nie będzie mógł się zalogować");
            return;
        }
        try {
            AppUser admin = userService.createUser(properties.username(), properties.password(), Role.ADMIN);
            LOG.info("Utworzono pierwszego administratora: {}", admin.getUsername());
        } catch (InvalidRequestException ex) {
            // Komunikat nie zawiera hasła. Przerywamy start, bo to błąd konfiguracji do poprawienia.
            throw new IllegalStateException("Niepoprawna konfiguracja pierwszego administratora: "
                    + ex.getMessage(), ex);
        } catch (ConflictException | DataIntegrityViolationException ex) {
            LOG.info("Pierwszy administrator został w międzyczasie utworzony przez inną instancję aplikacji");
        }
    }
}
