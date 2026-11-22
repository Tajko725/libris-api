package pl.tajko.library.api.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;

/**
 * Testy jednostkowe {@link AdminBootstrap}: kiedy tworzy pierwszego administratora, a kiedy nie.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private AppUserService userService;

    private AdminBootstrap bootstrap(String username, String password) {
        return new AdminBootstrap(userRepository, userService, new AdminBootstrapProperties(username, password));
    }

    private static DefaultApplicationArguments noArgs() {
        return new DefaultApplicationArguments();
    }

    /**
     * Gdy nie ma użytkowników i podano hasło, powstaje administrator z nazwą i hasłem z konfiguracji.
     */
    @Test
    void shouldCreateAdminWhenNoUsersAndPasswordConfigured() {
        when(userRepository.count()).thenReturn(0L);
        when(userService.createUser("szef", "Tajne-haslo-123", Role.ADMIN))
                .thenReturn(new AppUser("szef", AppUserTest.VALID_HASH, Role.ADMIN));

        bootstrap("szef", "Tajne-haslo-123").run(noArgs());

        verify(userService).createUser("szef", "Tajne-haslo-123", Role.ADMIN);
    }

    /**
     * Gdy w bazie są już użytkownicy, konfiguracja jest ignorowana (nie powstaje drugi administrator
     * i nie zmienia się hasło istniejącego).
     */
    @Test
    void shouldDoNothingWhenUsersAlreadyExist() {
        when(userRepository.count()).thenReturn(1L);

        bootstrap("admin", "Tajne-haslo-123").run(noArgs());

        verifyNoInteractions(userService);
    }

    /**
     * Bez hasła w konfiguracji aplikacja startuje, ale nie tworzy konta (tylko ostrzega w logu).
     */
    @Test
    void shouldNotCreateAdminWithoutPassword() {
        when(userRepository.count()).thenReturn(0L);

        assertThatCode(() -> bootstrap("admin", null).run(noArgs())).doesNotThrowAnyException();
        assertThatCode(() -> bootstrap("admin", "   ").run(noArgs())).doesNotThrowAnyException();

        verify(userService, never()).createUser(any(), any(), any());
    }

    /**
     * Niepoprawne hasło z konfiguracji zatrzymuje start aplikacji, a komunikat nie zawiera hasła.
     */
    @Test
    void shouldFailStartupWhenConfiguredPasswordIsInvalid() {
        when(userRepository.count()).thenReturn(0L);
        when(userService.createUser("admin", "krotkie", Role.ADMIN))
                .thenThrow(new InvalidRequestException("Hasło musi mieć co najmniej 12 znaków"));

        assertThatThrownBy(() -> bootstrap("admin", "krotkie").run(noArgs()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12 znaków")
                .hasMessageNotContaining("krotkie");
    }

    /**
     * Gdy inna instancja aplikacji zdążyła utworzyć administratora, start przebiega normalnie.
     */
    @Test
    void shouldIgnoreAdminCreatedConcurrentlyByAnotherInstance() {
        when(userRepository.count()).thenReturn(0L);
        when(userService.createUser("admin", "Tajne-haslo-123", Role.ADMIN))
                .thenThrow(new ConflictException("już istnieje"))
                .thenThrow(new DataIntegrityViolationException("dup"));

        AdminBootstrap bootstrap = bootstrap("admin", "Tajne-haslo-123");

        assertThatCode(() -> bootstrap.run(noArgs())).doesNotThrowAnyException();
        assertThatCode(() -> bootstrap.run(noArgs())).doesNotThrowAnyException();
    }
}
