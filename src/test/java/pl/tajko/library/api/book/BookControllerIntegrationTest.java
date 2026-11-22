package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pl.tajko.library.api.AbstractIntegrationTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testy integracyjne API książek: żądania HTTP przez MockMvc do pełnego kontekstu Springa
 * z prawdziwą bazą PostgreSQL.
 */
@WithMockUser(roles = "ADMIN")
class BookControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/v1/books";
    private static final String ISBN_CLEAN_CODE = "9780132350884";
    private static final String ISBN_EFFECTIVE_JAVA = "9780134685991";
    private static final String ISBN_WITCHER = "9780306406157";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookItemRepository bookItemRepository;

    private MockMvc mockMvc;

    /** Tworzy MockMvc i czyści tabele, żeby testy były od siebie niezależne. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bookItemRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
    }

    // ---- pomocnicze ----

    private static Map<String, Object> validBook() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Czysty kod");
        body.put("author", "Robert C. Martin");
        body.put("publicationYear", 2008);
        body.put("isbn", ISBN_CLEAN_CODE);
        body.put("genre", "SCIENCE");
        return body;
    }

    private static String json(Map<String, Object> body) {
        return JSON.writeValueAsString(body);
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcResult postBook(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn();
    }

    private long createBook(Map<String, Object> body) throws Exception {
        MvcResult result = postBook(body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Number id = JsonPath.read(bodyOf(result), "$.id");
        return id.longValue();
    }

    private Book saveBook(String title, String author, int year, String isbn, Genre genre) {
        return bookRepository.save(new Book(title, author, year, isbn, genre));
    }

    private void seedCatalog() {
        saveBook("Czysty kod", "Robert C. Martin", 2008, ISBN_CLEAN_CODE, Genre.SCIENCE);
        saveBook("Effective Java", "Joshua Bloch", 2018, ISBN_EFFECTIVE_JAVA, Genre.SCIENCE);
        saveBook("Wiedźmin", "Andrzej Sapkowski", 1993, ISBN_WITCHER, Genre.FANTASY);
    }

    // ---- POST ----

    /**
     * Poprawne żądanie tworzy książkę: 201, nagłówek Location, ISBN-10 zapisany jako ISBN-13.
     */
    @Test
    void shouldCreateBook() throws Exception {
        Map<String, Object> body = validBook();
        body.put("isbn", "0132350882");

        MvcResult result = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Czysty kod"))
                .andExpect(jsonPath("$.isbn").value(ISBN_CLEAN_CODE))
                .andExpect(jsonPath("$.genre").value("SCIENCE"))
                .andExpect(jsonPath("$.copiesCount").value(0))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        Number id = JsonPath.read(bodyOf(result), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith(BASE_URL + "/" + id);
        assertThat(bookRepository.findByIsbn(ISBN_CLEAN_CODE)).isPresent();
    }

    static Stream<Arguments> invalidFieldValues() {
        int nextYear = Year.now().getValue() + 1;
        return Stream.of(
                Arguments.of("pusty tytuł", "title", "", "title"),
                Arguments.of("brak tytułu", "title", null, "title"),
                Arguments.of("autor z samych spacji", "author", "   ", "author"),
                Arguments.of("tytuł dłuższy niż 255 znaków", "title", "a".repeat(256), "title"),
                Arguments.of("rok przed 1450", "publicationYear", 1449, "publicationYear"),
                Arguments.of("rok z przyszłości", "publicationYear", nextYear, "publicationYear"),
                Arguments.of("brak roku", "publicationYear", null, "publicationYear"),
                Arguments.of("ISBN ze złą cyfrą kontrolną", "isbn", "9780306406158", "isbn"),
                Arguments.of("za krótki ISBN", "isbn", "123", "isbn"),
                Arguments.of("brak ISBN", "isbn", null, "isbn"),
                Arguments.of("brak gatunku", "genre", null, "genre"));
    }

    /**
     * Niepoprawne pole daje 400 w formacie Problem Details ze wskazaniem błędnego pola.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFieldValues")
    void shouldRejectInvalidField(String caseName, String field, Object value, String expectedField) throws Exception {
        Map<String, Object> body = validBook();
        body.put(field, value);

        MvcResult result = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertThat(bodyOf(result)).contains("\"field\":\"" + expectedField + "\"");
        assertThat(bookRepository.count()).isZero();
    }

    /**
     * Ten sam numer ISBN podany w innym formacie (ISBN-10 po ISBN-13) daje 409.
     */
    @Test
    void shouldRejectDuplicateIsbn() throws Exception {
        createBook(validBook());
        Map<String, Object> duplicate = validBook();
        duplicate.put("title", "Inny tytuł");
        duplicate.put("isbn", "0132350882");

        MvcResult result = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertThat(bodyOf(result)).contains("już istnieje");
        assertThat(bookRepository.count()).isEqualTo(1);
    }

    /**
     * Uszkodzony JSON i nieznany gatunek dają 400.
     */
    @Test
    void shouldRejectMalformedJsonAndUnknownGenre() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{ to nie jest json"))
                .andExpect(status().isBadRequest());

        Map<String, Object> body = validBook();
        body.put("genre", "POEM");
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /{id} ----

    /**
     * Odczyt po id zwraca książkę z liczbą egzemplarzy.
     */
    @Test
    void shouldReturnBookById() throws Exception {
        Book book = saveBook("Czysty kod", "Robert C. Martin", 2008, ISBN_CLEAN_CODE, Genre.SCIENCE);
        bookItemRepository.save(new BookItem(book));
        bookItemRepository.save(new BookItem(book));

        mockMvc.perform(get(BASE_URL + "/" + book.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(book.getId()))
                .andExpect(jsonPath("$.title").value("Czysty kod"))
                .andExpect(jsonPath("$.copiesCount").value(2));
    }

    /**
     * Nieistniejące id daje 404 w formacie Problem Details, a id niebędące liczbą daje 400.
     */
    @Test
    void shouldReturn404ForMissingBookAnd400ForBadId() throws Exception {
        mockMvc.perform(get(BASE_URL + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get(BASE_URL + "/abc")).andExpect(status().isBadRequest());
    }

    // ---- GET lista ----

    /**
     * Domyślna lista jest posortowana po tytule i ma metadane strony.
     */
    @Test
    void shouldListBooksSortedByTitle() throws Exception {
        seedCatalog();

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].title").value("Czysty kod"))
                .andExpect(jsonPath("$.content[1].title").value("Effective Java"))
                .andExpect(jsonPath("$.content[2].title").value("Wiedźmin"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    /**
     * Paginacja i sortowanie malejące działają zgodnie z parametrami.
     */
    @Test
    void shouldPageAndSortList() throws Exception {
        seedCatalog();

        mockMvc.perform(get(BASE_URL).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Wiedźmin"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(BASE_URL).param("sort", "title,desc"))
                .andExpect(jsonPath("$.content[0].title").value("Wiedźmin"));
    }

    /**
     * Zbyt duży rozmiar strony jest ograniczany do 100.
     */
    @Test
    void shouldClampPageSize() throws Exception {
        mockMvc.perform(get(BASE_URL).param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    /**
     * Wyszukiwanie po tytule i autorze nie rozróżnia wielkości liter.
     */
    @Test
    void shouldSearchByTitleOrAuthorIgnoringCase() throws Exception {
        seedCatalog();

        mockMvc.perform(get(BASE_URL).param("search", "KOD"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Czysty kod"));

        mockMvc.perform(get(BASE_URL).param("search", "sapkow"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Wiedźmin"));

        mockMvc.perform(get(BASE_URL).param("search", "nie ma takiej książki"))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    /**
     * Znak % w wyszukiwaniu jest traktowany dosłownie, więc nie dopasowuje wszystkiego.
     */
    @Test
    void shouldTreatPercentSignLiterally() throws Exception {
        seedCatalog();

        mockMvc.perform(get(BASE_URL).param("search", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * Filtr gatunku zawęża listę, a nieznany gatunek daje 400.
     */
    @Test
    void shouldFilterByGenre() throws Exception {
        seedCatalog();

        mockMvc.perform(get(BASE_URL).param("genre", "SCIENCE"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(BASE_URL).param("genre", "FANTASY"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(BASE_URL).param("genre", "POEM"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Sortowanie po niedozwolonym polu daje 400 z opisem dozwolonych pól.
     */
    @Test
    void shouldRejectSortingByDisallowedProperty() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE_URL).param("sort", "password,asc"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result)).contains("Niedozwolone pole sortowania");
    }

    /**
     * Lista pokazuje liczbę egzemplarzy każdej książki.
     */
    @Test
    void shouldShowItemsCountInList() throws Exception {
        Book book = saveBook("Czysty kod", "Robert C. Martin", 2008, ISBN_CLEAN_CODE, Genre.SCIENCE);
        saveBook("Wiedźmin", "Andrzej Sapkowski", 1993, ISBN_WITCHER, Genre.FANTASY);
        bookItemRepository.save(new BookItem(book));
        bookItemRepository.save(new BookItem(book));

        mockMvc.perform(get(BASE_URL))
                .andExpect(jsonPath("$.content[?(@.isbn=='" + ISBN_CLEAN_CODE + "')].copiesCount").value(2))
                .andExpect(jsonPath("$.content[?(@.isbn=='" + ISBN_WITCHER + "')].copiesCount").value(0));
    }

    // ---- PUT ----

    private Map<String, Object> updateBody(long version) {
        Map<String, Object> body = validBook();
        body.put("title", "Czysty kod. Wydanie 2");
        body.put("version", version);
        return body;
    }

    /**
     * Aktualizacja z aktualną wersją zmienia dane i zwiększa wersję.
     */
    @Test
    void shouldUpdateBook() throws Exception {
        long id = createBook(validBook());

        mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(updateBody(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Czysty kod. Wydanie 2"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(bookRepository.findById(id).orElseThrow().getTitle()).isEqualTo("Czysty kod. Wydanie 2");
    }

    /**
     * Drugi zapis ze starą wersją (równoczesna edycja) daje 409 i nie nadpisuje zmian pierwszego.
     */
    @Test
    void shouldRejectStaleVersion() throws Exception {
        long id = createBook(validBook());
        mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(updateBody(0))))
                .andExpect(status().isOk());

        Map<String, Object> stale = updateBody(0);
        stale.put("title", "Zmiana drugiego użytkownika");
        MvcResult result = mockMvc.perform(
                        put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(stale)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertThat(bodyOf(result)).contains("zmieniona");
        assertThat(bookRepository.findById(id).orElseThrow().getTitle()).isEqualTo("Czysty kod. Wydanie 2");
    }

    /**
     * Aktualizacja bez wersji daje 400, a z numerem ISBN innej książki daje 409.
     */
    @Test
    void shouldRejectUpdateWithoutVersionAndWithTakenIsbn() throws Exception {
        long id = createBook(validBook());
        Map<String, Object> other = validBook();
        other.put("isbn", ISBN_EFFECTIVE_JAVA);
        other.put("title", "Effective Java");
        createBook(other);

        Map<String, Object> withoutVersion = updateBody(0);
        withoutVersion.remove("version");
        MvcResult noVersion = mockMvc.perform(
                        put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(withoutVersion)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(bodyOf(noVersion)).contains("\"field\":\"version\"");

        Map<String, Object> takenIsbn = updateBody(0);
        takenIsbn.put("isbn", ISBN_EFFECTIVE_JAVA);
        mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(takenIsbn)))
                .andExpect(status().isConflict());
    }

    /**
     * Aktualizacja nieistniejącej książki daje 404.
     */
    @Test
    void shouldReturn404WhenUpdatingMissingBook() throws Exception {
        mockMvc.perform(put(BASE_URL + "/999999").contentType(MediaType.APPLICATION_JSON).content(json(updateBody(0))))
                .andExpect(status().isNotFound());
    }

    // ---- DELETE ----

    /**
     * Książka bez egzemplarzy jest usuwana (204), a potem nie istnieje (404).
     */
    @Test
    void shouldDeleteBookWithoutItems() throws Exception {
        long id = createBook(validBook());

        mockMvc.perform(delete(BASE_URL + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE_URL + "/" + id)).andExpect(status().isNotFound());
    }

    /**
     * Książki z egzemplarzami nie da się usunąć (409), a książka zostaje w bazie.
     */
    @Test
    void shouldRejectDeletingBookWithItems() throws Exception {
        Book book = saveBook("Czysty kod", "Robert C. Martin", 2008, ISBN_CLEAN_CODE, Genre.SCIENCE);
        bookItemRepository.save(new BookItem(book));

        MvcResult result = mockMvc.perform(delete(BASE_URL + "/" + book.getId()))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(bodyOf(result)).contains("egzemplarze");
        assertThat(bookRepository.findById(book.getId())).isPresent();
    }

    /**
     * Usuwanie nieistniejącej książki daje 404.
     */
    @Test
    void shouldReturn404WhenDeletingMissingBook() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/999999")).andExpect(status().isNotFound());
    }
}