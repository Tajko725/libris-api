package pl.tajko.library.api.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import pl.tajko.library.api.user.AppUser;
import pl.tajko.library.api.user.AppUserRepository;

/**
 * Zamienia poprawnie podpisany token JWT na uwierzytelnienie, sprawdzając przy każdym żądaniu aktualny
 * stan konta w bazie: konto musi istnieć i być aktywne, a uprawnienia wynikają z <em>aktualnej</em> roli
 * z bazy, a nie z roli zapisanej w tokenie. Dzięki temu wyłączenie konta albo zmiana roli działa od razu,
 * a nie dopiero po wygaśnięciu tokenu. Koszt to jedno proste zapytanie po unikalnej nazwie na żądanie.
 */
@Component
public class DatabaseJwtAuthenticationConverter {

    private final AppUserRepository userRepository;

    /**
     * Tworzy konwerter.
     *
     * @param userRepository repozytorium kont
     */
    public DatabaseJwtAuthenticationConverter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Buduje uwierzytelnienie na podstawie tokenu i aktualnego konta.
     *
     * @param jwt zweryfikowany token (podpis, ważność i wystawca są już sprawdzone)
     * @return uwierzytelnienie z rolą z bazy
     * @throws InvalidBearerTokenException gdy konto z tokenu nie istnieje albo jest wyłączone
     */
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String username = jwt.getSubject();
        if (username == null || username.isBlank()) {
            throw new InvalidBearerTokenException("Token nie wskazuje użytkownika");
        }
        AppUser user = userRepository.findByUsername(username)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new InvalidBearerTokenException("Konto nie istnieje albo jest wyłączone"));
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
    }
}
