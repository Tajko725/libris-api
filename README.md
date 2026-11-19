# Libris API

REST API biblioteki. Trzeci projekt z cyklu Libris: najpierw konsolowy [libris-cli](https://github.com/Tajko725/libris-cli), potem [libris-core](https://github.com/Tajko725/libris-core), a teraz aplikacja webowa z bazą danych PostgreSQL. Projekt przygotowuję, żeby pokazać swój poziom w Javie i Spring Boot.

## Technologie

- Java 25
- Spring Boot 4.1.x (Spring Web, Spring Data JPA, Validation, Actuator)
- PostgreSQL 18 uruchamiany w Dockerze (Docker Compose, wolumen na dane)
- Flyway (migracje schematu bazy)
- JUnit i Testcontainers (testy z prawdziwą bazą)
- Maven (wrapper `mvnw`)

## Jak uruchomić

1. Uruchamiam bazę: `docker compose up -d`
2. Uruchamiam aplikację: `.\mvnw.cmd spring-boot:run` (albo klasa `LibrisApiApplication` w IntelliJ)
3. Sprawdzam stan: http://localhost:8080/actuator/health

Dane logowania do bazy w `docker-compose.yml` służą tylko do pracy lokalnej. Bazę mogę przeglądać w DBeaver (`localhost:5432`, baza `libris`).

Zatrzymanie bazy z zachowaniem danych: `docker compose down`. Zatrzymanie razem z usunięciem danych: `docker compose down -v`.

## Testy

`.\mvnw.cmd clean verify`. Testy integracyjne same uruchamiają PostgreSQL w kontenerze, więc potrzebuję działającego Dockera.

## Status

W trakcie: szkielet projektu i baza w Dockerze. Dalej: encje i migracje, REST API z DTO, dokumentacja OpenAPI, CI w GitHub Actions.

## Licencja

MIT