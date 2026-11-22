package pl.tajko.library.api.user;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pl.tajko.library.api.AbstractIntegrationTest;

/**
 * Testy integracyjne zarządzania kontami: żądania HTTP z prawdziwymi tokenami do pełnego kontekstu z prawdziwą
 * bazą. Sprawdzają też, że zmiany kont działają od razu, mimo już wydanych tokenów.
 */
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Tajne-haslo-123";
    private static final String NEW_PASSWORD = "Nowe-haslo-56789";
    private static final String USERS = "/api/v1/users";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserService userService;

    @Autowired
    private AppUserRepository userRepository;

    private MockMvc mockMvc;

    /** Tworzy MockMvc z filtrami zabezpieczeń oraz dwa konta: administratora i bibliotekarza. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.deleteAllInBatch();
        userService.createUser("anna.admin", PASSWORD, Role.ADMIN);
        userService.createUser("jan.bibliotekarz", PASSWORD, Role.LIBRARIAN);
    }

    /** Sprząta po teście, bo baza jest wspólna dla klas testowych. */
    @AfterEach
    void cleanUp() {
        userRepository.deleteAllInBatch();
    }

    // ---- pomocnicze ----

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private ResultActions tryLogin(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(username, password)));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = tryLogin(username, password).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String adminToken() throws Exception {
        return login("anna.admin", PASSWORD);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long idOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    private long versionOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getVersion();
    }

    private static String updateBody(String role, boolean enabled, long version) {
        return "{\"role\":\"" + role + "\",\"enabled\":" + enabled + ",\"version\":" + version + "}";
    }

    private ResultActions updateUser(String token, String username, String role, boolean enabled) throws Exception {
        return mockMvc.perform(put(USERS + "/" + idOf(username)).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(role, enabled, versionOf(username))));
    }

    // ---- tworzenie ----

    /**
     * Administrator tworzy konto: odpowiedź 201 z adresem konta, bez hasła, a nowy użytkownik może się zalogować.
     */
    @Test
    void shouldCreateUserAndLetThemLogin() throws Exception {
        mockMvc.perform(post(USERS).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Nowa.Osoba\",\"password\":\"" + PASSWORD + "\",\"role\":\"LIBRARIAN\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(USERS + "/" + idOf("nowa.osoba"))))
                .andExpect(jsonPath("$.username").value("nowa.osoba"))
                .andExpect(jsonPath("$.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        tryLogin("nowa.osoba", PASSWORD).andExpect(status().isOk());
    }

    /**
     * Zajęta nazwa (także zapisana wielkimi literami) to 409.
     */
    @Test
    void shouldRejectDuplicateUsername() throws Exception {
        mockMvc.perform(post(USERS).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"JAN.BIBLIOTEKARZ\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
    }

    /**
     * Niepoprawna nazwa, za krótkie hasło, brak roli i nieznana rola to 400.
     */
    @Test
    void shouldRejectInvalidCreateRequests() throws Exception {
        String token = adminToken();
        String[] bodies = {
                "{\"username\":\"a b\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}",
                "{\"username\":\"ok.nazwa\",\"password\":\"krotkie\",\"role\":\"ADMIN\"}",
                "{\"username\":\"ok.nazwa\",\"password\":\"" + PASSWORD + "\"}",
                "{\"username\":\"ok.nazwa\",\"password\":\"" + PASSWORD + "\",\"role\":\"SUPERUSER\"}",
                "{\"username\":\"\",\"password\":\"\",\"role\":null}"
        };
        for (String body : bodies) {
            mockMvc.perform(post(USERS).header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(USERS).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ---- dostęp ----

    /**
     * Bibliotekarz dostaje 403 na wszystkie operacje na kontach, a niezalogowany 401.
     */
    @Test
    void shouldRestrictUserManagementToAdmin() throws Exception {
        String librarian = login("jan.bibliotekarz", PASSWORD);
        long id = idOf("anna.admin");

        mockMvc.perform(get(USERS).header("Authorization", bearer(librarian))).andExpect(status().isForbidden());
        mockMvc.perform(get(USERS + "/" + id).header("Authorization", bearer(librarian)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(USERS + "/" + id).header("Authorization", bearer(librarian))
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("LIBRARIAN", true, 0)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(USERS + "/" + id + "/password").header("Authorization", bearer(librarian))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
    }

    /**
     * Kont nie da się usunąć przez API (tylko wyłączyć): DELETE to 405.
     */
    @Test
    void shouldNotOfferDeletingUsers() throws Exception {
        mockMvc.perform(delete(USERS + "/" + idOf("jan.bibliotekarz")).header("Authorization", bearer(adminToken())))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---- odczyt i lista ----

    /**
     * Konto można pobrać po identyfikatorze; nieznane id to 404, a id niebędące liczbą to 400.
     */
    @Test
    void shouldGetUserById() throws Exception {
        String token = adminToken();

        mockMvc.perform(get(USERS + "/" + idOf("jan.bibliotekarz")).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jan.bibliotekarz"))
                .andExpect(jsonPath("$.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(get(USERS + "/999999").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(USERS + "/abc").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Lista obsługuje stronicowanie, wyszukiwanie po nazwie oraz filtry roli i statusu.
     */
    @Test
    void shouldListAndFilterUsers() throws Exception {
        String token = adminToken();
        userService.createUser("zofia.druga", PASSWORD, Role.LIBRARIAN);
        updateUser(token, "zofia.druga", "LIBRARIAN", false).andExpect(status().isOk());

        mockMvc.perform(get(USERS).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].username").value("anna.admin"))
                .andExpect(jsonPath("$.content[2].username").value("zofia.druga"));
        mockMvc.perform(get(USERS).param("sort", "username,desc").param("size", "1").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("zofia.druga"))
                .andExpect(jsonPath("$.totalPages").value(3));
        mockMvc.perform(get(USERS).param("search", "BIBLIO").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("jan.bibliotekarz"));
        mockMvc.perform(get(USERS).param("role", "ADMIN").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(USERS).param("enabled", "false").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("zofia.druga"));
        mockMvc.perform(get(USERS).param("search", "%").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * Sortowanie po polu spoza listy dozwolonych (np. po hashu hasła) i nieznana rola w filtrze to 400.
     */
    @Test
    void shouldRejectInvalidListParameters() throws Exception {
        String token = adminToken();

        mockMvc.perform(get(USERS).param("sort", "passwordHash").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(USERS).param("role", "SUPERUSER").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    // ---- zmiana roli i statusu ----

    /**
     * Zmiana roli i statusu podnosi wersję, a odpowiedź pokazuje nowe wartości.
     */
    @Test
    void shouldUpdateRoleAndStatus() throws Exception {
        updateUser(adminToken(), "jan.bibliotekarz", "ADMIN", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.version").value(1));
    }

    /**
     * Nieaktualna wersja to 409, nieznane konto 404, a brak wersji lub roli to 400.
     */
    @Test
    void shouldRejectInvalidUpdates() throws Exception {
        String token = adminToken();
        long id = idOf("jan.bibliotekarz");

        mockMvc.perform(put(USERS + "/" + id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("ADMIN", true, 7)))
                .andExpect(status().isConflict());
        mockMvc.perform(put(USERS + "/999999").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("ADMIN", true, 0)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(USERS + "/" + id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"enabled\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(USERS + "/" + id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"version\":0}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(userRepository.findByUsername("jan.bibliotekarz").orElseThrow().getRole())
                .isEqualTo(Role.LIBRARIAN);
    }

    // ---- ochrona ostatniego administratora ----

    /**
     * Jedyny administrator nie może zdegradować ani wyłączyć samego siebie (409), a konto zostaje bez zmian.
     */
    @Test
    void shouldProtectLastAdmin() throws Exception {
        String token = adminToken();

        updateUser(token, "anna.admin", "LIBRARIAN", true).andExpect(status().isConflict());
        updateUser(token, "anna.admin", "ADMIN", false).andExpect(status().isConflict());

        AppUser admin = userRepository.findByUsername("anna.admin").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        org.assertj.core.api.Assertions.assertThat(admin.isEnabled()).isTrue();
    }

    /**
     * Gdy jest drugi aktywny administrator, pierwszy może zdegradować samego siebie (4a).
     */
    @Test
    void shouldLetAdminDemoteSelfWhenAnotherAdminExists() throws Exception {
        String token = adminToken();
        updateUser(token, "jan.bibliotekarz", "ADMIN", true).andExpect(status().isOk());

        updateUser(token, "anna.admin", "LIBRARIAN", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LIBRARIAN"));
    }

    // ---- zmiany działają od razu mimo wydanych tokenów ----

    /**
     * Wyłączone konto traci dostęp od razu (już wydany token daje 401), a po ponownym włączeniu ten sam token
     * znowu działa.
     */
    @Test
    void shouldCutOffDisabledUserImmediately() throws Exception {
        String admin = adminToken();
        String librarian = login("jan.bibliotekarz", PASSWORD);
        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(librarian))).andExpect(status().isOk());

        updateUser(admin, "jan.bibliotekarz", "LIBRARIAN", false).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(librarian)))
                .andExpect(status().isUnauthorized());
        tryLogin("jan.bibliotekarz", PASSWORD).andExpect(status().isUnauthorized());

        updateUser(admin, "jan.bibliotekarz", "LIBRARIAN", true).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(librarian))).andExpect(status().isOk());
    }

    /**
     * Awans i degradacja działają od razu na już wydanym tokenie (rola pochodzi z bazy).
     */
    @Test
    void shouldApplyRoleChangeImmediately() throws Exception {
        String admin = adminToken();
        String librarian = login("jan.bibliotekarz", PASSWORD);
        mockMvc.perform(get(USERS).header("Authorization", bearer(librarian))).andExpect(status().isForbidden());

        updateUser(admin, "jan.bibliotekarz", "ADMIN", true).andExpect(status().isOk());
        mockMvc.perform(get(USERS).header("Authorization", bearer(librarian))).andExpect(status().isOk());

        updateUser(admin, "jan.bibliotekarz", "LIBRARIAN", true).andExpect(status().isOk());
        mockMvc.perform(get(USERS).header("Authorization", bearer(librarian))).andExpect(status().isForbidden());
    }

    // ---- reset hasła przez administratora ----

    /**
     * Po resecie stare hasło przestaje działać, a nowe pozwala się zalogować.
     */
    @Test
    void shouldResetPassword() throws Exception {
        mockMvc.perform(put(USERS + "/" + idOf("jan.bibliotekarz") + "/password")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        tryLogin("jan.bibliotekarz", PASSWORD).andExpect(status().isUnauthorized());
        tryLogin("jan.bibliotekarz", NEW_PASSWORD).andExpect(status().isOk());
    }

    /**
     * Reset z za słabym hasłem to 400, a dla nieznanego konta 404.
     */
    @Test
    void shouldRejectInvalidPasswordReset() throws Exception {
        String token = adminToken();

        mockMvc.perform(put(USERS + "/" + idOf("jan.bibliotekarz") + "/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"krotkie\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(USERS + "/999999/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNotFound());
        tryLogin("jan.bibliotekarz", PASSWORD).andExpect(status().isOk());
    }

    // ---- zmiana własnego hasła ----

    /**
     * Zalogowany użytkownik zmienia własne hasło: stare przestaje działać, nowe działa.
     */
    @Test
    void shouldChangeOwnPassword() throws Exception {
        String token = login("jan.bibliotekarz", PASSWORD);

        mockMvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        tryLogin("jan.bibliotekarz", PASSWORD).andExpect(status().isUnauthorized());
        tryLogin("jan.bibliotekarz", NEW_PASSWORD).andExpect(status().isOk());
    }

    /**
     * Błędne aktualne hasło, hasło takie samo jak obecne i za słabe nowe hasło to 400, a hasło się nie zmienia.
     */
    @Test
    void shouldRejectInvalidOwnPasswordChange() throws Exception {
        String token = login("jan.bibliotekarz", PASSWORD);
        String[] bodies = {
                "{\"currentPassword\":\"zle-haslo-zle-haslo\",\"newPassword\":\"" + NEW_PASSWORD + "\"}",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"krotkie\"}",
                "{\"currentPassword\":\"\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"
        };
        for (String body : bodies) {
            mockMvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        tryLogin("jan.bibliotekarz", PASSWORD).andExpect(status().isOk());
    }

    /**
     * Zmiana własnego hasła wymaga zalogowania.
     */
    @Test
    void shouldRequireLoginToChangeOwnPassword() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Za długie hasło przy logowaniu daje 401, a nie błąd serwera (także z polskimi literami, które zajmują
     * po 2 bajty).
     */
    @Test
    void shouldReturnUnauthorizedForOverlongLoginPassword() throws Exception {
        tryLogin("jan.bibliotekarz", "x".repeat(100)).andExpect(status().isUnauthorized());
        tryLogin("jan.bibliotekarz", "ą".repeat(60)).andExpect(status().isUnauthorized());
    }
}
