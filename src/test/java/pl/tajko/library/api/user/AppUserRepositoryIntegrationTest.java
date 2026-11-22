package pl.tajko.library.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.AbstractIntegrationTest;

/**
 * Testy integracyjne repozytorium użytkowników z prawdziwą bazą PostgreSQL: mapowanie, zapytania,
 * unikalność nazwy, ograniczenia {@code CHECK} w bazie i wersjonowanie.
 */
@Transactional
class AppUserRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private AppUser newUser(String username, Role role) {
        return new AppUser(username, passwordEncoder.encode("Tajne-haslo-123"), role);
    }

    private void insertRaw(String username, String hash, String role) {
        jdbcTemplate.update("INSERT INTO app_user (username, password_hash, role) VALUES (?, ?, ?)",
                username, hash, role);
    }

    // ---- zapis i odczyt ----

    /**
     * Zapisane konto dostaje identyfikator i wersję, a po odczycie ma te same dane (rola jako tekst).
     */
    @Test
    void shouldSaveAndLoadUser() {
        AppUser saved = userRepository.saveAndFlush(newUser("jan.kowalski", Role.LIBRARIAN));
        entityManager.clear();

        AppUser loaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getUsername()).isEqualTo("jan.kowalski");
        assertThat(loaded.getRole()).isEqualTo(Role.LIBRARIAN);
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM app_user WHERE id = ?", String.class, saved.getId())).isEqualTo("LIBRARIAN");
    }

    // ---- zapytania ----

    /**
     * Szukanie po nazwie znajduje istniejące konto, a dla nieistniejącego zwraca pusty wynik.
     */
    @Test
    void shouldFindByUsername() {
        userRepository.saveAndFlush(newUser("jan.kowalski", Role.LIBRARIAN));

        assertThat(userRepository.findByUsername("jan.kowalski")).isPresent();
        assertThat(userRepository.findByUsername("ktos.inny")).isEmpty();
        assertThat(userRepository.existsByUsername("jan.kowalski")).isTrue();
        assertThat(userRepository.existsByUsername("ktos.inny")).isFalse();
    }

    /**
     * Liczenie aktywnych kont danej roli pomija konta wyłączone i konta innych ról.
     */
    @Test
    void shouldCountOnlyEnabledUsersOfGivenRole() {
        long adminsBefore = userRepository.countByRoleAndEnabledTrue(Role.ADMIN);
        userRepository.save(newUser("admin.jeden", Role.ADMIN));
        AppUser disabled = newUser("admin.dwa", Role.ADMIN);
        disabled.setEnabled(false);
        userRepository.save(disabled);
        userRepository.save(newUser("bibliotekarz", Role.LIBRARIAN));
        userRepository.flush();

        assertThat(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).isEqualTo(adminsBefore + 1);
        assertThat(userRepository.countByRoleAndEnabledTrue(Role.LIBRARIAN)).isGreaterThanOrEqualTo(1);
    }

    // ---- unikalność ----

    /**
     * Druga osoba o tej samej nazwie nie może zostać zapisana.
     */
    @Test
    void shouldRejectDuplicateUsername() {
        userRepository.saveAndFlush(newUser("jan.kowalski", Role.LIBRARIAN));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("jan.kowalski", Role.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Encja zapisuje nazwę małymi literami, więc "Jan.Kowalski" koliduje z "jan.kowalski".
     */
    @Test
    void shouldRejectDuplicateUsernameDifferingOnlyByCase() {
        userRepository.saveAndFlush(newUser("jan.kowalski", Role.LIBRARIAN));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("Jan.Kowalski", Role.LIBRARIAN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- ograniczenia CHECK w bazie (omijamy encję, wstawiając wiersze SQL-em) ----

    /**
     * Baza przyjmuje poprawny wiersz wstawiony wprost SQL-em (kontrola, że testy poniżej padają z właściwego powodu).
     */
    @Test
    void shouldAcceptValidRawRow() {
        insertRaw("ok.uzytkownik", passwordEncoder.encode("Tajne-haslo-123"), "ADMIN");

        assertThat(userRepository.existsByUsername("ok.uzytkownik")).isTrue();
    }

    /**
     * Baza odrzuca nazwy w złym formacie (wielkie litery, spacja, zbyt krótka, zbyt długa).
     */
    @ParameterizedTest
    @ValueSource(strings = {"Jan.Kowalski", "jan kowalski", "ab", "zażółć"})
    void shouldRejectInvalidUsernameInDatabase(String username) {
        String hash = passwordEncoder.encode("Tajne-haslo-123");

        assertThatThrownBy(() -> insertRaw(username, hash, "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Baza odrzuca nazwę dłuższą niż 50 znaków.
     */
    @Test
    void shouldRejectTooLongUsernameInDatabase() {
        String hash = passwordEncoder.encode("Tajne-haslo-123");

        assertThatThrownBy(() -> insertRaw("a".repeat(51), hash, "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Baza odrzuca nieznaną rolę.
     */
    @Test
    void shouldRejectUnknownRoleInDatabase() {
        String hash = passwordEncoder.encode("Tajne-haslo-123");

        assertThatThrownBy(() -> insertRaw("jan.kowalski", hash, "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Baza odrzuca hasło zapisane jawnym tekstem lub w niedozwolonym formacie ({noop}, goły bcrypt, śmieci).
     */
    @ParameterizedTest
    @ValueSource(strings = {"Tajne-haslo-123", "{noop}Tajne-haslo-123", "{bcrypt}nie-hash", "",
            "$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234"})
    void shouldRejectNonBcryptPasswordInDatabase(String hash) {
        assertThatThrownBy(() -> insertRaw("jan.kowalski", hash, "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- wersjonowanie ----

    /**
     * Zapis nieaktualnej wersji konta (zmienionego w międzyczasie) kończy się błędem blokady optymistycznej.
     */
    @Test
    void shouldDetectConcurrentModification() {
        AppUser saved = userRepository.saveAndFlush(newUser("jan.kowalski", Role.LIBRARIAN));
        entityManager.clear();

        // inny „użytkownik” zmienia wiersz i podnosi wersję
        jdbcTemplate.update("UPDATE app_user SET version = version + 1 WHERE id = ?", saved.getId());

        AppUser stale = userRepository.findById(saved.getId()).orElseThrow();
        jdbcTemplate.update("UPDATE app_user SET version = version + 1 WHERE id = ?", saved.getId());
        stale.setEnabled(false);

        assertThatThrownBy(() -> userRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    /**
     * Zmiana roli podnosi wersję o jeden, a nazwy użytkownika nie da się zmienić (kolumna niezmienialna).
     */
    @Test
    void shouldIncrementVersionOnUpdateAndKeepUsername() {
        AppUser saved = userRepository.saveAndFlush(newUser("jan.kowalski", Role.LIBRARIAN));
        long versionBefore = jdbcTemplate.queryForObject(
                "SELECT version FROM app_user WHERE id = ?", Long.class, saved.getId());

        saved.setRole(Role.ADMIN);
        userRepository.saveAndFlush(saved);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM app_user WHERE id = ?", Long.class, saved.getId()))
                .isEqualTo(versionBefore + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT username FROM app_user WHERE id = ?", String.class, saved.getId()))
                .isEqualTo("jan.kowalski");
    }
}
