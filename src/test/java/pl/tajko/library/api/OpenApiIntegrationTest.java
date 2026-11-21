package pl.tajko.library.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Testy integracyjne dokumentacji OpenAPI: sprawdzają, że specyfikacja jest dostępna,
 * opisuje wszystkie endpointy książek i nie ujawnia niczego ponad to.
 */
class OpenApiIntegrationTest extends AbstractIntegrationTest {

    private static final String BOOKS = "$.paths['/api/v1/books']";
    private static final String BOOK_BY_ID = "$.paths['/api/v1/books/{id}']";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    /** Tworzy MockMvc dla pełnego kontekstu aplikacji. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /**
     * Specyfikacja jest dostępna i ma tytuł oraz wersję API.
     */
    @Test
    void shouldExposeSpecificationWithApiInfo() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Libris API"))
                .andExpect(jsonPath("$.info.version").value("v1"));
    }

    /**
     * Specyfikacja opisuje wszystkie operacje na książkach.
     */
    @Test
    void shouldDocumentAllBookEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(BOOKS + ".post").exists())
                .andExpect(jsonPath(BOOKS + ".get").exists())
                .andExpect(jsonPath(BOOK_BY_ID + ".get").exists())
                .andExpect(jsonPath(BOOK_BY_ID + ".put").exists())
                .andExpect(jsonPath(BOOK_BY_ID + ".delete").exists());
    }

    /**
     * Błędy 404 i 409 są opisane w formacie Problem Details.
     */
    @Test
    void shouldDocumentProblemDetailsResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(BOOK_BY_ID + ".delete.responses['404'].content['application/problem+json']")
                        .exists())
                .andExpect(jsonPath(BOOK_BY_ID + ".delete.responses['409'].content['application/problem+json']")
                        .exists())
                .andExpect(jsonPath(BOOKS + ".post.responses['400'].content['application/problem+json']")
                        .exists());
    }

    /**
     * Specyfikacja opisuje endpointy egzemplarzy i nie zawiera operacji, których API nie ma.
     */
    @Test
    void shouldDocumentBookItemEndpoints() throws Exception {
        String items = "$.paths['/api/v1/books/{bookId}/items']";
        String itemById = "$.paths['/api/v1/books/{bookId}/items/{itemId}']";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(items + ".post").exists())
                .andExpect(jsonPath(items + ".get").exists())
                .andExpect(jsonPath(itemById + ".delete").exists())
                .andExpect(jsonPath(items + ".delete").doesNotExist())
                .andExpect(jsonPath(itemById + ".get").doesNotExist());
    }

    /**
     * Specyfikacja opisuje endpointy czytelników i nie zawiera operacji, których API nie ma.
     */
    @Test
    void shouldDocumentReaderEndpoints() throws Exception {
        String readers = "$.paths['/api/v1/readers']";
        String readerById = "$.paths['/api/v1/readers/{id}']";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(readers + ".post").exists())
                .andExpect(jsonPath(readers + ".get").exists())
                .andExpect(jsonPath(readerById + ".get").exists())
                .andExpect(jsonPath(readerById + ".put").exists())
                .andExpect(jsonPath(readerById + ".delete").exists())
                .andExpect(jsonPath(readerById + ".post").doesNotExist())
                .andExpect(jsonPath(readers + ".delete").doesNotExist());
    }

    /**
     * Specyfikacja nie zawiera operacji, których API nie ma, ani endpointów Actuatora.
     */
    @Test
    void shouldNotDocumentOperationsThatDoNotExist() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(BOOK_BY_ID + ".post").doesNotExist())
                .andExpect(jsonPath(BOOKS + ".delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }

    /**
     * Strona Swagger UI jest dostępna.
     */
    @Test
    void shouldServeSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}