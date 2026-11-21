package pl.tajko.library.api.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.PageResponse;
import pl.tajko.library.api.common.ResourceNotFoundException;

/**
 * Testy jednostkowe logiki {@link ReaderService} z zamockowanym repozytorium.
 */
@ExtendWith(MockitoExtension.class)
class ReaderServiceTest {

    private static final String EMAIL = "anna.nowak@example.com";

    @Mock
    private ReaderRepository readerRepository;

    @InjectMocks
    private ReaderService readerService;

    private static Reader readerWithId(long id, String email) {
        Reader reader = new Reader("Anna", "Nowak", email, "+48 123 456 789");
        ReflectionTestUtils.setField(reader, "id", id);
        return reader;
    }

    private static ReaderUpdateRequest updateRequest(String email, boolean active, long version) {
        return new ReaderUpdateRequest("Ania", "Kowalska", email, null, active, version);
    }

    // ---- create ----

    /**
     * Nowy czytelnik jest zapisywany jako aktywny, z przyciętymi danymi i e-mailem małymi literami.
     */
    @Test
    void shouldCreateActiveReaderWithNormalizedEmail() {
        when(readerRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(readerRepository.save(any(Reader.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReaderResponse response = readerService.create(
                new ReaderRequest("  Anna ", "Nowak", "Anna.Nowak@Example.COM", null));

        assertThat(response.firstName()).isEqualTo("Anna");
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.phone()).isNull();
        assertThat(response.active()).isTrue();
        assertThat(response.version()).isZero();
        verify(readerRepository).save(any(Reader.class));
    }

    /**
     * Czytelnik z istniejącym adresem e-mail (także zapisanym inaczej wielkością liter) nie jest zapisywany.
     */
    @Test
    void shouldRejectCreatingReaderWithExistingEmail() {
        when(readerRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> readerService.create(new ReaderRequest("Anna", "Nowak", "ANNA.NOWAK@example.com", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("e-mail");
        verify(readerRepository, never()).save(any());
    }

    // ---- get ----

    /**
     * Odczyt zwraca czytelnika o podanym identyfikatorze.
     */
    @Test
    void shouldReturnReaderById() {
        when(readerRepository.findById(5L)).thenReturn(Optional.of(readerWithId(5L, EMAIL)));

        ReaderResponse response = readerService.get(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.email()).isEqualTo(EMAIL);
    }

    /**
     * Odczyt nieistniejącego czytelnika zgłasza wyjątek "nie znaleziono".
     */
    @Test
    void shouldFailWhenReaderIsMissing() {
        when(readerRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> readerService.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    // ---- update ----

    /**
     * Aktualizacja z aktualną wersją zmienia dane i dezaktywuje czytelnika; własny e-mail
     * (nawet zapisany inaczej wielkością liter) nie jest traktowany jako duplikat.
     */
    @Test
    void shouldUpdateAndDeactivateReaderWhenVersionMatches() {
        Reader reader = readerWithId(5L, EMAIL);
        when(readerRepository.findById(5L)).thenReturn(Optional.of(reader));
        when(readerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(reader));
        when(readerRepository.saveAndFlush(reader)).thenReturn(reader);

        ReaderResponse response = readerService.update(5L, updateRequest("ANNA.NOWAK@example.com", false, 0L));

        assertThat(response.firstName()).isEqualTo("Ania");
        assertThat(response.lastName()).isEqualTo("Kowalska");
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.phone()).isNull();
        assertThat(response.active()).isFalse();
    }

    /**
     * Aktualizacja z nieaktualną wersją jest odrzucana i nic nie zapisuje.
     */
    @Test
    void shouldRejectUpdateWithStaleVersion() {
        Reader reader = readerWithId(5L, EMAIL);
        ReflectionTestUtils.setField(reader, "version", 3L);
        when(readerRepository.findById(5L)).thenReturn(Optional.of(reader));

        assertThatThrownBy(() -> readerService.update(5L, updateRequest(EMAIL, true, 2L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("zmieniony");
        verify(readerRepository, never()).saveAndFlush(any());
        assertThat(reader.getFirstName()).isEqualTo("Anna");
    }

    /**
     * Aktualizacja nie może przypisać adresu e-mail należącego do innego czytelnika.
     */
    @Test
    void shouldRejectUpdateWithEmailOfAnotherReader() {
        Reader reader = readerWithId(5L, EMAIL);
        Reader other = readerWithId(9L, "jan.kowalski@example.com");
        when(readerRepository.findById(5L)).thenReturn(Optional.of(reader));
        when(readerRepository.findByEmail("jan.kowalski@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> readerService.update(5L, updateRequest("Jan.Kowalski@example.com", true, 0L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("e-mail");
        verify(readerRepository, never()).saveAndFlush(any());
        assertThat(reader.getEmail()).isEqualTo(EMAIL);
    }

    /**
     * Aktualizacja nieistniejącego czytelnika zgłasza wyjątek "nie znaleziono".
     */
    @Test
    void shouldFailUpdatingMissingReader() {
        when(readerRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> readerService.update(404L, updateRequest(EMAIL, true, 0L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- delete ----

    /**
     * Istniejący czytelnik jest usuwany.
     */
    @Test
    void shouldDeleteReader() {
        Reader reader = readerWithId(5L, EMAIL);
        when(readerRepository.findById(5L)).thenReturn(Optional.of(reader));

        readerService.delete(5L);

        verify(readerRepository).delete(reader);
    }

    /**
     * Usuwanie nieistniejącego czytelnika zgłasza wyjątek "nie znaleziono".
     */
    @Test
    void shouldFailDeletingMissingReader() {
        when(readerRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> readerService.delete(404L)).isInstanceOf(ResourceNotFoundException.class);
        verify(readerRepository, never()).delete(any(Reader.class));
    }

    // ---- search ----

    /**
     * Lista zwraca czytelników z metadanymi strony.
     */
    @Test
    void shouldReturnPageOfReaders() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("lastName"));
        when(readerRepository.findAll(ArgumentMatchers.<Specification<Reader>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(readerWithId(1L, EMAIL), readerWithId(2L, "jan@example.com")), pageable, 2));

        PageResponse<ReaderResponse> response = readerService.search("nowak", true, pageable);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).extracting(ReaderResponse::email).containsExactly(EMAIL, "jan@example.com");
    }

    /**
     * Sortowanie po niedozwolonym polu jest odrzucane, zanim dojdzie do zapytania do bazy.
     */
    @Test
    void shouldRejectSortingByDisallowedProperty() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("version"));

        assertThatThrownBy(() -> readerService.search(null, null, pageable))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("version");
        verifyNoInteractions(readerRepository);
    }
}