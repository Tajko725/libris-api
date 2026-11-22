package pl.tajko.library.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
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
     * Specyfikacja opisuje endpointy wypożyczeń (w tym zwrot i przedłużenie), błędy 409 w formacie
     * Problem Details oraz nie zawiera operacji, których API nie ma.
     */
    @Test
    void shouldDocumentLoanEndpoints() throws Exception {
        String loans = "$.paths['/api/v1/loans']";
        String loanById = "$.paths['/api/v1/loans/{id}']";
        String giveBack = "$.paths['/api/v1/loans/{id}/return']";
        String renew = "$.paths['/api/v1/loans/{id}/renew']";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(loans + ".post").exists())
                .andExpect(jsonPath(loans + ".get").exists())
                .andExpect(jsonPath(loanById + ".get").exists())
                .andExpect(jsonPath(giveBack + ".post").exists())
                .andExpect(jsonPath(renew + ".post").exists())
                .andExpect(jsonPath(loans + ".post.responses['409'].content['application/problem+json']").exists())
                .andExpect(jsonPath(renew + ".post.responses['409'].content['application/problem+json']").exists())
                .andExpect(jsonPath(loanById + ".delete").doesNotExist())
                .andExpect(jsonPath(loanById + ".put").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LoanResponse").exists());
    }

    /**
     * Usuwanie egzemplarza i czytelnika ma opisaną odpowiedź 409 (historia wypożyczeń), a lista książek
     * parametr available.
     */
    @Test
    void shouldDocumentLoanRelatedChangesInBookAndReaderEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/books/{bookId}/items/{itemId}'].delete.responses['409']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/readers/{id}'].delete.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/books'].get.parameters[?(@.name=='available')]").exists());
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

    /**
     * Specyfikacja opisuje uwierzytelnianie tokenem Bearer JWT, wymagane domyślnie dla wszystkich operacji.
     */
    @Test
    void shouldDocumentBearerAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    /**
     * Logowanie jest opisane (z błędami 400 i 401), a chronione operacje mają opisane odpowiedzi 401 i 403.
     * Logowanie nie dostaje odpowiedzi 403, bo jest publiczne.
     */
    @Test
    void shouldDocumentLoginAndSecurityResponses() throws Exception {
        String login = "$.paths['/api/v1/auth/login'].post";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(login).exists())
                .andExpect(jsonPath(login + ".responses['200']").exists())
                .andExpect(jsonPath(login + ".responses['400']").exists())
                .andExpect(jsonPath(login + ".responses['401']").exists())
                .andExpect(jsonPath(login + ".responses['403']").doesNotExist())
                .andExpect(jsonPath(BOOKS + ".get.responses['401'].content['application/problem+json']").exists())
                .andExpect(jsonPath(BOOKS + ".get.responses['403'].content['application/problem+json']").exists())
                .andExpect(jsonPath(BOOK_BY_ID + ".delete.responses['403']").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse").exists());
    }
}
