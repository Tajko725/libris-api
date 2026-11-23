package pl.tajko.library.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
// Importy @LocalServerPort uzupełnij przez Alt+Enter
// (w Spring Boot 4 pakiet: org.springframework.boot.test.web.server.LocalServerPort).
import org.springframework.boot.test.web.server.LocalServerPort;
import pl.tajko.library.api.TestcontainersConfiguration;

/**
 * Testy sprawdzające, że serwer przyjmuje żądania z dużą liczbą języków
 * w nagłówku {@code Accept-Language}, na przykład z przeglądarki Opera.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TomcatConfigIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    /**
     * Wysyła żądanie z 15 językami i sprawdza, że endpoint zdrowia odpowiada statusem 200.
     */
    @Test
    void shouldAcceptManyLocalesInAcceptLanguageHeader() throws Exception {
        String manyLocales = "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7,de;q=0.6,fr;q=0.5,es;q=0.4,"
                + "it;q=0.3,cs;q=0.2,sk;q=0.1,uk;q=0.09,ru;q=0.08,nl;q=0.07,sv;q=0.06,pt;q=0.05";

        HttpResponse<String> response = get(manyLocales);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    /**
     * Sprawdza, że zwykłe żądanie z jednym językiem nadal działa poprawnie.
     */
    @Test
    void shouldAcceptSingleLocale() throws Exception {
        HttpResponse<String> response = get("pl-PL");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    private HttpResponse<String> get(String acceptLanguage) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .header("Accept-Language", acceptLanguage)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}