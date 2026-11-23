package pl.tajko.library.api.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * Testy integracyjne API egzemplarzy: żądania HTTP przez MockMvc do pełnego kontekstu Springa
 * z prawdziwą bazą PostgreSQL.
 */
@WithMockUser(roles = "ADMIN")
class BookItemControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BOOKS_URL = "/api/v1/books";
    private static final String ISBN_CLEAN_CODE = "9780132350884";
    private static final String ISBN_WITCHER = "9780306406157";

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

    private Book saveBook(String title, String isbn) {
        return bookRepository.save(new Book(title, "Autor", 2000, isbn, Genre.OTHER));
    }

    private static String itemsUrl(Long bookId) {
        return BOOKS_URL + "/" + bookId + "/items";
    }

    private static String countBody(int count) {
        return "{\"count\": " + count + "}";
    }

    private void postItems(Long bookId, int count) throws Exception {
        mockMvc.perform(post(itemsUrl(bookId)).contentType(MediaType.APPLICATION_JSON).content(countBody(count)))
                .andExpect(status().isCreated());
    }

    // ---- POST ----

    /**
     * Poprawne żądanie tworzy podaną liczbę egzemplarzy: 201, nagłówek Location z adresem listy
     * i lista sztuk z identyfikatorem książki.
     */
    @Test
    void shouldAddItems() throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);

        MvcResult result = mockMvc.perform(
                        post(itemsUrl(book.getId())).contentType(MediaType.APPLICATION_JSON).content(countBody(3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].bookId").value(book.getId()))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].version").value(0))
                .andReturn();

        assertThat(result.getResponse().getHeader("Location")).endsWith(itemsUrl(book.getId()));
        assertThat(bookItemRepository.countByBookId(book.getId())).isEqualTo(3);
    }

    /**
     * Kolejne wywołania dodają kolejne sztuki, a liczba egzemplarzy widoczna przy książce rośnie.
     */
    @Test
    void shouldAccumulateItemsAcrossRequests() throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);

        postItems(book.getId(), 2);
        postItems(book.getId(), 3);

        assertThat(bookItemRepository.countByBookId(book.getId())).isEqualTo(5);
        mockMvc.perform(get(BOOKS_URL + "/" + book.getId()))
                .andExpect(jsonPath("$.copiesCount").value(5));
    }

    /**
     * Granica limitu (100 sztuk naraz) jest przyjmowana.
     */
    @Test
    void shouldAcceptMaximumCountPerRequest() throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);

        postItems(book.getId(), BookItemService.MAX_COPIES_PER_REQUEST);

        assertThat(bookItemRepository.countByBookId(book.getId()))
                .isEqualTo(BookItemService.MAX_COPIES_PER_REQUEST);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("zero", "{\"count\": 0}"),
                Arguments.of("liczba ujemna", "{\"count\": -5}"),
                Arguments.of("powyżej limitu", "{\"count\": 101}"),
                Arguments.of("brak pola", "{}"),
                Arguments.of("wartość null", "{\"count\": null}"),
                Arguments.of("tekst zamiast liczby", "{\"count\": \"abc\"}"),
                Arguments.of("uszkodzony JSON", "{ to nie jest json"));
    }

    /**
     * Niepoprawne ciało żądania daje 400 w formacie Problem Details i nic nie zapisuje.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void shouldRejectInvalidBody(String caseName, String body) throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);

        mockMvc.perform(post(itemsUrl(book.getId())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(bookItemRepository.countByBookId(book.getId())).isZero();
    }

    /**
     * Dodawanie do nieistniejącej książki daje 404, a identyfikator niebędący liczbą daje 400.
     */
    @Test
    void shouldRejectAddingToMissingBookOrBadId() throws Exception {
        mockMvc.perform(post(itemsUrl(999999L)).contentType(MediaType.APPLICATION_JSON).content(countBody(1)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post(BOOKS_URL + "/abc/items").contentType(MediaType.APPLICATION_JSON).content(countBody(1)))
                .andExpect(status().isBadRequest());
        assertThat(bookItemRepository.count()).isZero();
    }

    // ---- GET ----

    /**
     * Lista zawiera tylko egzemplarze danej książki, posortowane rosnąco po identyfikatorze.
     */
    @Test
    void shouldListOnlyItemsOfGivenBook() throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);
        Book other = saveBook("Wiedźmin", ISBN_WITCHER);
        BookItem first = bookItemRepository.save(new BookItem(book));
        BookItem second = bookItemRepository.save(new BookItem(book));
        bookItemRepository.save(new BookItem(other));

        mockMvc.perform(get(itemsUrl(book.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[0].bookId").value(book.getId()))
                .andExpect(jsonPath("$[1].bookId").value(book.getId()));
    }

    /**
     * Książka bez egzemplarzy daje 200 i pustą listę.
     */
    @Test
    void shouldReturnEmptyListForBookWithoutItems() throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);

        mockMvc.perform(get(itemsUrl(book.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * Lista egzemplarzy nieistniejącej książki daje 404.
     */
    @Test
    void shouldReturn404WhenListingItemsOfMissingBook() throws Exception {
        mockMvc.perform(get(itemsUrl(999999L)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---- DELETE ----

    /**
     * Usunięcie egzemplarza daje 204, znika z listy, a liczba egzemplarzy przy książce maleje.
     */
    @Test
    void shouldDeleteItem() throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);
        BookItem first = bookItemRepository.save(new BookItem(book));
        BookItem second = bookItemRepository.save(new BookItem(book));

        mockMvc.perform(delete(itemsUrl(book.getId()) + "/" + first.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(itemsUrl(book.getId())))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(second.getId()));
        mockMvc.perform(get(BOOKS_URL + "/" + book.getId()))
                .andExpect(jsonPath("$.copiesCount").value(1));
    }

    /**
     * Egzemplarz innej książki nie może zostać usunięty przez adres innej książki (404),
     * a nieistniejący egzemplarz i nieistniejąca książka też dają 404.
     */
    @Test
    void shouldReturn404ForItemOfAnotherBookMissingItemAndMissingBook() throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);
        Book other = saveBook("Wiedźmin", ISBN_WITCHER);
        BookItem otherItem = bookItemRepository.save(new BookItem(other));

        mockMvc.perform(delete(itemsUrl(book.getId()) + "/" + otherItem.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(bookItemRepository.findById(otherItem.getId())).isPresent();

        mockMvc.perform(delete(itemsUrl(book.getId()) + "/999999")).andExpect(status().isNotFound());
        mockMvc.perform(delete(itemsUrl(999999L) + "/" + otherItem.getId())).andExpect(status().isNotFound());
    }

    // ---- współpraca z usuwaniem książki ----

    /**
     * Książki z egzemplarzami nie da się usunąć (409), ale po usunięciu wszystkich egzemplarzy już tak (204).
     */
    @Test
    void shouldAllowDeletingBookOnlyAfterRemovingItsItems() throws Exception {
        Book book = saveBook("Czysty kod", ISBN_CLEAN_CODE);
        BookItem item = bookItemRepository.save(new BookItem(book));

        mockMvc.perform(delete(BOOKS_URL + "/" + book.getId())).andExpect(status().isConflict());

        mockMvc.perform(delete(itemsUrl(book.getId()) + "/" + item.getId())).andExpect(status().isNoContent());
        mockMvc.perform(delete(BOOKS_URL + "/" + book.getId())).andExpect(status().isNoContent());
        assertThat(bookRepository.findById(book.getId())).isEmpty();
    }
}