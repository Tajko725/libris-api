package pl.tajko.library.api.user;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.ResourceNotFoundException;
import pl.tajko.library.api.common.SortValidator;

/**
 * Logika biznesowa kont pracowników: tworzenie, wyszukiwanie, zmiana roli i statusu, zmiana hasła oraz polityka haseł.
 * Hasło jawnym tekstem istnieje tylko w pamięci, w trakcie tej metody, i nigdy nie jest zapisywane ani logowane.
 */
@Service
public class AppUserService {

    /** Minimalna długość hasła (w znakach). */
    public static final int MIN_PASSWORD_LENGTH = 12;

    /**
     * Maksymalna długość hasła w bajtach UTF-8. Bcrypt uwzględnia tylko pierwsze 72 bajty, więc dłuższe hasło
     * dawałoby złudne poczucie bezpieczeństwa (końcówka nie miałaby znaczenia).
     */
    public static final int MAX_PASSWORD_BYTES = 72;

    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "username", "role", "enabled", "createdAt");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Tworzy serwis z potrzebnymi zależnościami.
     *
     * @param userRepository  repozytorium kont
     * @param passwordEncoder koder haseł
     */
    public AppUserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Tworzy aktywne konto. Hasło jest haszowane przed zapisem, a nazwa użytkownika zapisywana małymi literami.
     *
     * @param username    nazwa użytkownika (3-50 znaków: a-z, 0-9, . _ -), wielkość liter nie ma znaczenia
     * @param rawPassword hasło jawnym tekstem, 12 znaków lub więcej i najwyżej 72 bajty
     * @param role        rola użytkownika
     * @return zapisane konto
     * @throws InvalidRequestException jeśli hasło, nazwa użytkownika lub rola są niepoprawne
     * @throws ConflictException       jeśli nazwa użytkownika jest już zajęta
     */
    @Transactional
    public AppUser createUser(String username, String rawPassword, Role role) {
        requireAcceptablePassword(rawPassword);
        String normalized = AppUser.normalizeUsername(username);
        if (normalized != null && userRepository.existsByUsername(normalized)) {
            throw usernameTaken(normalized);
        }

        AppUser user;
        try {
            user = new AppUser(normalized, passwordEncoder.encode(rawPassword), role);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(ex.getMessage());
        }
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Ktoś utworzył konto o tej samej nazwie w tej samej chwili; złapało to ograniczenie unikalności w bazie.
            throw usernameTaken(normalized);
        }
    }

    /**
     * Zwraca konto o podanym identyfikatorze.
     *
     * @param id identyfikator konta
     * @return konto
     * @throws ResourceNotFoundException jeśli konto nie istnieje
     */
    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return findOrThrow(id);
    }

    /**
     * Zwraca stronę kont z opcjonalnym wyszukiwaniem po nazwie oraz filtrami roli i statusu.
     *
     * @param search   fragment nazwy użytkownika, może być {@code null}
     * @param role     filtr roli, może być {@code null}
     * @param enabled  filtr statusu konta, może być {@code null}
     * @param pageable strona i sortowanie
     * @return strona kont
     * @throws InvalidRequestException jeśli sortowanie dotyczy niedozwolonego pola
     */
    @Transactional(readOnly = true)
    public Page<AppUser> search(String search, Role role, Boolean enabled, Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Specification<AppUser> specification = Specification.unrestricted();
        if (role != null) {
            specification = specification.and(UserSpecifications.hasRole(role));
        }
        if (enabled != null) {
            specification = specification.and(UserSpecifications.hasEnabled(enabled));
        }
        if (search != null && !search.isBlank()) {
            specification = specification.and(UserSpecifications.usernameContains(search));
        }
        return userRepository.findAll(specification, pageable);
    }

    /**
     * Zmienia rolę i status konta. Zmiana jest przyjmowana tylko wtedy, gdy klient zmienia aktualną wersję.
     * Nie wolno zdegradować ani wyłączyć ostatniego aktywnego administratora. Wiersze aktywnych
     * administratorów są blokowane na czas zmiany, więc dwóch administratorów nie może jednocześnie
     * pozbawić się uprawnień nawzajem.
     *
     * @param id      identyfikator konta
     * @param role    nowa rola
     * @param enabled nowy status konta
     * @param version wersja konta, którą klient wcześniej pobrał
     * @return zaktualizowane konto
     * @throws ResourceNotFoundException jeśli konto nie istnieje
     * @throws ConflictException         jeśli wersja jest nieaktualna albo zmiana usunęłaby ostatniego administratora
     */
    @Transactional
    public AppUser update(Long id, Role role, boolean enabled, long version) {
        if (role == null) {
            throw new InvalidRequestException("Rola nie może być pusta");
        }
        // Blokadę zawsze zakładam jako pierwszą i w tej samej kolejności, żeby transakcje nie zakleszczały się.
        List<AppUser> activeAdmins = userRepository.findEnabledAdminsForUpdate();
        AppUser user = findOrThrow(id);
        if (user.getVersion() != version) {
            throw new ConflictException("Konto zostało w międzyczasie zmienione przez kogoś innego (twoja wersja: "
                    + version + ", aktualna: " + user.getVersion() + "). Pobierz je ponownie i spróbuj jeszcze raz");
        }

        boolean isActiveAdmin = user.getRole() == Role.ADMIN && user.isEnabled();
        boolean staysActiveAdmin = role == Role.ADMIN && enabled;
        if (isActiveAdmin && !staysActiveAdmin && activeAdmins.size() <= 1) {
            throw new ConflictException("Nie można zmienić roli ani wyłączyć ostatniego aktywnego administratora. "
                    + "Najpierw ustanów innego administratora");
        }

        user.setRole(role);
        user.setEnabled(enabled);
        return userRepository.saveAndFlush(user);
    }

    /**
     * Ustawia nowe hasło konta bez znajomości starego (reset przez administratora).
     *
     * @param id          identyfikator konta
     * @param newPassword nowe hasło jawnym tekstem
     * @throws ResourceNotFoundException jeśli konto nie istnieje
     * @throws InvalidRequestException   jeśli hasło nie spełnia zasad
     */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        requireAcceptablePassword(newPassword);
        AppUser user = findOrThrow(id);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.saveAndFlush(user);
    }

    /**
     * Zmienia własne hasło zalogowanego użytkownika po sprawdzeniu aktualnego hasła.
     *
     * @param username        nazwa użytkownika z tokenu
     * @param currentPassword aktualne hasło
     * @param newPassword     nowe hasło
     * @throws ResourceNotFoundException jeśli konto nie istnieje
     * @throws InvalidRequestException   jeśli aktualne hasło jest błędne, nowe nie spełnia zasad albo jest takie samo
     */
    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        requireAcceptablePassword(newPassword);
        String normalized = AppUser.normalizeUsername(username);
        if (normalized == null) {
            throw new ResourceNotFoundException("Nie znaleziono konta zalogowanego użytkownika");
        }
        AppUser user = userRepository.findByUsername(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono konta zalogowanego użytkownika"));

        // Hasło dłuższe niż 72 bajty nie mogło zostać ustawione, więc na pewno nie jest aktualnym hasłem.
        boolean currentMatches = currentPassword != null
                && currentPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES
                && passwordEncoder.matches(currentPassword, user.getPasswordHash());
        if (!currentMatches) {
            throw new InvalidRequestException("Aktualne hasło jest niepoprawne");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new InvalidRequestException("Nowe hasło musi różnić się od obecnego");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.saveAndFlush(user);
    }

    private AppUser findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono użytkownika o id " + id));
    }

    private static void requireAcceptablePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidRequestException("Hasło nie może być puste");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRequestException("Hasło musi mieć co najmniej " + MIN_PASSWORD_LENGTH + " znaków");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidRequestException("Hasło jest za długie: może mieć najwyżej " + MAX_PASSWORD_BYTES
                    + " bajtów (polskie litery zajmują po 2 bajty)");
        }
    }

    private static ConflictException usernameTaken(String username) {
        return new ConflictException("Użytkownik o nazwie " + username + " już istnieje");
    }
}
