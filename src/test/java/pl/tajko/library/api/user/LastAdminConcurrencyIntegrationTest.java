package pl.tajko.library.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import pl.tajko.library.api.AbstractIntegrationTest;
import pl.tajko.library.api.common.ConflictException;

/**
 * Test równoczesnych zmian na prawdziwej bazie: dwóch administratorów jednocześnie odbiera sobie nawzajem
 * uprawnienia. Dzięki blokadzie wierszy dokładnie jedna zmiana przechodzi, a druga dostaje konflikt, więc
 * zawsze zostaje co najmniej jeden aktywny administrator.
 */
class LastAdminConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Tajne-haslo-123";

    @Autowired
    private AppUserService userService;

    @Autowired
    private AppUserRepository userRepository;

    private long firstId;
    private long secondId;

    /** Tworzy dwóch aktywnych administratorów (test nie działa w transakcji, bo musi mieć dwie niezależne). */
    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        firstId = userService.createUser("admin.pierwszy", PASSWORD, Role.ADMIN).getId();
        secondId = userService.createUser("admin.drugi", PASSWORD, Role.ADMIN).getId();
    }

    /** Sprząta po teście, bo baza jest wspólna dla klas testowych. */
    @AfterEach
    void cleanUp() {
        userRepository.deleteAllInBatch();
    }

    /**
     * Dwie równoczesne degradacje administratorów: jedna się udaje, druga kończy się konfliktem, a w bazie
     * zostaje dokładnie jeden aktywny administrator. Powtarzam test (przed każdym powtórzeniem wraca stan wyjściowy z @BeforeEach), żeby trafić w różne przeploty wątków.
     */
    @RepeatedTest(10)
    void shouldKeepExactlyOneAdminWhenTwoAdminsDemoteEachOtherConcurrently() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            results.add(executor.submit(demote(firstId, start)));
            results.add(executor.submit(demote(secondId, start)));
            start.countDown();

            int succeeded = 0;
            int conflicts = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                } else {
                    conflicts++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> demote(long id, CountDownLatch start) {
        return () -> {
            start.await();
            try {
                userService.update(id, Role.LIBRARIAN, true, 0);
                return true;
            } catch (ConflictException ex) {
                return false;
            }
        };
    }
}
