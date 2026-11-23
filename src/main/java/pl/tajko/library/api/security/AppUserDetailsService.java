package pl.tajko.library.api.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.user.AppUser;
import pl.tajko.library.api.user.AppUserRepository;

/**
 * Udostępnia Spring Security konta pracowników z tabeli {@code app_user}. Nazwa użytkownika jest
 * normalizowana tak samo jak przy tworzeniu konta (przycięcie i małe litery), a konto wyłączone
 * ({@code enabled = false}) nie może się zalogować.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    /**
     * Tworzy serwis.
     *
     * @param userRepository repozytorium użytkowników
     */
    public AppUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Wczytuje konto po nazwie użytkownika.
     *
     * @param username nazwa wpisana przy logowaniu
     * @return dane konta z hashem hasła i rolą
     * @throws UsernameNotFoundException gdy konto o takiej nazwie nie istnieje
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String normalized = AppUser.normalizeUsername(username);
        if (normalized == null) {
            throw new UsernameNotFoundException("Nie znaleziono użytkownika");
        }
        AppUser user = userRepository.findByUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Nie znaleziono użytkownika"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}
