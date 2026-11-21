package pl.tajko.library.api.reader;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tajko.library.api.common.ConflictException;
import pl.tajko.library.api.common.InvalidRequestException;
import pl.tajko.library.api.common.PageResponse;
import pl.tajko.library.api.common.ResourceNotFoundException;

/**
 * Logika biznesowa czytelników: rejestracja, odczyt, wyszukiwanie, edycja (w tym dezaktywacja) i usuwanie.
 * Metody działają w transakcjach, a błędy domenowe są zgłaszane wyjątkami zamienianymi
 * na odpowiedzi HTTP w {@code ApiExceptionHandler}.
 */
@Service
public class ReaderService {

    /** Pola encji, po których wolno sortować listę. */
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "lastName", "firstName", "email", "cardNumber", "registeredAt");

    private final ReaderRepository readerRepository;

    /**
     * Tworzy serwis z repozytorium czytelników.
     *
     * @param readerRepository repozytorium czytelników
     */
    public ReaderService(ReaderRepository readerRepository) {
        this.readerRepository = readerRepository;
    }

    /**
     * Rejestruje nowego, aktywnego czytelnika. Numer karty nadaje baza.
     *
     * @param request dane czytelnika
     * @return zapisany czytelnik
     * @throws ConflictException jeśli czytelnik z takim adresem e-mail już istnieje
     */
    @Transactional
    public ReaderResponse create(ReaderRequest request) {
        Reader reader = new Reader(request.firstName(), request.lastName(), request.email(), request.phone());
        if (readerRepository.existsByEmail(reader.getEmail())) {
            throw emailTaken();
        }
        return ReaderResponse.from(readerRepository.save(reader));
    }

    /**
     * Zwraca czytelnika o podanym identyfikatorze.
     *
     * @param id identyfikator czytelnika
     * @return czytelnik
     * @throws ResourceNotFoundException jeśli czytelnik nie istnieje
     */
    @Transactional(readOnly = true)
    public ReaderResponse get(Long id) {
        return ReaderResponse.from(findOrThrow(id));
    }

    /**
     * Zwraca stronę czytelników z opcjonalnym wyszukiwaniem i filtrem aktywności.
     *
     * @param search   fragment imienia, nazwiska, e-maila lub numeru karty, może być {@code null}
     * @param active   filtr statusu aktywności, może być {@code null}
     * @param pageable strona i sortowanie
     * @return strona czytelników
     * @throws InvalidRequestException jeśli sortowanie dotyczy niedozwolonego pola
     */
    @Transactional(readOnly = true)
    public PageResponse<ReaderResponse> search(String search, Boolean active, Pageable pageable) {
        requireAllowedSort(pageable.getSort());

        Specification<Reader> specification = Specification.unrestricted();
        if (active != null) {
            specification = specification.and(ReaderSpecifications.hasActive(active));
        }
        if (search != null && !search.isBlank()) {
            specification = specification.and(ReaderSpecifications.matchesText(search));
        }
        return PageResponse.from(readerRepository.findAll(specification, pageable).map(ReaderResponse::from));
    }

    /**
     * Aktualizuje czytelnika (w tym aktywuje lub dezaktywuje). Zmiana jest przyjmowana tylko wtedy,
     * gdy klient zmienia aktualną wersję.
     *
     * @param id      identyfikator czytelnika
     * @param request nowe dane wraz z wersją, którą klient wcześniej pobrał
     * @return zaktualizowany czytelnik
     * @throws ResourceNotFoundException jeśli czytelnik nie istnieje
     * @throws ConflictException         jeśli wersja jest nieaktualna lub e-mail należy do innego czytelnika
     */
    @Transactional
    public ReaderResponse update(Long id, ReaderUpdateRequest request) {
        Reader reader = findOrThrow(id);
        if (reader.getVersion() != request.version()) {
            throw new ConflictException("Czytelnik został w międzyczasie zmieniony przez kogoś innego (twoja wersja: "
                    + request.version() + ", aktualna: " + reader.getVersion()
                    + "). Pobierz go ponownie i spróbuj jeszcze raz");
        }

        String email = Reader.normalizeEmail(request.email());
        boolean takenByOtherReader = readerRepository.findByEmail(email)
                .filter(other -> !other.getId().equals(id))
                .isPresent();
        if (takenByOtherReader) {
            throw emailTaken();
        }

        reader.setFirstName(request.firstName());
        reader.setLastName(request.lastName());
        reader.setEmail(email);
        reader.setPhone(request.phone());
        reader.setActive(request.active());

        return ReaderResponse.from(readerRepository.saveAndFlush(reader));
    }

    /**
     * Usuwa czytelnika. Blokadę dla czytelników z historią wypożyczeń dodam razem z wypożyczeniami.
     *
     * @param id identyfikator czytelnika
     * @throws ResourceNotFoundException jeśli czytelnik nie istnieje
     */
    @Transactional
    public void delete(Long id) {
        readerRepository.delete(findOrThrow(id));
    }

    private Reader findOrThrow(Long id) {
        return readerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono czytelnika o id " + id));
    }

    private static ConflictException emailTaken() {
        return new ConflictException("Czytelnik z takim adresem e-mail już istnieje");
    }

    private static void requireAllowedSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Niedozwolone pole sortowania: " + order.getProperty()
                        + ". Dozwolone: " + new TreeSet<>(ALLOWED_SORT_PROPERTIES));
            }
        }
    }
}