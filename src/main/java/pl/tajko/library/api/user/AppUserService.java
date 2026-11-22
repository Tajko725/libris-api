package pl.tajko.library.api.user;

import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;

/**
 * Logika biznesowa kont pracowników: tworzenie konta z zahaszowanym hasłem i polityka haseł.
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
