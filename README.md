# Libris API

REST API biblioteki. Trzeci projekt z cyklu Libris: najpierw konsolowy [libris-cli](https://github.com/Tajko725/libris-cli), potem [libris-core](https://github.com/Tajko725/libris-core), a teraz aplikacja webowa z bazą danych PostgreSQL. Projekt przygotowuję, żeby pokazać swój poziom w Javie i Spring Boot.

## Technologie

- Java 25
- Spring Boot 4.1.x (Spring Web, Spring Data JPA, Validation, Actuator)
- Spring Data JPA z Hibernate 7 (encje, repozytoria) i Jakarta Persistence
- PostgreSQL 18 uruchamiany w Dockerze (Docker Compose, wolumen na dane)
- Flyway (migracje schematu bazy)
- JUnit i Testcontainers (testy z prawdziwą bazą)
- Maven (wrapper `mvnw`)
- Jackson 3.1.8 (podniesiony ponad wersję ze Spring Boot 4.1.2 z powodu CVE-2026-110879)
- springdoc-openapi 3.2.0 (dokumentacja OpenAPI i Swagger UI)

## Jak uruchomić

1. Uruchamiam bazę: `docker compose up -d`
2. Uruchamiam aplikację: `.\mvnw.cmd spring-boot:run` (albo klasa `LibrisApiApplication` w IntelliJ)
3. Sprawdzam stan: http://localhost:8080/actuator/health

Dane logowania do bazy w `docker-compose.yml` służą tylko do pracy lokalnej. Bazę mogę przeglądać w DBeaver (`localhost:5432`, baza `libris`).

Zatrzymanie bazy z zachowaniem danych: `docker compose down`. Zatrzymanie razem z usunięciem danych: `docker compose down -v`.

## API książek

Adres bazowy: `http://localhost:8080/api/v1/books`

| Metoda | Adres | Opis |
|---|---|---|
| POST | `/api/v1/books` | dodaję książkę (201 i nagłówek `Location`) |
| GET | `/api/v1/books/{id}` | zwracam książkę z liczbą egzemplarzy |
| GET | `/api/v1/books` | zwracam stronę książek; parametry: `page`, `size` (maks. 100), `sort` (np. `title,desc`), `search` (tytuł lub autor), `genre` |
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
| DELETE | `/api/v1/books/{bookId}/items/{itemId}` | usuwam egzemplarz książki |

Książkę można usunąć dopiero po usunięciu wszystkich jej egzemplarzy (w przeciwnym razie `409`). Egzemplarz z adresu innej książki lub nieistniejący daje `404`.

### Czytelnicy

Adres bazowy: `http://localhost:8080/api/v1/readers`

| Metoda | Adres | Opis |
|---|---|---|
| POST | `/api/v1/readers` | rejestruję czytelnika (201 i nagłówek `Location`); numer karty `LB-000001` nadaje system |
| GET | `/api/v1/readers/{id}` | zwracam czytelnika |
| GET | `/api/v1/readers` | zwracam stronę czytelników; parametry: `page`, `size` (maks. 100), `sort` (np. `lastName,desc`), `search` (imię, nazwisko, e-mail lub numer karty), `active` |
| PUT | `/api/v1/readers/{id}` | aktualizuję czytelnika (w tym dezaktywację polem `active`); wymaga pola `version` |
| DELETE | `/api/v1/readers/{id}` | usuwam czytelnika |

Adres e-mail jest unikalny i zapisywany małymi literami (duplikat daje `409`). Numeru karty i daty rejestracji nie da się zmienić. Konflikt równoczesnej edycji wykrywam polem `version`, tak samo jak przy książkach.

## Status

Gotowe: szkielet projektu, baza w Dockerze, model danych, REST API książek, egzemplarzy i czytelników z DTO, walidacją i obsługą błędów oraz dokumentacja OpenAPI ze Swagger UI. Dalej: wypożyczenia, uwierzytelnianie (Spring Security), CI w GitHub Actions.

## Testy

`.\mvnw.cmd clean verify`. Testy integracyjne same uruchamiają PostgreSQL w kontenerze, więc potrzebuję działającego Dockera.

## Model danych

Schemat bazy tworzy Flyway (migracje w `src/main/resources/db/migration`).

- `book`: tytuł książki (tytuł, autor, rok wydania, ISBN, gatunek). Numer ISBN zapisuję zawsze jako ISBN-13, a ISBN-10 zamieniam na ISBN-13, więc ta sama książka nie może trafić do bazy dwa razy.
- `book_item`: fizyczny egzemplarz książki na półce. Trzy sztuki tej samej książki to jeden wiersz w `book` i trzy w `book_item`. To, czy egzemplarz jest wypożyczony, wyniknie z wypożyczeń.

Reguły pilnuję w dwóch miejscach: w kodzie (walidacja w encjach) i w bazie (ograniczenia `CHECK`, `UNIQUE`, klucz obcy). Obie encje mają pole `version` (optymistyczne blokowanie), które wykrywa równoczesną edycję tego samego rekordu przez dwóch użytkowników.

### Czytelnicy

Tabela `reader` przechowuje czytelników biblioteki: imię, nazwisko, adres e-mail (unikalny, zapisywany małymi literami), opcjonalny telefon, datę rejestracji i status `active` (dezaktywowany czytelnik nie będzie mógł wypożyczać książek). Numer karty bibliotecznej w formacie `LB-000001` nadaje baza z sekwencji `reader_card_number_seq`, więc bibliotekarz go nie wpisuje, a numery się nie powtarzają. Poprawność danych pilnuję trzy razy: w encji, w walidacji API (dodam je w kolejnej funkcji) i w ograniczeniach bazy.

## Status

Gotowe: szkielet projektu, baza w Dockerze, model danych książek, egzemplarzy i czytelników, REST API książek i egzemplarzy z DTO, walidacją i obsługą błędów oraz dokumentacja OpenAPI ze Swagger UI. Dalej: REST API czytelników, wypożyczenia, CI w GitHub Actions.

## Licencja

MIT