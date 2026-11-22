package pl.tajko.library.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Testy jednostkowe {@link TokenService} z prawdziwym koderem JWT i zegarem ustawionym na stały moment.
 */
class TokenServiceTest {

    private static final String SECRET = "sekret-testowy-sekret-testowy-1234567890";
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(60), "libris-api");
    private final JwtConfig jwtConfig = new JwtConfig();

    private TokenService tokenService;

    /** Tworzy serwis z kluczem testowym i zegarem stojącym w miejscu. */
    @BeforeEach
    void setUp() {
        tokenService = new TokenService(jwtConfig.jwtEncoder(properties), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Authentication login(String username, String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(username, null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    /** Dekoder bez sprawdzania czasu (zegar testu stoi w ustalonym dniu), ale z weryfikacją podpisu. */
    private Jwt decodeIgnoringTime(String token) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder.decode(token);
    }

    /**
     * Token jest podpisany algorytmem HS256 i zawiera nazwę użytkownika, wystawcę, role, czasy i identyfikator.
     */
    @Test
    void shouldIssueSignedTokenWithExpectedClaims() {
        TokenService.IssuedToken issued = tokenService.issue(login("jan.kowalski", "ROLE_LIBRARIAN"));

        Jwt jwt = decodeIgnoringTime(issued.value());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo("jan.kowalski");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("libris-api");
        assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly("LIBRARIAN");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(jwt.getId()).isNotBlank();
    }

    /**
     * Wynik zawiera czas wygaśnięcia i liczbę sekund ważności zgodne z ustawieniami.
     */
    @Test
    void shouldReportExpirationOfIssuedToken() {
        TokenService.IssuedToken issued = tokenService.issue(login("admin", "ROLE_ADMIN"));

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(issued.expiresIn()).isEqualTo(3600);
    }

    /**
     * Do tokenu trafiają tylko role (bez prefiksu ROLE_), a inne uprawnienia są pomijane.
     */
    @Test
    void shouldPutOnlyRolesWithoutPrefixInToken() {
        TokenService.IssuedToken issued = tokenService.issue(login("jan", "SCOPE_read", "ROLE_LIBRARIAN", "inne"));

        List<String> roles = decodeIgnoringTime(issued.value()).getClaimAsStringList(TokenService.ROLES_CLAIM);
        assertThat(roles).containsExactly("LIBRARIAN");
    }

    /**
     * Każdy wystawiony token ma inny identyfikator (jti).
     */
    @Test
    void shouldGiveEachTokenUniqueId() {
        Authentication authentication = login("jan", "ROLE_LIBRARIAN");

        String first = decodeIgnoringTime(tokenService.issue(authentication).value()).getId();
        String second = decodeIgnoringTime(tokenService.issue(authentication).value()).getId();

        assertThat(first).isNotEqualTo(second);
    }

    /**
     * Token podpisany innym sekretem jest odrzucany przez dekoder aplikacji.
     */
    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        String token = tokenService.issue(login("jan", "ROLE_ADMIN")).value();
        JwtProperties other = new JwtProperties("inny-sekret-inny-sekret-inny-sekret-12", Duration.ofMinutes(60),
                "libris-api");
        JwtDecoder otherDecoder = jwtConfig.jwtDecoder(other);

        assertThatThrownBy(() -> otherDecoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
