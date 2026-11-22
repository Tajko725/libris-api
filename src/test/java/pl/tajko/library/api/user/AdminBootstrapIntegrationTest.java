package pl.tajko.library.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.tajko.library.api.AbstractIntegrationTest;

/**
 * Test integracyjny tworzenia pierwszego administratora na prawdziwej bazie: konfiguracja z właściwości
 * (w aplikacji pochodzi ze zmiennych środowiskowych), hasło w bazie tylko jako hash, powtórne uruchomienie nic nie zmienia.
 */
@SpringBootTest(properties = {
        "libris.security.admin.username=szef.biblioteki",
        "libris.security.admin.password=Haslo-startowe-123"
})
class AdminBootstrapIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Czyści tabelę przed testem (kontekst współdzielony z innymi klasami, a tabela nie jest czyszczona automatycznie). */
    @BeforeEach
    @AfterEach
    void cleanUsers() {
        userRepository.deleteAllInBatch();
    }

    /**
     * Przy pustej tabeli powstaje aktywny administrator, a hasło jest zapisane jako hash bcrypt, nie jawnie.
     */
    @Test
    void shouldCreateAdminFromConfiguration() throws Exception {
        adminBootstrap.run(new DefaultApplicationArguments());

        AppUser admin = userRepository.findByUsername("szef.biblioteki").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("Haslo-startowe-123");
        assertThat(passwordEncoder.matches("Haslo-startowe-123", admin.getPasswordHash())).isTrue();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    /**
     * Powtórne uruchomienie nie tworzy drugiego konta i nie zmienia istniejącego hasła.
     */
    @Test
    void shouldBeIdempotent() throws Exception {
        adminBootstrap.run(new DefaultApplicationArguments());
        String hashAfterFirstRun = userRepository.findByUsername("szef.biblioteki").orElseThrow().getPasswordHash();

        adminBootstrap.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByUsername("szef.biblioteki").orElseThrow().getPasswordHash())
                .isEqualTo(hashAfterFirstRun);
    }

    /**
     * Gdy w bazie jest już jakikolwiek użytkownik, administrator z konfiguracji nie jest dodawany.
     */
    @Test
    void shouldNotCreateAdminWhenAnyUserExists() throws Exception {
        userRepository.saveAndFlush(new AppUser("bibliotekarz", passwordEncoder.encode("Tajne-haslo-123"), Role.LIBRARIAN));

        adminBootstrap.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.existsByUsername("szef.biblioteki")).isFalse();
    }
}
