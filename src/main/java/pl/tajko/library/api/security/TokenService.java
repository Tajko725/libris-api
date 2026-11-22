package pl.tajko.library.api.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Wystawia tokeny dostępu JWT dla uwierzytelnionego użytkownika. Token zawiera nazwę użytkownika
 * ({@code sub}), jego rolę ({@code roles}), wystawcę, czas wystawienia i wygaśnięcia oraz unikalny
 * identyfikator ({@code jti}).
 */
@Service
public class TokenService {

    /** Nazwa właściwości z rolami w tokenie. */
    public static final String ROLES_CLAIM = "roles";

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    /**
     * Tworzy serwis.
     *
     * @param jwtEncoder koder tokenów
     * @param properties ustawienia JWT
     * @param clock      zegar aplikacji
     */
    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Wystawiony token razem z informacją o jego ważności.
     *
     * @param value     tekst tokenu
     * @param expiresAt moment wygaśnięcia
     * @param expiresIn liczba sekund ważności od chwili wystawienia
     */
    public record IssuedToken(String value, Instant expiresAt, long expiresIn) {
    }

    /**
     * Wystawia token dla zalogowanego użytkownika. Do tokenu trafiają tylko role (uprawnienia
     * zaczynające się od {@code ROLE_}, bez tego prefiksu).
     *
     * @param authentication wynik poprawnego logowania
     * @return token i czas jego ważności
     */
    public IssuedToken issue(Authentication authentication) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.expiration());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(authentication.getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt, properties.expiration().toSeconds());
    }
}
