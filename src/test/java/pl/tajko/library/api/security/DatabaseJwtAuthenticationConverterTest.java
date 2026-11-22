package pl.tajko.library.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import pl.tajko.library.api.user.AppUser;
import pl.tajko.library.api.user.AppUserRepository;
import pl.tajko.library.api.user.Role;

/**
 * Testy jednostkowe {@link DatabaseJwtAuthenticationConverter}: uprawnienia pochodzą z aktualnego konta w bazie,
 * a nie z roli zapisanej w tokenie.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseJwtAuthenticationConverterTest {

    private static final String HASH = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("Tajne-haslo-123");

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private DatabaseJwtAuthenticationConverter converter;

    private static Jwt token(String subject, String... rolesInToken) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .issuedAt(Instant.parse("2026-11-22T10:00:00Z"))
                .expiresAt(Instant.parse("2026-11-22T11:00:00Z"))
                .claim("roles", List.of(rolesInToken));
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }

    /**
     * Aktywne konto daje uwierzytelnienie z nazwą z bazy i rolą w postaci uprawnienia ROLE_*.
     */
    @Test
    void shouldAuthenticateEnabledUserWithRoleFromDatabase() {
        when(userRepository.findByUsername("jan.kowalski"))
                .thenReturn(Optional.of(new AppUser("jan.kowalski", HASH, Role.LIBRARIAN)));

        AbstractAuthenticationToken authentication = converter.convert(token("jan.kowalski", "LIBRARIAN"));

        assertThat(authentication.getName()).isEqualTo("jan.kowalski");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_LIBRARIAN");
    }

    /**
     * Rola z tokenu jest ignorowana: token z rolą ADMIN dla konta, które w bazie jest bibliotekarzem,
     * daje tylko uprawnienia bibliotekarza.
     */
    @Test
    void shouldIgnoreRoleClaimFromToken() {
        when(userRepository.findByUsername("jan.kowalski"))
                .thenReturn(Optional.of(new AppUser("jan.kowalski", HASH, Role.LIBRARIAN)));

        AbstractAuthenticationToken authentication = converter.convert(token("jan.kowalski", "ADMIN"));

        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_LIBRARIAN");
    }

    /**
     * Awans w bazie działa od razu, nawet jeśli token został wydany, gdy konto było jeszcze bibliotekarzem.
     */
    @Test
    void shouldReflectPromotionImmediately() {
        when(userRepository.findByUsername("jan.kowalski"))
                .thenReturn(Optional.of(new AppUser("jan.kowalski", HASH, Role.ADMIN)));

        assertThat(converter.convert(token("jan.kowalski", "LIBRARIAN")).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    /**
     * Konto wyłączone po wydaniu tokenu powoduje odrzucenie tokenu.
     */
    @Test
    void shouldRejectTokenOfDisabledUser() {
        AppUser user = new AppUser("jan.kowalski", HASH, Role.LIBRARIAN);
        user.setEnabled(false);
        when(userRepository.findByUsername("jan.kowalski")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> converter.convert(token("jan.kowalski", "LIBRARIAN")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    /**
     * Token konta, które nie istnieje (np. zostało usunięte z bazy), jest odrzucany.
     */
    @Test
    void shouldRejectTokenOfUnknownUser() {
        when(userRepository.findByUsername("nikt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(token("nikt", "ADMIN")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    /**
     * Token bez wskazanego użytkownika jest odrzucany bez pytania bazy.
     */
    @Test
    void shouldRejectTokenWithoutSubject() {
        assertThatThrownBy(() -> converter.convert(token(null, "ADMIN")))
                .isInstanceOf(InvalidBearerTokenException.class);

        verifyNoInteractions(userRepository);
    }
}
