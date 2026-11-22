# Libris API

REST API biblioteki. Trzeci projekt z cyklu Libris: najpierw konsolowy [libris-cli](https://github.com/Tajko725/libris-cli), potem [libris-core](https://github.com/Tajko725/libris-core), a teraz aplikacja webowa z bazą danych PostgreSQL. Projekt przygotowuję, żeby pokazać swój poziom w Javie i Spring Boot.

## Technologie

- Java 25
- Spring Boot 4.1.x (Spring Web, Spring Data JPA, Validation, Actuator, Security)
- Spring Data JPA z Hibernate 7 (encje, repozytoria) i Jakarta Persistence
- PostgreSQL 18 uruchamiany w Dockerze (Docker Compose, wolumen na dane)
- Flyway (migracje schematu bazy)
- JUnit i Testcontainers (testy z prawdziwą bazą)
- Maven (wrapper `mvnw`)
- Jackson 3.1.8 (podniesiony ponad wersję ze Spring Boot 4.1.2 z powodu CVE-2026-110879)
- springdoc-openapi 3.2.0 (dokumentacja OpenAPI i Swagger UI)
- Spring Security 7 z serwerem zasobów OAuth2 (tokeny JWT HS256) i haszowaniem haseł bcrypt (`DelegatingPasswordEncoder`)

## Jak uruchomić

1. Uruchamiam bazę: `docker compose up -d`
2. Uruchamiam aplikację: `.\mvnw.cmd spring-boot:run` (albo klasa `LibrisApiApplication` w IntelliJ)
3. Sprawdzam stan: http://localhost:8080/actuator/health
4. Loguję się (`POST /api/v1/auth/login`), bo pozostałe adresy API wymagają tokenu (szczegóły w sekcji „Uwierzytelnianie i uprawnienia”)

Dane logowania do bazy w `docker-compose.yml` służą tylko do pracy lokalnej. Bazę mogę przeglądać w DBeaver (`localhost:5432`, baza `libris`).

Zatrzymanie bazy z zachowaniem danych: `docker compose down`. Zatrzymanie razem z usunięciem danych: `docker compose down -v`.

## API książek

Adres bazowy: `http://localhost:8080/api/v1/books`

| Metoda | Adres | Opis |
|---|---|---|
| POST | `/api/v1/books` | dodaję książkę (201 i nagłówek `Location`) |
| GET | `/api/v1/books/{id}` | zwracam książkę z liczbą egzemplarzy (`copiesCount`) i liczbą dostępnych do wypożyczenia (`availableCopies`) |
| GET | `/api/v1/books` | zwracam stronę książek; parametry: `page`, `size` (maks. 100), `sort` (np. `title,desc`), `search` (tytuł lub autor), `genre`, `available` (`true`: jest wolny egzemplarz) |
| PUT | `/api/v1/books/{id}` | aktualizuję książkę; wymaga pola `version` |
| DELETE | `/api/v1/books/{id}` | usuwam książkę bez egzemplarzy |

Dokumentację API mam w Swagger UI pod adresem `http://localhost:8080/swagger-ui.html` (po uruchomieniu aplikacji). Można tam wypróbować każdy endpoint przyciskiem „Try it out". Specyfikacja OpenAPI jest pod adresem `http://localhost:8080/v3/api-docs`.

Przykładowe żądania są też w pliku `http/books.http` (działa w VS Code z rozszerzeniem REST Client oraz w IntelliJ IDEA Ultimate).

Błędy zwracam w formacie Problem Details (RFC 9457, `application/problem+json`): `400` przy niepoprawnych danych (z listą błędnych pól), `404` gdy książki nie ma, `409` przy duplikacie ISBN, próbie usunięcia książki z egzemplarzami oraz przy konflikcie wersji.

Równoczesna edycja: odpowiedź zawiera pole `version`, które odsyłam w `PUT`. Jeśli ktoś zdążył zmienić książkę wcześniej, dostaję `409` zamiast po cichu nadpisanych zmian.

### Egzemplarze

Egzemplarz to fizyczna sztuka książki. Dane książki (tytuł, autor, ISBN) są zapisane raz, a egzemplarze tylko wskazują na nią. Liczba sztuk jest widoczna w polu `copiesCount` odpowiedzi o książce.

| Metoda | Adres | Opis |
|---|---|---|
| POST | `/api/v1/books/{bookId}/items` | dodaję `count` egzemplarzy (od 1 do 100 w jednym żądaniu); kolejne wywołania dodają kolejne sztuki |
| GET | `/api/v1/books/{bookId}/items` | zwracam listę egzemplarzy książki |
| DELETE | `/api/v1/books/{bookId}/items/{itemId}` | usuwam egzemplarz książki, który nigdy nie był wypożyczony |

Książkę można usunąć dopiero po usunięciu wszystkich jej egzemplarzy (w przeciwnym razie `409`). Egzemplarz z adresu innej książki lub nieistniejący daje `404`. Egzemplarza z historią wypożyczeń (także zakończonych) nie usunę (`409`), bo historia musi pozostać kompletna.

### Czytelnicy

Adres bazowy: `http://localhost:8080/api/v1/readers`

| Metoda | Adres | Opis |
|---|---|---|
| POST | `/api/v1/readers` | rejestruję czytelnika (201 i nagłówek `Location`); numer karty `LB-000001` nadaje system |
| GET | `/api/v1/readers/{id}` | zwracam czytelnika |
| GET | `/api/v1/readers` | zwracam stronę czytelników; parametry: `page`, `size` (maks. 100), `sort` (np. `lastName,desc`), `search` (imię, nazwisko, e-mail lub numer karty), `active` |
| PUT | `/api/v1/readers/{id}` | aktualizuję czytelnika (w tym dezaktywację polem `active`); wymaga pola `version` |
| DELETE | `/api/v1/readers/{id}` | usuwam czytelnika, który nigdy nie wypożyczał książek |

Adres e-mail jest unikalny i zapisywany małymi literami (duplikat daje `409`). Numeru karty i daty rejestracji nie da się zmienić. Konflikt równoczesnej edycji wykrywam polem `version`, tak samo jak przy książkach. Czytelnika z historią wypożyczeń nie usunę (`409`), mogę go tylko dezaktywować.

## API wypożyczeń

Adres bazowy: `http://localhost:8080/api/v1/loans`

| Metoda | Adres | Opis |
|---|---|---|
| POST | `/api/v1/loans` | wypożyczam egzemplarz czytelnikowi (`bookItemId`, `readerId`); termin zwrotu ustawia serwer |
| GET | `/api/v1/loans/{id}` | zwracam wypożyczenie z podstawowymi danymi czytelnika i książki |
| GET | `/api/v1/loans` | zwracam stronę wypożyczeń (także historię); parametry: `page`, `size`, `sort`, `readerId`, `bookId`, `status` (`ACTIVE`, `OVERDUE`, `RETURNED`) |
| POST | `/api/v1/loans/{id}/return` | rejestruję zwrot |
| POST | `/api/v1/loans/{id}/renew` | przedłużam termin zwrotu |

Zasady wypożyczeń ustawiam w `application.yml` (klucz `libris.loans`): okres wypożyczenia 30 dni, limit 5 niezwróconych książek na czytelnika, przedłużenie o 14 dni. Aplikacja nie uruchomi się, jeśli którakolwiek wartość jest mniejsza niż 1.

Odmawiam (`409`), gdy: egzemplarz jest już wypożyczony, czytelnik jest nieaktywny, ma książkę po terminie albo osiągnął limit. Termin zwrotu można przedłużyć tylko raz i tylko do dnia terminu włącznie. Kar za przetrzymanie nie ma. Stan wypożyczenia (`ACTIVE`, `OVERDUE`, `RETURNED`) wyliczam na bieżąco z terminu i daty zwrotu, a nie zapisuję w bazie. Czas pobieram ze wstrzykiwanego `Clock` (UTC), więc testy mogą ustawić dowolny moment. Limit wypożyczeń pilnuję blokadą wiersza czytelnika, więc dwa równoczesne żądania nie ominą limitu, a ten sam egzemplarz nie trafi do dwóch osób naraz dzięki indeksowi w bazie.

## Uwierzytelnianie i uprawnienia

Konta mają tylko pracownicy biblioteki, czytelnicy ich nie mają. Loguję się przez `POST /api/v1/auth/login` (JSON z polami `username` i `password`) i dostaję token JWT ważny 60 minut (`expiresIn` podaję w sekundach). Token wysyłam w każdym żądaniu w nagłówku `Authorization: Bearer <token>`. W Swagger UI wklejam sam token pod przyciskiem **Authorize**. Odświeżania tokenów nie ma: po wygaśnięciu loguję się ponownie.

| Rola | Co może |
| --- | --- |
| `LIBRARIAN` | czytać, dodawać i edytować książki, egzemplarze, czytelników oraz wypożyczenia (wypożyczenie, zwrot, przedłużenie), bez usuwania |
| `ADMIN` | to samo co `LIBRARIAN`, a do tego usuwanie książek, egzemplarzy i czytelników oraz zarządzanie użytkownikami |

Publiczne są tylko: logowanie, `/actuator/health`, Swagger UI i `/v3/api-docs`. Cała reszta wymaga tokenu. Brak ważnego tokenu daje `401`, a zbyt niska rola `403`, oba w formacie Problem Details. Wszystkie reguły dostępu są w jednym miejscu: `SecurityConfig`, a rola `ADMIN` dziedziczy uprawnienia `LIBRARIAN` (hierarchia ról).

Jak to działa:

- Token podpisuję algorytmem HS256 sekretem z zmiennej `LIBRIS_JWT_SECRET` (co najmniej 32 bajty). Domyślna wartość w `application.yml` jest tylko do pracy lokalnej, a aplikacja ostrzega o niej w logu. Na każdym innym środowisku ustawiam własny sekret, na przykład wygenerowany poleceniem `openssl rand -base64 48`.
- Token zawiera nazwę użytkownika, wystawcę, czas wystawienia i wygaśnięcia, unikalny identyfikator oraz rolę (informacyjnie). Podpis, ważność i wystawcę sprawdza Spring Security, bez własnych filtrów.
- Aplikacja jest bezstanowa: nie tworzę sesji ani ciasteczek, więc CSRF jest wyłączony.
- Zła nazwa, złe hasło i wyłączone konto dają taką samą odpowiedź `401`, żeby nie dało się sprawdzić, które konta istnieją.
- Przy każdym żądaniu sprawdzam w bazie, czy konto z tokenu istnieje i jest aktywne, a uprawnienia biorę z aktualnej roli w bazie, a nie z tokenu (`DatabaseJwtAuthenticationConverter`). Dzięki temu wyłączenie konta albo zmiana roli działa od razu, nawet na już wydanym tokenie. Kosztuje to jedno zapytanie po unikalnej nazwie na żądanie. Zmiana hasła nie unieważnia wydanych tokenów, więc przy podejrzeniu włamania wyłączam konto.

Świadomie pominąłem ochronę przed zgadywaniem haseł (ograniczanie liczby prób logowania, blokady kont), bo to projekt do nauki. W prawdziwym wdrożeniu dodałbym ją na bramie przed aplikacją albo w samej aplikacji.

## Zarządzanie użytkownikami

Adres bazowy: `http://localhost:8080/api/v1/users` (tylko rola `ADMIN`). Kont nie usuwam, tylko wyłączam, żeby nie tracić śladu po tym, kto istniał.

- `POST /api/v1/users`: tworzę konto (nazwa, hasło, rola).
- `GET /api/v1/users`: lista ze stronicowaniem, sortowaniem (`id`, `username`, `role`, `enabled`, `createdAt`) oraz filtrami `search` (fragment nazwy), `role` i `enabled`.
- `GET /api/v1/users/{id}`: jedno konto. Hasło ani jego hash nigdy nie są zwracane.
- `PUT /api/v1/users/{id}`: zmieniam rolę i status konta. Wymaga `version` z ostatniego odczytu (nieaktualna wersja daje `409`, tak samo jak przy czytelnikach).
- `PUT /api/v1/users/{id}/password`: administrator ustawia nowe hasło bez znajomości starego.
- `PUT /api/v1/auth/password`: każdy zalogowany zmienia własne hasło (podaje aktualne i nowe, które musi różnić się od obecnego).

Nie wolno zdegradować ani wyłączyć ostatniego aktywnego administratora (`409`). Na czas zmiany blokuję wiersze aktywnych administratorów, więc dwóch administratorów, którzy jednocześnie odbierają sobie uprawnienia, nie zostawi aplikacji bez administratora: jedna zmiana przechodzi, druga dostaje `409`. Administrator może zdegradować lub wyłączyć samego siebie, o ile jest inny aktywny administrator.

## Pierwszy administrator

Konta mają tylko pracownicy biblioteki (czytelnicy kont nie mają). Gdy tabela `app_user` jest pusta, przy starcie tworzę pierwszego administratora ze zmiennych środowiskowych:

- `LIBRIS_ADMIN_USERNAME` (domyślnie `admin`),
- `LIBRIS_ADMIN_PASSWORD` (od 12 znaków, najwyżej 72 bajty; bez tej zmiennej konto nie powstaje, a aplikacja tylko ostrzega w logu).

W IntelliJ ustawiam hasło w Run → Edit Configurations → `LibrisApiApplication` → Modify options → Environment variables (`LIBRIS_ADMIN_PASSWORD=...`). W PowerShellu: `$env:LIBRIS_ADMIN_PASSWORD = "..."` przed `.\mvnw.cmd spring-boot:run`. Jeśli hasło jest za krótkie lub za długie, aplikacja nie wystartuje. Gdy w bazie jest już jakikolwiek użytkownik, zmienne ignoruję, więc restart nie zmienia hasła ani nie dodaje drugiego administratora.

## Testy

`.\mvnw.cmd clean verify`. Testy integracyjne same uruchamiają PostgreSQL w kontenerze, więc potrzebuję działającego Dockera.

## Model danych

Schemat bazy tworzy Flyway (migracje w `src/main/resources/db/migration`).

- `book`: tytuł książki (tytuł, autor, rok wydania, ISBN, gatunek). Numer ISBN zapisuję zawsze jako ISBN-13, a ISBN-10 zamieniam na ISBN-13, więc ta sama książka nie może trafić do bazy dwa razy.
- `book_item`: fizyczny egzemplarz książki na półce. Trzy sztuki tej samej książki to jeden wiersz w `book` i trzy w `book_item`. To, czy egzemplarz jest wypożyczony, wyniknie z wypożyczeń.

Reguły pilnuję w dwóch miejscach: w kodzie (walidacja w encjach) i w bazie (ograniczenia `CHECK`, `UNIQUE`, klucz obcy). Obie encje mają pole `version` (optymistyczne blokowanie), które wykrywa równoczesną edycję tego samego rekordu przez dwóch użytkowników.

### Wypożyczenia

Tabela `loan` łączy egzemplarz (`book_item`) z czytelnikiem (`reader`): zawiera datę wypożyczenia, termin zwrotu, datę zwrotu (pustą, dopóki książka nie wróci) i liczbę przedłużeń. Status „wypożyczony" nie jest zapisany w egzemplarzu, tylko wynika z tego, czy ma aktywne wypożyczenie, więc nie może się rozjechać z historią. Jeden egzemplarz ma co najwyżej jedno aktywne wypożyczenie, co pilnuje częściowy indeks unikalny w bazie (działa też przy równoczesnych żądaniach). Klucze obce mają `ON DELETE RESTRICT`, więc egzemplarza ani czytelnika z historią wypożyczeń nie da się usunąć. Termin zwrotu można przedłużyć najwyżej raz i tylko przed jego upływem. Liczba dostępnych egzemplarzy książki to liczba jej egzemplarzy minus aktywne wypożyczenia.

### Czytelnicy

Tabela `reader` przechowuje czytelników biblioteki: imię, nazwisko, adres e-mail (unikalny, zapisywany małymi literami), opcjonalny telefon, datę rejestracji i status `active` (dezaktywowany czytelnik nie będzie mógł wypożyczać książek). Numer karty bibliotecznej w formacie `LB-000001` nadaje baza z sekwencji `reader_card_number_seq`, więc bibliotekarz go nie wpisuje, a numery się nie powtarzają. Poprawność danych pilnuję trzy razy: w encji, w walidacji API (dodam je w kolejnej funkcji) i w ograniczeniach bazy.

### Użytkownicy

Tabela `app_user` przechowuje konta pracowników: nazwę użytkownika (unikalna, 3-50 znaków: małe litery, cyfry, `.`, `_`, `-`), hash hasła, rolę (`LIBRARIAN` albo `ADMIN`), status `enabled` i datę utworzenia. Hasła nigdy nie zapisuję jawnie: używam `DelegatingPasswordEncoder` z bcrypt, więc w bazie jest wartość w formacie `{bcrypt}$2a$...`. Pilnuję tego w bazie ograniczeniem `CHECK`, więc nawet ręczny `INSERT` z jawnym hasłem lub `{noop}` zostanie odrzucony. Nazwę zapisuję małymi literami, więc `Jan` i `jan` to to samo konto. Hasło ma od 12 znaków i najwyżej 72 bajty, bo bcrypt nie bierze pod uwagę dalszych bajtów. Logowanie opisuję w sekcji „Uwierzytelnianie i uprawnienia”.

## Status

Gotowe: szkielet projektu, baza w Dockerze, model danych książek, egzemplarzy, czytelników i wypożyczeń, REST API książek, egzemplarzy, czytelników i wypożyczeń z DTO, walidacją i obsługą błędów oraz dokumentacja OpenAPI ze Swagger UI. Model użytkowników z bezpiecznie przechowywanymi hasłami, pierwszy administrator oraz logowanie tokenem JWT z rolami `LIBRARIAN` i `ADMIN`. API do zarządzania użytkownikami z ochroną ostatniego administratora. Dalej: CI w GitHub Actions.

## Licencja

MIT