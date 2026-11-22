package pl.tajko.library.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pl.tajko.library.api.AbstractIntegrationTest;
import pl.tajko.library.api.book.BookItemRepository;
import pl.tajko.library.api.book.BookRepository;
import pl.tajko.library.api.user.AppUser;
import pl.tajko.library.api.user.AppUserRepository;
import pl.tajko.library.api.user.AppUserService;
import pl.tajko.library.api.user.Role;

/**
 * Testy integracyjne zabezpieczeń na pełnym kontekście z prawdziwą bazą: logowanie, ważność i podpis tokenów,
 * reguły dostępu dla ról oraz publiczne adresy. Tokeny pochodzą z prawdziwego logowania, a nie z atrap.
 */
class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Tajne-haslo-123";
    private static final String BOOK_JSON = """
            {"title":"Czysty kod","author":"Robert C. Martin","publicationYear":2008,
             "isbn":"9780132350884","genre":"SCIENCE"}""";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserService userService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookItemRepository bookItemRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mockMvc;

    /** Tworzy MockMvc z filtrami zabezpieczeń oraz dwa konta: bibliotekarza i administratora. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanUp();
        userService.createUser("jan.bibliotekarz", PASSWORD, Role.LIBRARIAN);
        userService.createUser("anna.admin", PASSWORD, Role.ADMIN);
    }

    /** Sprząta po teście, bo baza jest wspólna dla klas testowych. */
    @AfterEach
    void cleanUp() {
        bookItemRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ---- pomocnicze ----

    private static String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String librarianToken() throws Exception {
        return login("jan.bibliotekarz", PASSWORD);
    }

    private String adminToken() throws Exception {
        return login("anna.admin", PASSWORD);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long createBook(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String mint(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt, List<String> roles) {
        return mint(encoder, issuer, "jan.bibliotekarz", issuedAt, expiresAt, roles);
    }

    private String mint(JwtEncoder encoder, String issuer, String subject, Instant issuedAt, Instant expiresAt,
                        List<String> roles) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TokenService.ROLES_CLAIM, roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String mintValid(List<String> roles) {
        Instant now = Instant.now();
        return mint(jwtEncoder, "libris-api", now, now.plus(1, ChronoUnit.HOURS), roles);
    }

    // ---- logowanie ----

    /**
     * Poprawne dane dają token typu Bearer ważny 3600 sekund (60 minut).
     */
    @Test
    void shouldLoginWithValidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("jan.bibliotekarz", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    /**
     * Wielkość liter i spacje wokół nazwy nie mają znaczenia przy logowaniu.
     */
    @Test
    void shouldNormalizeUsernameOnLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("  Jan.Bibliotekarz ", PASSWORD)))
                .andExpect(status().isOk());
    }

    /**
     * Złe hasło, nieznany użytkownik i konto wyłączone dają identyczną odpowiedź 401, więc nie da się
     * sprawdzić, które konta istnieją.
     */
    @Test
    void shouldGiveIdenticalAnswerForEveryLoginFailure() throws Exception {
        userService.createUser("wylaczony.user", PASSWORD, Role.LIBRARIAN);
        AppUser toDisable = userRepository.findByUsername("wylaczony.user").orElseThrow();
        toDisable.setEnabled(false);
        userRepository.saveAndFlush(toDisable);

        String[] bodies = {
                loginBody("jan.bibliotekarz", "zle-haslo-zle-haslo"),
                loginBody("nie.istnieje", PASSWORD),
                loginBody("wylaczony.user", PASSWORD)
        };
        for (String body : bodies) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Nieudane logowanie"))
                    .andExpect(jsonPath("$.detail").value("Niepoprawna nazwa użytkownika lub hasło."));
        }
    }

    /**
     * Puste dane logowania to błąd walidacji 400 z listą pól.
     */
    @Test
    void shouldRejectBlankLoginFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", "  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    /**
     * Niepoprawny JSON przy logowaniu to 400, a nie błąd serwera.
     */
    @Test
    void shouldRejectMalformedLoginBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nie json"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Logowanie jest tylko pod POST: GET na ten adres wymaga tokenu jak każdy inny adres API.
     */
    @Test
    void shouldNotAllowGetOnLoginWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    // ---- tokeny ----

    /**
     * Token z logowania pozwala wywołać chroniony endpoint.
     */
    @Test
    void shouldAcceptTokenFromLogin() throws Exception {
        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(librarianToken())))
                .andExpect(status().isOk());
    }

    /**
     * Brak tokenu to 401 w formacie Problem Details z nagłówkiem WWW-Authenticate.
     */
    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Brak uwierzytelnienia"))
                .andExpect(jsonPath("$.instance").value("/api/v1/books"));
    }

    /**
     * Przypadkowy ciąg zamiast tokenu to 401 z kodem błędu invalid_token.
     */
    @Test
    void shouldRejectGarbageToken() throws Exception {
        mockMvc.perform(get("/api/v1/books").header("Authorization", "Bearer to.nie.jest.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Token po terminie ważności jest odrzucany.
     */
    @Test
    void shouldRejectExpiredToken() throws Exception {
        Instant now = Instant.now();
        String expired = mint(jwtEncoder, "libris-api", now.minus(3, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.HOURS), List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Token podpisany cudzym sekretem jest odrzucany, nawet gdy ma rolę administratora.
     */
    @Test
    void shouldRejectTokenSignedWithForeignSecret() throws Exception {
        JwtEncoder foreign = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "cudzy-sekret-cudzy-sekret-cudzy-sekret-1".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        String forged = mint(foreign, "libris-api", now, now.plus(1, ChronoUnit.HOURS), List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Token z innym wystawcą jest odrzucany.
     */
    @Test
    void shouldRejectTokenFromOtherIssuer() throws Exception {
        Instant now = Instant.now();
        String token = mint(jwtEncoder, "ktos-inny", now, now.plus(1, ChronoUnit.HOURS), List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Rola zapisana w tokenie nie ma znaczenia: uprawnienia wynikają z konta w bazie. Token z rolą ADMIN
     * dla bibliotekarza nie pozwala usuwać, a token z nieznaną rolą nadal daje uprawnienia bibliotekarza.
     */
    @Test
    void shouldIgnoreRoleClaimInToken() throws Exception {
        long bookId = createBook(adminToken());

        mockMvc.perform(delete("/api/v1/books/" + bookId)
                        .header("Authorization", bearer(mintValid(List.of("ADMIN")))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Brak uprawnień"));
        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(mintValid(List.of("ROOT")))))
                .andExpect(status().isOk());
    }

    /**
     * Poprawnie podpisany token konta, którego nie ma w bazie, jest odrzucany.
     */
    @Test
    void shouldRejectValidTokenOfUnknownUser() throws Exception {
        Instant now = Instant.now();
        String token = mint(jwtEncoder, "libris-api", "duch.nieistnieje", now, now.plus(1, ChronoUnit.HOURS),
                List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    /**
     * Token konta usuniętego z bazy po zalogowaniu przestaje działać od razu.
     */
    @Test
    void shouldRejectTokenOfDeletedUser() throws Exception {
        String token = librarianToken();
        userRepository.delete(userRepository.findByUsername("jan.bibliotekarz").orElseThrow());
        userRepository.flush();

        mockMvc.perform(get("/api/v1/books").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Za długie hasło przy logowaniu (ponad 72 bajty) daje 401, a nie błąd serwera.
     */
    @Test
    void shouldReturnUnauthorizedForOverlongPasswordOnLogin() throws Exception {
        for (String password : List.of("x".repeat(100), "ą".repeat(60))) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("jan.bibliotekarz", password)))
                    .andExpect(status().isUnauthorized());
        }
    }

    /**
     * Logowanie hasłem w nagłówku Basic nie jest obsługiwane.
     */
    @Test
    void shouldNotAcceptBasicAuthentication() throws Exception {
        String basic = java.util.Base64.getEncoder()
                .encodeToString(("jan.bibliotekarz:" + PASSWORD).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/books").header("Authorization", "Basic " + basic))
                .andExpect(status().isUnauthorized());
    }

    // ---- role ----

    /**
     * Bibliotekarz czyta i dodaje, ale nie usuwa i nie zarządza użytkownikami.
     */
    @Test
    void shouldLetLibrarianReadAndAddButNotDeleteOrManageUsers() throws Exception {
        String token = librarianToken();
        long bookId = createBook(token);

        mockMvc.perform(get("/api/v1/books/" + bookId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/books/" + bookId).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        assertThat(bookRepository.existsById(bookId)).isTrue();
    }

    /**
     * Administrator robi to, co bibliotekarz (hierarchia ról), i dodatkowo usuwa.
     */
    @Test
    void shouldLetAdminDoEverythingLibrarianCanAndDelete() throws Exception {
        String token = adminToken();
        long bookId = createBook(token);

        mockMvc.perform(get("/api/v1/books/" + bookId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/books/" + bookId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(bookRepository.existsById(bookId)).isFalse();
    }

    /**
     * Rola pochodzi z bazy: po awansie konta już wydany token bibliotekarza od razu pozwala usuwać.
     */
    @Test
    void shouldApplyPromotionToAlreadyIssuedToken() throws Exception {
        String oldToken = librarianToken();
        long bookId = createBook(adminToken());

        mockMvc.perform(delete("/api/v1/books/" + bookId).header("Authorization", bearer(oldToken)))
                .andExpect(status().isForbidden());

        AppUser user = userRepository.findByUsername("jan.bibliotekarz").orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);

        mockMvc.perform(delete("/api/v1/books/" + bookId).header("Authorization", bearer(oldToken)))
                .andExpect(status().isNoContent());
    }

    // ---- adresy publiczne i reszta ----

    /**
     * Stan aplikacji, specyfikacja OpenAPI i Swagger UI są dostępne bez tokenu.
     */
    @Test
    void shouldServePublicEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        int redirectStatus = mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus();
        assertThat(redirectStatus).isNotIn(401, 403);
    }

    /**
     * Inne endpointy Actuatora (info) i nieznane adresy wymagają tokenu, więc anonim nie dowie się,
     * co istnieje.
     */
    @Test
    void shouldRequireTokenForEverythingElse() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/nie-ma-takiego-adresu")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/nie-ma-takiego-adresu")).andExpect(status().isUnauthorized());
    }

    /**
     * Zalogowany bibliotekarz dla nieznanego adresu API dostaje zwykłe 404 w formacie Problem Details.
     */
    @Test
    void shouldReturnProblemNotFoundForUnknownApiPathWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/nie-ma-takiego-adresu").header("Authorization", bearer(librarianToken())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
