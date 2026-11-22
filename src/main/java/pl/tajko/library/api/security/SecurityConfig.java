package pl.tajko.library.api.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Konfiguracja zabezpieczeń API. Wszystkie reguły dostępu są w jednym miejscu ({@link #apiSecurityFilterChain}),
 * a nie rozrzucone po kontrolerach.
 *
 * <p>Aplikacja jest bezstanowa: nie tworzy sesji ani ciasteczek, a każde żądanie niesie token JWT
 * w nagłówku {@code Authorization: Bearer ...}. CSRF jest wyłączony, bo przeglądarka nie dołącza tokenu
 * automatycznie (jak robi to z ciasteczkami).
 *
 * <p>Rola użytkownika pochodzi z bazy i jest sprawdzana przy każdym żądaniu ({@link DatabaseJwtAuthenticationConverter}),
 * więc zmiana roli i wyłączenie konta działają od razu.
 *
 * <p>Reguły (pierwsza pasująca wygrywa):
 * <ul>
 *   <li>publiczne: logowanie, {@code /actuator/health}, Swagger UI i {@code /v3/api-docs},</li>
 *   <li>{@code /api/v1/users/**} (zarządzanie kontami): tylko {@code ADMIN},</li>
 *   <li>{@code DELETE /api/v1/**}: tylko {@code ADMIN},</li>
 *   <li>pozostałe {@code /api/v1/**} (odczyt, dodawanie, edycja, wypożyczenia): {@code LIBRARIAN} i {@code ADMIN},</li>
 *   <li>cała reszta: wymaga tokenu.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String ADMIN = "ADMIN";
    static final String LIBRARIAN = "LIBRARIAN";

    /**
     * Hierarchia ról: {@code ADMIN} może wszystko, co {@code LIBRARIAN}, więc reguły wystarczy pisać
     * dla najniższej roli. Metoda jest statyczna, żeby Spring tworzył ten bean przed resztą konfiguracji.
     *
     * @return hierarchia ról
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(ADMIN).implies(LIBRARIAN)
                .build();
    }

    /**
     * Menedżer uwierzytelniania używany przy logowaniu (sprawdza hasło z bazy przez {@link AppUserDetailsService}).
     *
     * @param configuration konfiguracja uwierzytelniania Spring Security
     * @return menedżer uwierzytelniania
     * @throws Exception gdy nie uda się go zbudować
     */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Łańcuch filtrów z regułami dostępu i obsługą tokenów JWT.
     *
     * @param http      konfigurator zabezpieczeń
     * @param converter konwerter tokenu na uwierzytelnienie (rola z bazy, nie z tokenu)
     * @return łańcuch filtrów
     * @throws Exception gdy konfiguracja jest niepoprawna
     */
    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, DatabaseJwtAuthenticationConverter converter)
            throws Exception {
        ProblemJsonSecurityHandler problemHandler = new ProblemJsonSecurityHandler();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Błędy przekazane przez kontener (dispatch ERROR) zachowują pierwotny kod odpowiedzi.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole(ADMIN)
                        .requestMatchers("/api/v1/**").hasRole(LIBRARIAN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter::convert))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }
}
