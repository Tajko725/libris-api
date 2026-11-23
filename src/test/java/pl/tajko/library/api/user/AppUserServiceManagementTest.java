package pl.tajko.library.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.ResourceNotFoundException;

/**
 * Testy jednostkowe zarządzania kontami w {@link AppUserService}: odczyt, wyszukiwanie, zmiana roli i statusu
 * (z ochroną ostatniego administratora), reset hasła i zmiana własnego hasła.
 */
@ExtendWith(MockitoExtension.class)
class AppUserServiceManagementTest {

    private static final String PASSWORD = "Stare-haslo-1234";
    private static final String NEW_PASSWORD = "Nowe-haslo-56789";

    @Mock
    private AppUserRepository userRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AppUserService userService;

    /** Tworzy serwis z prawdziwym koderem haseł. */
    @BeforeEach
    void setUp() {
        userService = new AppUserService(userRepository, passwordEncoder);
    }

    private AppUser user(long id, String username, Role role, boolean enabled) {
        AppUser user = new AppUser(username, passwordEncoder.encode(PASSWORD), role);
        user.setEnabled(enabled);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ---- odczyt i wyszukiwanie ----

    /**
     * Istniejące konto jest zwracane po identyfikatorze.
     */
    @Test
    void shouldGetUserById() {
        AppUser user = user(1L, "jan.kowalski", Role.LIBRARIAN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.get(1L)).isSameAs(user);
    }

    /**
     * Nieistniejące konto kończy się wyjątkiem 404.
     */
    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    /**
     * Wyszukiwanie z filtrami przekazuje warunki i stronę do repozytorium.
     */
    @Test
    void shouldSearchWithFilters() {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by("username"));
        Page<AppUser> page = new PageImpl<>(List.of(user(1L, "jan.kowalski", Role.ADMIN, true)));
        when(userRepository.findAll(ArgumentMatchers.<Specification<AppUser>>any(), eq(pageable))).thenReturn(page);

        assertThat(userService.search("jan", Role.ADMIN, true, pageable)).isSameAs(page);
    }

    /**
     * Wyszukiwanie bez filtrów też działa (pusty tekst jest ignorowany).
     */
    @Test
    void shouldSearchWithoutFilters() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(ArgumentMatchers.<Specification<AppUser>>any(), eq(pageable)))
                .thenReturn(Page.empty());

        assertThat(userService.search("   ", null, null, pageable)).isEmpty();
    }

    /**
     * Sortowanie po niedozwolonym polu (np. po hashu hasła) jest odrzucane bez pytania bazy.
     */
    @Test
    void shouldRejectSortingByDisallowedProperty() {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThatThrownBy(() -> userService.search(null, null, null, pageable))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(userRepository);
    }

    // ---- zmiana roli i statusu ----

    /**
     * Bibliotekarz może zostać administratorem, a zmiana jest zapisana.
     */
    @Test
    void shouldChangeRoleAndStatus() {
        AppUser librarian = user(2L, "jan.bibliotekarz", Role.LIBRARIAN, true);
        when(userRepository.findEnabledAdminsForUpdate()).thenReturn(List.of(user(1L, "anna.admin", Role.ADMIN, true)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(librarian));
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppUser updated = userService.update(2L, Role.ADMIN, false, 0);

        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
        assertThat(updated.isEnabled()).isFalse();
        verify(userRepository).saveAndFlush(librarian);
    }

    /**
     * Nieistniejące konto daje 404 i nic nie jest zapisywane.
     */
    @Test
    void shouldThrowWhenUpdatingUnknownUser() {
        when(userRepository.findEnabledAdminsForUpdate()).thenReturn(List.of());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(99L, Role.ADMIN, true, 0))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    /**
     * Nieaktualna wersja daje konflikt z opisem wersji i nie zmienia konta.
     */
    @Test
    void shouldRejectStaleVersion() {
        AppUser librarian = user(2L, "jan.bibliotekarz", Role.LIBRARIAN, true);
        ReflectionTestUtils.setField(librarian, "version", 3L);
        when(userRepository.findEnabledAdminsForUpdate()).thenReturn(List.of());
        when(userRepository.findById(2L)).thenReturn(Optional.of(librarian));

        assertThatThrownBy(() -> userService.update(2L, Role.ADMIN, true, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("twoja wersja: 1")
                .hasMessageContaining("aktualna: 3");
        assertThat(librarian.getRole()).isEqualTo(Role.LIBRARIAN);
        verify(userRepository, never()).saveAndFlush(any());
    }

    /**
     * Ostatniego aktywnego administratora nie można zdegradować.
     */
    @Test
    void shouldNotDemoteLastActiveAdmin() {
        AppUser admin = user(1L, "anna.admin", Role.ADMIN, true);
        when(userRepository.findEnabledAdminsForUpdate()).thenReturn(List.of(admin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.update(1L, Role.LIBRARIAN, true, 0))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ostatniego aktywnego administratora");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).saveAndFlush(any());
    }

    /**
     * Ostatniego aktywnego administratora nie można wyłączyć.
     */
    @Test
    void shouldNotDisableLastActiveAdmin() {
        AppUser admin = user(1L, "anna.admin", Role.ADMIN, true);
        when(userRepository.findEnabledAdminsForUpdate()).thenReturn(List.of(admin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.update(1L, Role.ADMIN, false, 0))
                .isInstanceOf(ConflictException.class);
        assertThat(admin.isEnabled()).isTrue();
    }

    /**
     * Gdy jest drugi aktywny administrator, pierwszego można zdegradować (także samego siebie).
     */
    @Test
    void shouldDemoteAdminWhenAnotherActiveAdminExists() {
        AppUser first = user(1L, "anna.admin", Role.ADMIN, true);
        AppUser second = user(2L, "adam.admin", Role.ADMIN, true);
        when(userRepository.findEnabledAdminsForUpdate()).thenReturn(List.of(first, second));
        when(userRepository.findById(1L)).thenReturn(Optional.of(first));
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(userService.update(1L, Role.LIBRARIAN, true, 0).getRole()).isEqualTo(Role.LIBRARIAN);
    }

    /**
     * Zmiana, która zostawia administratora aktywnym (np. tylko ponowne zapisanie), jest dozwolona
     * także dla jedynego administratora.
     */
    @Test
    void shouldAllowUnchangedLastAdmin() {
        AppUser admin = user(1L, "anna.admin", Role.ADMIN, true);
        when(userRepository.findEnabledAdminsForUpdate()).thenReturn(List.of(admin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(userService.update(1L, Role.ADMIN, true, 0).getRole()).isEqualTo(Role.ADMIN);
    }

    /**
     * Wyłączony administrator nie jest "aktywnym", więc jego zmiana nie podlega ochronie ostatniego administratora.
     */
    @Test
    void shouldAllowChangingDisabledAdminEvenWhenOneActiveAdminRemains() {
        AppUser disabledAdmin = user(2L, "adam.admin", Role.ADMIN, false);
        when(userRepository.findEnabledAdminsForUpdate()).thenReturn(List.of(user(1L, "anna.admin", Role.ADMIN, true)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(disabledAdmin));
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(userService.update(2L, Role.LIBRARIAN, false, 0).getRole()).isEqualTo(Role.LIBRARIAN);
    }

    /**
     * Brak roli to niepoprawne żądanie (bez blokowania wierszy w bazie).
     */
    @Test
    void shouldRejectMissingRole() {
        assertThatThrownBy(() -> userService.update(1L, null, true, 0))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(userRepository);
    }

    // ---- reset hasła przez administratora ----

    /**
     * Reset zapisuje nowy hash, który pasuje do nowego hasła, a nie do starego.
     */
    @Test
    void shouldResetPassword() {
        AppUser user = user(2L, "jan.bibliotekarz", Role.LIBRARIAN, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        userService.resetPassword(2L, NEW_PASSWORD);

        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isFalse();
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}");
    }

    /**
     * Za słabe hasło jest odrzucane, zanim aplikacja zajrzy do bazy.
     */
    @Test
    void shouldRejectWeakPasswordOnReset() {
        assertThatThrownBy(() -> userService.resetPassword(2L, "krotkie"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> userService.resetPassword(2L, "ą".repeat(37)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(userRepository);
    }

    /**
     * Reset hasła nieistniejącego konta to 404.
     */
    @Test
    void shouldThrowWhenResettingPasswordOfUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resetPassword(99L, NEW_PASSWORD))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- zmiana własnego hasła ----

    /**
     * Po podaniu poprawnego aktualnego hasła nowe hasło zostaje zapisane.
     */
    @Test
    void shouldChangeOwnPassword() {
        AppUser user = user(2L, "jan.bibliotekarz", Role.LIBRARIAN, true);
        when(userRepository.findByUsername("jan.bibliotekarz")).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        userService.changeOwnPassword("jan.bibliotekarz", PASSWORD, NEW_PASSWORD);

        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isFalse();
    }

    /**
     * Błędne albo puste aktualne hasło jest odrzucane i hasło się nie zmienia.
     */
    @Test
    void shouldRejectWrongCurrentPassword() {
        AppUser user = user(2L, "jan.bibliotekarz", Role.LIBRARIAN, true);
        String hashBefore = user.getPasswordHash();
        when(userRepository.findByUsername("jan.bibliotekarz")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeOwnPassword("jan.bibliotekarz", "zle-haslo-zle-haslo", NEW_PASSWORD))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Aktualne hasło");
        assertThatThrownBy(() -> userService.changeOwnPassword("jan.bibliotekarz", null, NEW_PASSWORD))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(user.getPasswordHash()).isEqualTo(hashBefore);
        verify(userRepository, never()).saveAndFlush(any());
    }

    /**
     * Aktualne hasło dłuższe niż 72 bajty nie może być prawdziwym hasłem, więc jest odrzucane (bez błędu serwera).
     */
    @Test
    void shouldRejectOverlongCurrentPasswordGracefully() {
        AppUser user = user(2L, "jan.bibliotekarz", Role.LIBRARIAN, true);
        when(userRepository.findByUsername("jan.bibliotekarz")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeOwnPassword("jan.bibliotekarz", "x".repeat(100), NEW_PASSWORD))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Aktualne hasło");
    }

    /**
     * Nowe hasło takie samo jak obecne jest odrzucane.
     */
    @Test
    void shouldRejectNewPasswordEqualToCurrent() {
        AppUser user = user(2L, "jan.bibliotekarz", Role.LIBRARIAN, true);
        when(userRepository.findByUsername("jan.bibliotekarz")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeOwnPassword("jan.bibliotekarz", PASSWORD, PASSWORD))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("różnić");
        verify(userRepository, never()).saveAndFlush(any());
    }

    /**
     * Za słabe nowe hasło jest odrzucane przed zajrzeniem do bazy.
     */
    @Test
    void shouldRejectWeakNewPassword() {
        assertThatThrownBy(() -> userService.changeOwnPassword("jan.bibliotekarz", PASSWORD, "krotkie"))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(userRepository);
    }

    /**
     * Konto z tokenu, którego nie ma w bazie, daje 404.
     */
    @Test
    void shouldThrowWhenOwnAccountDoesNotExist() {
        when(userRepository.findByUsername("duch")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeOwnPassword("duch", PASSWORD, NEW_PASSWORD))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> userService.changeOwnPassword(null, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
