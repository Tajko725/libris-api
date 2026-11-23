package pl.tajko.library.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;

/**
 * Testy jednostkowe {@link AppUserService} z zamockowanym repozytorium i prawdziwym koderem haseł.
 */
@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    private static final String PASSWORD = "Tajne-haslo-123";

    @Mock
    private AppUserRepository userRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AppUserService userService;

    /** Tworzy serwis z prawdziwym koderem, żeby sprawdzać faktyczny hash. */
    @BeforeEach
    void setUp() {
        userService = new AppUserService(userRepository, passwordEncoder);
    }

    // ---- poprawne tworzenie ----

    /**
     * Konto jest zapisywane z zahaszowanym hasłem (nie jawnym), małymi literami w nazwie i podaną rolą.
     */
    @Test
    void shouldCreateUserWithHashedPassword() {
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppUser created = userService.createUser("  Jan.Kowalski ", PASSWORD, Role.LIBRARIAN);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue()).isSameAs(created);
        assertThat(created.getUsername()).isEqualTo("jan.kowalski");
        assertThat(created.getRole()).isEqualTo(Role.LIBRARIAN);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
    }

    /**
     * Hasło o minimalnej długości (12 znaków) jest przyjmowane.
     */
    @Test
    void shouldAcceptPasswordOfMinimumLength() {
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(userService.createUser("jan.kowalski", "x".repeat(12), Role.ADMIN)).isNotNull();
    }

    /**
     * Hasło o maksymalnej długości (72 bajty) jest przyjmowane.
     */
    @Test
    void shouldAcceptPasswordOfMaximumLength() {
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(userService.createUser("jan.kowalski", "x".repeat(72), Role.ADMIN)).isNotNull();
    }

    // ---- odrzucenia ----

    /**
     * Zbyt krótkie albo puste hasło jest odrzucane, zanim zapytamy bazę, i nic nie jest zapisywane.
     */
    @Test
    void shouldRejectTooShortOrBlankPassword() {
        assertThatThrownBy(() -> userService.createUser("jan.kowalski", "x".repeat(11), Role.ADMIN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("12");
        assertThatThrownBy(() -> userService.createUser("jan.kowalski", "            ", Role.ADMIN))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> userService.createUser("jan.kowalski", null, Role.ADMIN))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(userRepository);
    }

    /**
     * Hasło dłuższe niż 72 bajty jest odrzucane, także gdy ma mało znaków, ale dużo bajtów (polskie litery).
     */
    @Test
    void shouldRejectPasswordLongerThan72Bytes() {
        assertThatThrownBy(() -> userService.createUser("jan.kowalski", "x".repeat(73), Role.ADMIN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("72");
        assertThatThrownBy(() -> userService.createUser("jan.kowalski", "ą".repeat(37), Role.ADMIN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("72");
        verifyNoInteractions(userRepository);
    }

    /**
     * Zajęta nazwa (także zapisana inaczej wielkością liter) daje konflikt i nic nie zapisuje.
     */
    @Test
    void shouldRejectTakenUsername() {
        when(userRepository.existsByUsername("jan.kowalski")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("Jan.Kowalski", PASSWORD, Role.LIBRARIAN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jan.kowalski");
        verify(userRepository, never()).saveAndFlush(any());
    }

    /**
     * Niepoprawna nazwa użytkownika albo pusta rola to błąd niepoprawnego żądania.
     */
    @Test
    void shouldRejectInvalidUsernameOrMissingRole() {
        assertThatThrownBy(() -> userService.createUser("ab", PASSWORD, Role.ADMIN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Nazwa użytkownika");
        assertThatThrownBy(() -> userService.createUser(null, PASSWORD, Role.ADMIN))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> userService.createUser("jan.kowalski", PASSWORD, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Rola");
        verify(userRepository, never()).saveAndFlush(any());
    }

    /**
     * Gdy dwa żądania tworzą tę samą nazwę naraz, przegrane (odrzucone przez bazę) dostaje konflikt, a nie błąd serwera.
     */
    @Test
    void shouldTurnDatabaseDuplicateIntoConflict() {
        when(userRepository.saveAndFlush(any(AppUser.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> userService.createUser("jan.kowalski", PASSWORD, Role.ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("już istnieje");
    }
}
