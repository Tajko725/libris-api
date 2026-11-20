package pl.tajko.library.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Klasa bazowa testów integracyjnych: uruchamia kontekst Springa z prawdziwym
 * PostgreSQL w kontenerze (Testcontainers). Migracje Flyway wykonują się przy starcie kontekstu.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}