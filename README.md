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

## Jak uruchomić

1. Uruchamiam bazę: `docker compose up -d`
2. Uruchamiam aplikację: `.\mvnw.cmd spring-boot:run` (albo klasa `LibrisApiApplication` w IntelliJ)
3. Sprawdzam stan: http://localhost:8080/actuator/health

Dane logowania do bazy w `docker-compose.yml` służą tylko do pracy lokalnej. Bazę mogę przeglądać w DBeaver (`localhost:5432`, baza `libris`).

Zatrzymanie bazy z zachowaniem danych: `docker compose down`. Zatrzymanie razem z usunięciem danych: `docker compose down -v`.

## Testy

`.\mvnw.cmd clean verify`. Testy integracyjne same uruchamiają PostgreSQL w kontenerze, więc potrzebuję działającego Dockera.

## Model danych

Schemat bazy tworzy Flyway (migracje w `src/main/resources/db/migration`).

- `book`: tytuł książki (tytuł, autor, rok wydania, ISBN, gatunek). Numer ISBN zapisuję zawsze jako ISBN-13, a ISBN-10 zamieniam na ISBN-13, więc ta sama książka nie może trafić do bazy dwa razy.
- `book_item`: fizyczny egzemplarz książki na półce. Trzy sztuki tej samej książki to jeden wiersz w `book` i trzy w `book_item`. To, czy egzemplarz jest wypożyczony, wyniknie z wypożyczeń.

Reguły pilnuję w dwóch miejscach: w kodzie (walidacja w encjach) i w bazie (ograniczenia `CHECK`, `UNIQUE`, klucz obcy). Obie encje mają pole `version` (optymistyczne blokowanie), które wykrywa równoczesną edycję tego samego rekordu przez dwóch użytkowników.

## Status

Gotowe: szkielet projektu, baza w Dockerze, model danych książek (migracja, encje, repozytoria, testy). Dalej: REST API książek z DTO, czytelnicy i wypożyczenia, dokumentacja OpenAPI, CI w GitHub Actions.

## Licencja

MIT