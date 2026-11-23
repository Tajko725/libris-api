package pl.tajko.library.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import pl.tajko.library.api.user.AppUser;
import pl.tajko.library.api.user.AppUserRepository;
import pl.tajko.library.api.user.Role;

/**
 * Testy jednostkowe {@link AppUserDetailsService} z zamockowanym repozytorium.
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    private static final String HASH = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("Tajne-haslo-123");

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService service;

    /**
     * Znalezione konto zwraca nazwę, hash hasła i rolę zapisaną jako uprawnienie z prefiksem ROLE_.
     */
    @Test
    void shouldLoadUserWithRoleAndPasswordHash() {
        when(userRepository.findByUsername("jan.kowalski"))
                .thenReturn(Optional.of(new AppUser("jan.kowalski", HASH, Role.LIBRARIAN)));

        UserDetails details = service.loadUserByUsername("jan.kowalski");

        assertThat(details.getUsername()).isEqualTo("jan.kowalski");
        assertThat(details.getPassword()).isEqualTo(HASH);
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_LIBRARIAN");
        assertThat(details.isEnabled()).isTrue();
    }

    /**
     * Administrator dostaje uprawnienie ROLE_ADMIN.
     */
    @Test
    void shouldMapAdminRole() {
        when(userRepository.findByUsername("szef"))
                .thenReturn(Optional.of(new AppUser("szef", HASH, Role.ADMIN)));

        assertThat(service.loadUserByUsername("szef").getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    /**
     * Konto wyłączone jest zgłaszane jako nieaktywne.
     */
    @Test
    void shouldReportDisabledAccount() {
        AppUser user = new AppUser("jan.kowalski", HASH, Role.LIBRARIAN);
        user.setEnabled(false);
        when(userRepository.findByUsername("jan.kowalski")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("jan.kowalski").isEnabled()).isFalse();
    }

    /**
     * Nazwa jest przycinana i zamieniana na małe litery przed szukaniem w bazie.
     */
    @Test
    void shouldNormalizeUsernameBeforeLookup() {
        when(userRepository.findByUsername("jan.kowalski"))
                .thenReturn(Optional.of(new AppUser("jan.kowalski", HASH, Role.LIBRARIAN)));

        service.loadUserByUsername("  Jan.Kowalski ");

        verify(userRepository).findByUsername("jan.kowalski");
    }

    /**
     * Nieistniejące konto kończy się wyjątkiem UsernameNotFoundException.
     */
    @Test
    void shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findByUsername("nikt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nikt")).isInstanceOf(UsernameNotFoundException.class);
    }

    /**
     * Pusta nazwa kończy się tym samym wyjątkiem i nie odpytuje bazy.
     */
    @Test
    void shouldThrowForNullUsernameWithoutQueryingDatabase() {
        assertThatThrownBy(() -> service.loadUserByUsername(null)).isInstanceOf(UsernameNotFoundException.class);

        verifyNoInteractions(userRepository);
    }
}
