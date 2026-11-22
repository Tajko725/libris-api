package pl.tajko.library.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * Testy jednostkowe odpowiedzi 401 i 403 w formacie Problem Details.
 */
class ProblemJsonSecurityHandlerTest {

    private final ProblemJsonSecurityHandler handler = new ProblemJsonSecurityHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/books");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /**
     * Brak uwierzytelnienia daje 401, nagłówek WWW-Authenticate: Bearer i opis w formacie Problem Details.
     */
    @Test
    void shouldWriteUnauthorizedProblem() throws Exception {
        handler.commence(request, response, new InsufficientAuthenticationException("brak"));

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(401);
        assertThat((String) JsonPath.read(body, "$.title")).isEqualTo("Brak uwierzytelnienia");
        assertThat((String) JsonPath.read(body, "$.detail")).contains("POST /api/v1/auth/login");
        assertThat((String) JsonPath.read(body, "$.instance")).isEqualTo("/api/v1/books");
    }

    /**
     * Brak uprawnień daje 403 z opisem w formacie Problem Details i bez nagłówka WWW-Authenticate.
     */
    @Test
    void shouldWriteForbiddenProblem() throws Exception {
        handler.handle(request, response, new AccessDeniedException("odmowa"));

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(403);
        assertThat((String) JsonPath.read(body, "$.title")).isEqualTo("Brak uprawnień");
        assertThat((String) JsonPath.read(body, "$.detail")).contains("Twoja rola");
    }

    /**
     * Odpowiedź nie ujawnia przyczyny odmowy z wyjątku (np. szczegółów błędu tokenu).
     */
    @Test
    void shouldNotLeakExceptionMessage() throws Exception {
        handler.commence(request, response, new InsufficientAuthenticationException("szczegol-techniczny-xyz"));

        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("szczegol-techniczny-xyz");
    }
}
