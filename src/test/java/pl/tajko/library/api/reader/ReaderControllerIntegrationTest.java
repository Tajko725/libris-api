package pl.tajko.library.api.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pl.tajko.library.api.AbstractIntegrationTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testy integracyjne API czytelników: żądania HTTP przez MockMvc do pełnego kontekstu Springa
 * z prawdziwą bazą PostgreSQL.
 */
class ReaderControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/v1/readers";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ReaderRepository readerRepository;

    private MockMvc mockMvc;

    /** Tworzy MockMvc i czyści tabelę, żeby testy były od siebie niezależne. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        readerRepository.deleteAllInBatch();
    }

    // ---- pomocnicze ----

    private static Map<String, Object> validReader() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Anna");
        body.put("lastName", "Nowak");
        body.put("email", "anna.nowak@example.com");
        body.put("phone", "+48 123 456 789");
        return body;
    }

    private static Map<String, Object> updateBody(boolean active, long version) {
        Map<String, Object> body = validReader();
        body.put("lastName", "Kowalska");
        body.put("active", active);
        body.put("version", version);
        return body;
    }

    private static String json(Map<String, Object> body) {
        return JSON.writeValueAsString(body);
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcResult postReader(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn();
    }

    private long createReader(Map<String, Object> body) throws Exception {
        MvcResult result = postReader(body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Number id = JsonPath.read(bodyOf(result), "$.id");
        return id.longValue();
    }

    private MvcResult putReader(long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn();
    }

    private Reader saveReader(String firstName, String lastName, String email) {
        return readerRepository.save(new Reader(firstName, lastName, email, null));
    }

    private void seedReaders() {
        saveReader("Anna", "Nowak", "anna.nowak@example.com");
        saveReader("Jan", "Kowalski", "jan.kowalski@example.com");
        saveReader("Ewa", "Adamska", "ewa.adamska@example.org");
    }

    // ---- POST ----

    /**
     * Poprawne żądanie rejestruje czytelnika: 201, nagłówek Location, numer karty LB-NNNNNN,
     * status aktywny, wersja 0 i e-mail małymi literami.
     */
    @Test
    void shouldCreateReader() throws Exception {
        Map<String, Object> body = validReader();
        body.put("email", "Anna.Nowak@Example.COM");

        MvcResult result = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.email").value("anna.nowak@example.com"))
                .andExpect(jsonPath("$.phone").value("+48 123 456 789"))
                .andExpect(jsonPath("$.cardNumber").value(org.hamcrest.Matchers.matchesPattern("LB-\\d{6}")))
                .andExpect(jsonPath("$.registeredAt").exists())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        Number id = JsonPath.read(bodyOf(result), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith(BASE_URL + "/" + id);
        assertThat(readerRepository.findByEmail("anna.nowak@example.com")).isPresent();
    }

    /**
     * Telefon jest opcjonalny: bez niego czytelnik jest rejestrowany z pustą wartością.
     */
    @Test
    void shouldCreateReaderWithoutPhone() throws Exception {
        Map<String, Object> body = validReader();
        body.remove("phone");

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").isEmpty());
    }

    static Stream<Arguments> invalidFieldValues() {
        return Stream.of(
                Arguments.of("puste imię", "firstName", "", "firstName"),
                Arguments.of("brak imienia", "firstName", null, "firstName"),
                Arguments.of("nazwisko z samych spacji", "lastName", "   ", "lastName"),
                Arguments.of("imię dłuższe niż 100 znaków", "firstName", "a".repeat(101), "firstName"),
                Arguments.of("nazwisko dłuższe niż 100 znaków", "lastName", "a".repeat(101), "lastName"),
                Arguments.of("brak e-maila", "email", null, "email"),
                Arguments.of("e-mail bez małpy", "email", "anna.example.com", "email"),
                Arguments.of("e-mail ze spacją", "email", "an na@example.com", "email"),
                Arguments.of("e-mail dłuższy niż 255 znaków", "email", "a".repeat(251) + "@b.pl", "email"),
                Arguments.of("za krótki telefon", "phone", "12345", "phone"),
                Arguments.of("telefon z literami", "phone", "12345abc678", "phone"));
    }

    /**
     * Niepoprawne pole daje 400 w formacie Problem Details ze wskazaniem błędnego pola i nic nie zapisuje.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFieldValues")
    void shouldRejectInvalidField(String caseName, String field, Object value, String expectedField) throws Exception {
        Map<String, Object> body = validReader();
        body.put(field, value);

        MvcResult result = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertThat(bodyOf(result)).contains("\"field\":\"" + expectedField + "\"");
        assertThat(readerRepository.count()).isZero();
    }

    /**
     * Ten sam adres e-mail zapisany inaczej wielkością liter daje 409.
     */
    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        createReader(validReader());
        Map<String, Object> duplicate = validReader();
        duplicate.put("firstName", "Inna");
        duplicate.put("email", "ANNA.NOWAK@EXAMPLE.COM");

        MvcResult result = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertThat(bodyOf(result)).contains("już istnieje");
        assertThat(readerRepository.count()).isEqualTo(1);
    }

    /**
     * Uszkodzony JSON daje 400.
     */
    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{ to nie jest json"))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /{id} ----

    /**
     * Odczyt po id zwraca wszystkie pola czytelnika.
     */
    @Test
    void shouldReturnReaderById() throws Exception {
        Reader reader = saveReader("Anna", "Nowak", "anna.nowak@example.com");

        mockMvc.perform(get(BASE_URL + "/" + reader.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reader.getId()))
                .andExpect(jsonPath("$.lastName").value("Nowak"))
                .andExpect(jsonPath("$.cardNumber").value(reader.getCardNumber()))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Nieistniejące id daje 404 w formacie Problem Details, a id niebędące liczbą daje 400.
     */
    @Test
    void shouldReturn404ForMissingReaderAnd400ForBadId() throws Exception {
        mockMvc.perform(get(BASE_URL + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get(BASE_URL + "/abc")).andExpect(status().isBadRequest());
    }

    // ---- GET lista ----

    /**
     * Domyślna lista jest posortowana po nazwisku, ma domyślny rozmiar strony 20 i metadane strony.
     */
    @Test
    void shouldListReadersSortedByLastName() throws Exception {
        seedReaders();

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].lastName").value("Adamska"))
                .andExpect(jsonPath("$.content[1].lastName").value("Kowalski"))
                .andExpect(jsonPath("$.content[2].lastName").value("Nowak"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    /**
     * Paginacja i sortowanie po innym polu działają zgodnie z parametrami.
     */
    @Test
    void shouldPageAndSortList() throws Exception {
        seedReaders();

        mockMvc.perform(get(BASE_URL).param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Nowak"))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(BASE_URL).param("sort", "email,desc"))
                .andExpect(jsonPath("$.content[0].email").value("jan.kowalski@example.com"))
                .andExpect(jsonPath("$.content[2].email").value("anna.nowak@example.com"));
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
     * Wyszukiwanie obejmuje imię, nazwisko, "imię nazwisko", e-mail i numer karty, bez rozróżniania wielkości liter.
     */
    @Test
    void shouldSearchByNameEmailAndCardNumber() throws Exception {
        seedReaders();
        Reader jan = readerRepository.findByEmail("jan.kowalski@example.com").orElseThrow();

        mockMvc.perform(get(BASE_URL).param("search", "KOWAL"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Jan"));

        mockMvc.perform(get(BASE_URL).param("search", "anna nowak"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Nowak"));

        mockMvc.perform(get(BASE_URL).param("search", "EXAMPLE.ORG"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Adamska"));

        mockMvc.perform(get(BASE_URL).param("search", jan.getCardNumber().toLowerCase()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jan.getId()));

        mockMvc.perform(get(BASE_URL).param("search", "LB-"))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get(BASE_URL).param("search", "nie ma takiej osoby"))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    /**
     * Znak % w wyszukiwaniu jest traktowany dosłownie, więc nie dopasowuje wszystkiego.
     */
    @Test
    void shouldTreatPercentSignLiterally() throws Exception {
        seedReaders();

        mockMvc.perform(get(BASE_URL).param("search", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * Filtr aktywności zawęża listę, a niepoprawna wartość daje 400.
     */
    @Test
    void shouldFilterByActiveStatus() throws Exception {
        seedReaders();
        Reader jan = readerRepository.findByEmail("jan.kowalski@example.com").orElseThrow();
        jan.setActive(false);
        readerRepository.save(jan);

        mockMvc.perform(get(BASE_URL).param("active", "true"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(BASE_URL).param("active", "false"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Jan"));
        mockMvc.perform(get(BASE_URL).param("active", "kto wie"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Sortowanie po niedozwolonym polu daje 400 z opisem dozwolonych pól.
     */
    @Test
    void shouldRejectSortingByDisallowedProperty() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE_URL).param("sort", "version,asc"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result)).contains("Niedozwolone pole sortowania");
    }

    // ---- PUT ----

    /**
     * Aktualizacja z aktualną wersją zmienia dane, dezaktywuje czytelnika i zwiększa wersję,
     * a numer karty i data rejestracji zostają bez zmian.
     */
    @Test
    void shouldUpdateAndDeactivateReader() throws Exception {
        Reader saved = saveReader("Anna", "Nowak", "anna.nowak@example.com");

        mockMvc.perform(put(BASE_URL + "/" + saved.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateBody(false, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Kowalska"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.cardNumber").value(saved.getCardNumber()));

        Reader reloaded = readerRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getRegisteredAt()).isEqualTo(saved.getRegisteredAt());
    }

    /**
     * Własny e-mail zapisany inaczej wielkością liter nie jest duplikatem, a brak telefonu usuwa numer.
     */
    @Test
    void shouldAllowKeepingOwnEmailAndRemovingPhone() throws Exception {
        long id = createReader(validReader());
        Map<String, Object> body = updateBody(true, 0);
        body.put("email", "ANNA.NOWAK@EXAMPLE.COM");
        body.remove("phone");

        mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna.nowak@example.com"))
                .andExpect(jsonPath("$.phone").isEmpty());
    }

    /**
     * Drugi zapis ze starą wersją (równoczesna edycja) daje 409 i nie nadpisuje zmian pierwszego.
     */
    @Test
    void shouldRejectStaleVersion() throws Exception {
        long id = createReader(validReader());
        assertThat(putReader(id, updateBody(true, 0)).getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> stale = updateBody(true, 0);
        stale.put("lastName", "Zmiana drugiego użytkownika");
        MvcResult result = mockMvc.perform(
                        put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(stale)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertThat(bodyOf(result)).contains("zmieniony");
        assertThat(readerRepository.findById(id).orElseThrow().getLastName()).isEqualTo("Kowalska");
    }

    /**
     * Aktualizacja bez wersji lub bez statusu daje 400, a z e-mailem innego czytelnika daje 409.
     */
    @Test
    void shouldRejectMissingVersionMissingActiveAndTakenEmail() throws Exception {
        long id = createReader(validReader());
        Map<String, Object> other = validReader();
        other.put("email", "jan.kowalski@example.com");
        createReader(other);

        Map<String, Object> withoutVersion = updateBody(true, 0);
        withoutVersion.remove("version");
        assertThat(bodyOf(mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json(withoutVersion)))
                .andExpect(status().isBadRequest()).andReturn())).contains("\"field\":\"version\"");

        Map<String, Object> withoutActive = updateBody(true, 0);
        withoutActive.remove("active");
        assertThat(bodyOf(mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json(withoutActive)))
                .andExpect(status().isBadRequest()).andReturn())).contains("\"field\":\"active\"");

        Map<String, Object> takenEmail = updateBody(true, 0);
        takenEmail.put("email", "Jan.Kowalski@example.com");
        mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(takenEmail)))
                .andExpect(status().isConflict());
    }

    /**
     * Aktualizacja nieistniejącego czytelnika daje 404.
     */
    @Test
    void shouldReturn404WhenUpdatingMissingReader() throws Exception {
        mockMvc.perform(put(BASE_URL + "/999999").contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateBody(true, 0))))
                .andExpect(status().isNotFound());
    }

    // ---- DELETE ----

    /**
     * Czytelnik jest usuwany (204), a potem nie istnieje (404).
     */
    @Test
    void shouldDeleteReader() throws Exception {
        long id = createReader(validReader());

        mockMvc.perform(delete(BASE_URL + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE_URL + "/" + id)).andExpect(status().isNotFound());
        assertThat(readerRepository.count()).isZero();
    }

    /**
     * Usuwanie nieistniejącego czytelnika daje 404.
     */
    @Test
    void shouldReturn404WhenDeletingMissingReader() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/999999")).andExpect(status().isNotFound());
    }
}