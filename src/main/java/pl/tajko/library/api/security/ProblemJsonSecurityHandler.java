package pl.tajko.library.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Zamienia odmowy dostępu z filtrów Spring Security na odpowiedzi w formacie Problem Details
 * ({@code application/problem+json}), takim samym jak reszta błędów API. Brak ważnego tokenu
 * kończy się kodem 401 (z nagłówkiem {@code WWW-Authenticate: Bearer}), a brak uprawnień kodem 403.
 * Komunikaty nie zdradzają, dlaczego token został odrzucony.
 */
public class ProblemJsonSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

    /**
     * Odpowiada kodem 401, gdy żądanie nie ma ważnego tokenu.
     *
     * @param request       żądanie
     * @param response      odpowiedź
     * @param authException przyczyna odmowy
     * @throws IOException gdy nie uda się zapisać odpowiedzi
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Ustawia status 401 i nagłówek WWW-Authenticate (z kodem błędu, jeśli token był, ale niepoprawny).
        bearerEntryPoint.commence(request, response, authException);
        write(request, response, HttpStatus.UNAUTHORIZED, "Brak uwierzytelnienia",
                "Wymagany jest ważny token dostępu. Zaloguj się: POST /api/v1/auth/login");
    }

    /**
     * Odpowiada kodem 403, gdy użytkownik jest zalogowany, ale jego rola nie pozwala na operację.
     *
     * @param request               żądanie
     * @param response              odpowiedź
     * @param accessDeniedException przyczyna odmowy
     * @throws IOException gdy nie uda się zapisać odpowiedzi
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "Brak uprawnień",
                "Twoja rola nie pozwala na wykonanie tej operacji.");
    }

    private static void write(HttpServletRequest request, HttpServletResponse response,
                              HttpStatus status, String title, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
    }
}
