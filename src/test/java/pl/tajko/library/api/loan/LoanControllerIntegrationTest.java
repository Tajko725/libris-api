package pl.tajko.library.api.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pl.tajko.library.api.AbstractIntegrationTest;
import pl.tajko.library.api.book.Book;
import pl.tajko.library.api.book.BookItem;
import pl.tajko.library.api.book.BookItemRepository;
import pl.tajko.library.api.book.BookRepository;
import pl.tajko.library.api.book.Genre;
import pl.tajko.library.api.reader.Reader;
import pl.tajko.library.api.reader.ReaderRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testy integracyjne API wypożyczeń: żądania HTTP przez MockMvc do pełnego kontekstu Springa z prawdziwą
 * bazą PostgreSQL. Testy nie są transakcyjne (żądania działają we własnych transakcjach), więc po każdym
 * teście czyszczą wszystkie tabele, wypożyczenia jako pierwsze. Inaczej klucze obce zablokowałyby
 * czyszczenie w innych klasach testowych.
 */
class LoanControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/v1/loans";
    private static final String ISBN_CLEAN_CODE = "9780132350884";
    private static final String ISBN_OTHER = "9780306406157";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BookItemRepository bookItemRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReaderRepository readerRepository;

    private MockMvc mockMvc;
    private Book book;
    private Reader anna;

    /** Tworzy MockMvc, czyści tabele i przygotowuje książkę oraz aktywnego czytelnika. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        cleanUp();
        book = bookRepository.save(new Book("Czysty kod", "Robert C. Martin", 2008, ISBN_CLEAN_CODE, Genre.SCIENCE));
        anna = newReader("Anna", "anna.nowak@example.com", true);
    }

    /** Sprząta po teście, żeby wypożyczenia nie blokowały czyszczenia tabel w innych klasach testowych. */
    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        loanRepository.deleteAllInBatch();
        bookItemRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        readerRepository.deleteAllInBatch();
    }

    // ---- pomocnicze ----

    private BookItem newItem() {
        return newItem(book);
    }

    private BookItem newItem(Book owner) {
        return bookItemRepository.save(new BookItem(owner));
    }

    private Reader newReader(String firstName, String email, boolean active) {
        Reader reader = new Reader(firstName, "Kowalska", email, null);
        reader.setActive(active);
        return readerRepository.save(reader);
    }

    /** Zapisuje wypożyczenie z przeszłości: wypożyczone {@code borrowedDaysAgo} dni temu, termin za {@code dueInDays} dni. */
    private Loan pastLoan(BookItem item, Reader reader, int borrowedDaysAgo, int dueInDays) {
        return loanRepository.save(new Loan(item, reader,
                Instant.now().minus(borrowedDaysAgo, ChronoUnit.DAYS),
                LocalDate.now(ZoneOffset.UTC).plusDays(dueInDays)));
    }

    private static String json(Map<String, Object> body) {
        return JSON.writeValueAsString(body);
    }

    private static Map<String, Object> borrowBody(Object itemId, Object readerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookItemId", itemId);
        body.put("readerId", readerId);
        return body;
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcResult postBorrow(Object itemId, Object readerId) throws Exception {
        return mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(borrowBody(itemId, readerId))))
                .andReturn();
    }

    private long borrow(BookItem item, Reader reader) throws Exception {
        MvcResult result = postBorrow(item.getId(), reader.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
    }

    private void giveBack(long loanId) throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + loanId + "/return")).andExpect(status().isOk());
    }

    // ---- POST: wypożyczanie ----

    /**
     * Poprawne żądanie wypożycza egzemplarz: 201, nagłówek Location, stan ACTIVE, termin za 30 dni
     * oraz podsumowanie czytelnika i książki.
     */
    @Test
    void shouldBorrowItem() throws Exception {
        BookItem item = newItem();
        LocalDate before = LocalDate.now(ZoneOffset.UTC);

        MvcResult result = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(borrowBody(item.getId(), anna.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reader.id").value(anna.getId()))
                .andExpect(jsonPath("$.reader.firstName").value("Anna"))
                .andExpect(jsonPath("$.reader.cardNumber").value(matchesPattern("LB-\\d{6}")))
                .andExpect(jsonPath("$.book.id").value(book.getId()))
                .andExpect(jsonPath("$.book.title").value("Czysty kod"))
                .andExpect(jsonPath("$.book.bookItemId").value(item.getId()))
                .andExpect(jsonPath("$.borrowedAt").exists())
                .andExpect(jsonPath("$.returnedAt").doesNotExist())
                .andExpect(jsonPath("$.renewals").value(0))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        LocalDate after = LocalDate.now(ZoneOffset.UTC);

        LocalDate dueDate = LocalDate.parse(JsonPath.read(bodyOf(result), "$.dueDate"));
        assertThat(dueDate).isBetween(before.plusDays(30), after.plusDays(30));
        Number id = JsonPath.read(bodyOf(result), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith(BASE_URL + "/" + id);
        assertThat(loanRepository.count()).isEqualTo(1);
    }

    /**
     * Ten sam egzemplarz nie może być wypożyczony drugi raz, dopóki nie wróci: 409 i nadal jedno wypożyczenie.
     */
    @Test
    void shouldRejectBorrowingItemThatIsAlreadyBorrowed() throws Exception {
        BookItem item = newItem();
        borrow(item, anna);
        Reader other = newReader("Ewa", "ewa.adamska@example.org", true);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(borrowBody(item.getId(), other.getId()))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("już wypożyczony")));

        assertThat(loanRepository.count()).isEqualTo(1);
    }

    /**
     * Wypożyczenie nieistniejącego egzemplarza albo przez nieistniejącego czytelnika to 404.
     */
    @Test
    void shouldReturnNotFoundForMissingItemOrReader() throws Exception {
        BookItem item = newItem();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(borrowBody(999_999, anna.getId()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(borrowBody(item.getId(), 999_999))))
                .andExpect(status().isNotFound());

        assertThat(loanRepository.count()).isZero();
    }

    /**
     * Brakujące albo niedodatnie identyfikatory to 400 z listą błędnych pól, a niepoprawny JSON to 400.
     */
    @Test
    void shouldRejectInvalidBorrowRequests() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field=='bookItemId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='readerId')]").exists());

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(borrowBody(-1, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{nie json"))
                .andExpect(status().isBadRequest());

        assertThat(loanRepository.count()).isZero();
    }

    /**
     * Nieaktywny czytelnik nie może wypożyczać: 409.
     */
    @Test
    void shouldRejectBorrowingByInactiveReader() throws Exception {
        Reader inactive = newReader("Nieaktywna", "nieaktywny@example.com", false);

        MvcResult result = postBorrow(newItem().getId(), inactive.getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(loanRepository.count()).isZero();
    }

    /**
     * Czytelnik może mieć najwyżej 5 niezwróconych książek: szósta to 409, a po zwrocie jednej można znów wypożyczyć.
     */
    @Test
    void shouldEnforceLoanLimitPerReader() throws Exception {
        long firstLoanId = 0;
        for (int i = 0; i < 5; i++) {
            long loanId = borrow(newItem(), anna);
            if (i == 0) {
                firstLoanId = loanId;
            }
        }

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(borrowBody(newItem().getId(), anna.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("limit")));
        assertThat(loanRepository.count()).isEqualTo(5);

        giveBack(firstLoanId);

        assertThat(postBorrow(newItem().getId(), anna.getId()).getResponse().getStatus()).isEqualTo(201);
    }

    /**
     * Limit dotyczy jednego czytelnika: inny czytelnik nadal może wypożyczać.
     */
    @Test
    void shouldNotApplyOneReadersLimitToAnotherReader() throws Exception {
        for (int i = 0; i < 5; i++) {
            borrow(newItem(), anna);
        }
        Reader other = newReader("Ewa", "ewa.adamska@example.org", true);

        assertThat(postBorrow(newItem().getId(), other.getId()).getResponse().getStatus()).isEqualTo(201);
    }

    /**
     * Czytelnik z książką po terminie nie może wypożyczać (409), a inny czytelnik tak.
     */
    @Test
    void shouldRejectBorrowingWhenReaderHasOverdueLoan() throws Exception {
        pastLoan(newItem(), anna, 40, -10);
        Reader other = newReader("Ewa", "ewa.adamska@example.org", true);

        MvcResult rejected = postBorrow(newItem().getId(), anna.getId());
        MvcResult accepted = postBorrow(newItem().getId(), other.getId());

        assertThat(rejected.getResponse().getStatus()).isEqualTo(409);
        assertThat(bodyOf(rejected)).contains("po terminie");
        assertThat(accepted.getResponse().getStatus()).isEqualTo(201);
    }

    // ---- POST: zwrot ----

    /**
     * Zwrot kończy wypożyczenie (stan RETURNED, data zwrotu), a egzemplarz można od razu wypożyczyć ponownie.
     */
    @Test
    void shouldReturnLoanAndAllowBorrowingItemAgain() throws Exception {
        BookItem item = newItem();
        long loanId = borrow(item, anna);

        mockMvc.perform(post(BASE_URL + "/" + loanId + "/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.returnedAt").exists())
                .andExpect(jsonPath("$.version").value(1));

        Reader other = newReader("Ewa", "ewa.adamska@example.org", true);
        assertThat(postBorrow(item.getId(), other.getId()).getResponse().getStatus()).isEqualTo(201);
        assertThat(loanRepository.count()).isEqualTo(2);
    }

    /**
     * Przeterminowane wypożyczenie można zwrócić, a po zwrocie czytelnik znów może wypożyczać.
     */
    @Test
    void shouldReturnOverdueLoanAndUnblockReader() throws Exception {
        Loan overdue = pastLoan(newItem(), anna, 40, -10);

        giveBack(overdue.getId());

        assertThat(postBorrow(newItem().getId(), anna.getId()).getResponse().getStatus()).isEqualTo(201);
    }

    /**
     * Drugi zwrot tego samego wypożyczenia to 409, a nieistniejące wypożyczenie to 404.
     */
    @Test
    void shouldRejectSecondReturnAndReportMissingLoan() throws Exception {
        long loanId = borrow(newItem(), anna);
        giveBack(loanId);

        mockMvc.perform(post(BASE_URL + "/" + loanId + "/return")).andExpect(status().isConflict());
        mockMvc.perform(post(BASE_URL + "/999999/return")).andExpect(status().isNotFound());
        mockMvc.perform(post(BASE_URL + "/abc/return")).andExpect(status().isBadRequest());
    }

    // ---- POST: przedłużanie ----

    /**
     * Przedłużenie przesuwa termin o 14 dni i zwiększa licznik, a drugie przedłużenie to 409.
     */
    @Test
    void shouldRenewOnceAndRejectSecondRenewal() throws Exception {
        long loanId = borrow(newItem(), anna);
        LocalDate originalDue = loanRepository.findById(loanId).orElseThrow().getDueDate();

        mockMvc.perform(post(BASE_URL + "/" + loanId + "/renew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renewals").value(1))
                .andExpect(jsonPath("$.dueDate").value(originalDue.plusDays(14).toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post(BASE_URL + "/" + loanId + "/renew"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("już przedłużone")));
        assertThat(loanRepository.findById(loanId).orElseThrow().getDueDate()).isEqualTo(originalDue.plusDays(14));
    }

    /**
     * Po terminie zwrotu przedłużenie jest zabronione (409), a termin się nie zmienia.
     */
    @Test
    void shouldRejectRenewalAfterDueDate() throws Exception {
        Loan overdue = pastLoan(newItem(), anna, 40, -1);

        mockMvc.perform(post(BASE_URL + "/" + overdue.getId() + "/renew"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("po terminie")));
        assertThat(loanRepository.findById(overdue.getId()).orElseThrow().getRenewals()).isZero();
    }

    /**
     * Zwróconego wypożyczenia nie można przedłużyć (409), a nieistniejące wypożyczenie to 404.
     */
    @Test
    void shouldRejectRenewalOfReturnedAndMissingLoan() throws Exception {
        long loanId = borrow(newItem(), anna);
        giveBack(loanId);

        mockMvc.perform(post(BASE_URL + "/" + loanId + "/renew")).andExpect(status().isConflict());
        mockMvc.perform(post(BASE_URL + "/999999/renew")).andExpect(status().isNotFound());
    }

    // ---- GET: pojedyncze wypożyczenie ----

    /**
     * Odczyt zwraca wypożyczenie, stan przeterminowanego to OVERDUE, nieistniejące to 404, a zły id to 400.
     */
    @Test
    void shouldGetLoanById() throws Exception {
        Loan overdue = pastLoan(newItem(), anna, 40, -10);

        mockMvc.perform(get(BASE_URL + "/" + overdue.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(overdue.getId()))
                .andExpect(jsonPath("$.status").value("OVERDUE"))
                .andExpect(jsonPath("$.reader.id").value(anna.getId()))
                .andExpect(jsonPath("$.book.title").value("Czysty kod"));

        mockMvc.perform(get(BASE_URL + "/999999")).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE_URL + "/abc")).andExpect(status().isBadRequest());
    }

    // ---- GET: lista z filtrami ----

    /** Tworzy cztery wypożyczenia: dwa aktywne (L1, L4), jedno zwrócone (L2) i jedno przeterminowane (L3). */
    private void seedLoans(Book otherBook) {
        Reader ewa = newReader("Ewa", "ewa.adamska@example.org", true);
        pastLoan(newItem(), anna, 5, 25);                                   // L1: Anna, aktywne
        Loan returned = new Loan(newItem(), anna,
                Instant.now().minus(10, ChronoUnit.DAYS), LocalDate.now(ZoneOffset.UTC).plusDays(20));
        returned.markReturned(Instant.now().minus(5, ChronoUnit.DAYS));
        loanRepository.save(returned);                                       // L2: Anna, zwrócone
        pastLoan(newItem(), ewa, 40, -10);                                   // L3: Ewa, po terminie
        pastLoan(newItem(otherBook), anna, 1, 29);                           // L4: Anna, inna książka, aktywne
    }

    /**
     * Bez filtrów lista zawiera całą historię (także zwrócone), od najnowszych, z metadanymi strony.
     */
    @Test
    void shouldListAllLoansNewestFirst() throws Exception {
        Book other = bookRepository.save(new Book("Wiedźmin", "Andrzej Sapkowski", 1993, ISBN_OTHER, Genre.FANTASY));
        seedLoans(other);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.content[0].book.title").value("Wiedźmin"))
                .andExpect(jsonPath("$.content[3].status").value("OVERDUE"));
    }

    /**
     * Filtry po czytelniku, książce i stanie zawężają wyniki, a łączone działają razem.
     */
    @Test
    void shouldFilterLoansByReaderBookAndStatus() throws Exception {
        Book other = bookRepository.save(new Book("Wiedźmin", "Andrzej Sapkowski", 1993, ISBN_OTHER, Genre.FANTASY));
        seedLoans(other);

        mockMvc.perform(get(BASE_URL).param("readerId", String.valueOf(anna.getId())))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(BASE_URL).param("bookId", String.valueOf(other.getId())))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].book.title").value("Wiedźmin"));
        mockMvc.perform(get(BASE_URL).param("status", "ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(BASE_URL).param("status", "OVERDUE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reader.firstName").value("Ewa"));
        mockMvc.perform(get(BASE_URL).param("status", "RETURNED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].returnedAt").exists());
        mockMvc.perform(get(BASE_URL)
                        .param("readerId", String.valueOf(anna.getId()))
                        .param("status", "ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(BASE_URL)
                        .param("readerId", String.valueOf(anna.getId()))
                        .param("bookId", String.valueOf(other.getId()))
                        .param("status", "RETURNED"))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    /**
     * Filtr po nieistniejącym czytelniku daje pustą stronę (200), a nie błąd.
     */
    @Test
    void shouldReturnEmptyPageForUnknownReader() throws Exception {
        mockMvc.perform(get(BASE_URL).param("readerId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    /**
     * Stronicowanie i sortowanie po terminie zwrotu działają, a niedozwolone pole sortowania i nieznany status to 400.
     */
    @Test
    void shouldPageSortAndValidateListParameters() throws Exception {
        Book other = bookRepository.save(new Book("Wiedźmin", "Andrzej Sapkowski", 1993, ISBN_OTHER, Genre.FANTASY));
        seedLoans(other);

        mockMvc.perform(get(BASE_URL).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(BASE_URL).param("sort", "dueDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("OVERDUE"));
        mockMvc.perform(get(BASE_URL).param("sort", "version"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE_URL).param("status", "NIEZNANY"))
                .andExpect(status().isBadRequest());
    }

    // ---- historia ----

    /**
     * Po zwrocie wypożyczenie zostaje w historii, a ponowne wypożyczenie tego egzemplarza tworzy nowy wpis.
     */
    @Test
    void shouldKeepReturnedLoanInHistory() throws Exception {
        BookItem item = newItem();
        long first = borrow(item, anna);
        giveBack(first);
        Reader other = newReader("Ewa", "ewa.adamska@example.org", true);
        borrow(item, other);

        mockMvc.perform(get(BASE_URL).param("bookId", String.valueOf(book.getId())))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(BASE_URL).param("status", "RETURNED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
        mockMvc.perform(get(BASE_URL).param("status", "ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---- ochrona historii przed usunięciem ----

    /**
     * Egzemplarza z historią wypożyczeń (nawet zakończonych) nie da się usunąć: 409 z czytelnym komunikatem,
     * a egzemplarz zostaje. Egzemplarz bez wypożyczeń nadal można usunąć.
     */
    @Test
    void shouldNotDeleteItemWithLoanHistory() throws Exception {
        BookItem borrowed = newItem();
        BookItem neverBorrowed = newItem();
        giveBack(borrow(borrowed, anna));

        mockMvc.perform(delete("/api/v1/books/" + book.getId() + "/items/" + borrowed.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("historię wypożyczeń")));
        mockMvc.perform(delete("/api/v1/books/" + book.getId() + "/items/" + neverBorrowed.getId()))
                .andExpect(status().isNoContent());

        assertThat(bookItemRepository.existsById(borrowed.getId())).isTrue();
        assertThat(bookItemRepository.existsById(neverBorrowed.getId())).isFalse();
    }

    /**
     * Czytelnika z historią wypożyczeń (nawet zakończonych) nie da się usunąć: 409 z czytelnym komunikatem.
     * Czytelnik bez wypożyczeń nadal można usunąć.
     */
    @Test
    void shouldNotDeleteReaderWithLoanHistory() throws Exception {
        Reader idle = newReader("Ewa", "ewa.adamska@example.org", true);
        giveBack(borrow(newItem(), anna));

        mockMvc.perform(delete("/api/v1/readers/" + anna.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("historią wypożyczeń")));
        mockMvc.perform(delete("/api/v1/readers/" + idle.getId()))
                .andExpect(status().isNoContent());

        assertThat(readerRepository.existsById(anna.getId())).isTrue();
        assertThat(readerRepository.existsById(idle.getId())).isFalse();
    }

    // ---- dostępność egzemplarzy w książkach ----

    /**
     * Odpowiedź o książce pokazuje liczbę dostępnych egzemplarzy: wypożyczenie ją zmniejsza, zwrot zwiększa.
     */
    @Test
    void shouldShowAvailableCopiesOfBook() throws Exception {
        BookItem first = newItem();
        newItem();
        newItem();
        String url = "/api/v1/books/" + book.getId();

        mockMvc.perform(get(url))
                .andExpect(jsonPath("$.copiesCount").value(3))
                .andExpect(jsonPath("$.availableCopies").value(3));

        long loanId = borrow(first, anna);
        mockMvc.perform(get(url))
                .andExpect(jsonPath("$.copiesCount").value(3))
                .andExpect(jsonPath("$.availableCopies").value(2));

        giveBack(loanId);
        mockMvc.perform(get(url))
                .andExpect(jsonPath("$.availableCopies").value(3));
    }

    /**
     * Filtr available na liście książek: true zwraca książki z wolnym egzemplarzem, false pozostałe
     * (w tym bez egzemplarzy), a bez filtra wszystkie.
     */
    @Test
    void shouldFilterBooksByAvailability() throws Exception {
        bookRepository.save(new Book("Wiedźmin", "Andrzej Sapkowski", 1993, ISBN_OTHER, Genre.FANTASY));
        BookItem first = newItem();
        BookItem second = newItem();

        mockMvc.perform(get("/api/v1/books").param("available", "true"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Czysty kod"));
        mockMvc.perform(get("/api/v1/books").param("available", "false"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Wiedźmin"));

        long firstLoan = borrow(first, anna);
        borrow(second, anna);

        mockMvc.perform(get("/api/v1/books").param("available", "true"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/books").param("available", "false"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(jsonPath("$.totalElements").value(2));

        giveBack(firstLoan);

        mockMvc.perform(get("/api/v1/books").param("available", "true"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].availableCopies").value(1));
    }
}
