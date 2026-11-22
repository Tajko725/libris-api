package pl.tajko.library.api.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Konfiguracja podpisywania i weryfikacji tokenów JWT: ten sam sekret (HS256) służy do wystawiania
 * tokenów przy logowaniu i do sprawdzania ich w każdym kolejnym żądaniu.
 */
@Configuration
public class JwtConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JwtConfig.class);

    private static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Koder tokenów (podpisuje token sekretem). Przy sekrecie deweloperskim ostrzega w logu.
     *
     * @param properties ustawienia JWT
     * @return koder JWT
     */
    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        if (properties.usesDevelopmentSecret()) {
            LOG.warn("Używam deweloperskiego sekretu JWT. Na środowisku innym niż lokalne ustaw zmienną LIBRIS_JWT_SECRET");
        }
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    /**
     * Dekoder tokenów: przyjmuje wyłącznie HS256, sprawdza podpis, ważność w czasie oraz wystawcę.
     *
     * @param properties ustawienia JWT
     * @return dekoder JWT
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
