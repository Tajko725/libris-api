# syntax=docker/dockerfile:1

# Etap 1: buduję JAR i rozpakowuję go na warstwy (zależności zmieniają się rzadziej niż kod).
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /builder

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# Testy pomijam, bo wykonuje je wcześniej workflow CI (./mvnw verify).
# Cache Mavena przyspiesza kolejne budowania i nie trafia do obrazu.
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -DskipTests package

# Kopiuję JAR jako application.jar: narzędzie extract nadaje wyodrębnionemu plikowi tę samą nazwę,
# a ENTRYPOINT uruchamia właśnie application.jar.
RUN cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Etap 2: lekkie środowisko uruchomieniowe (samo JRE), bez narzędzi budowania.
FROM eclipse-temurin:25-jre
WORKDIR /application

# Aplikacja działa jako zwykły użytkownik, nie jako root.
RUN groupadd --system --gid 10001 libris \
    && useradd --system --uid 10001 --gid libris --no-create-home libris

# Każdy COPY to osobna warstwa: przy zmianie kodu Docker zmienia tylko ostatnią.
COPY --from=builder --chown=libris:libris /builder/extracted/dependencies/ ./
COPY --from=builder --chown=libris:libris /builder/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=libris:libris /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=libris:libris /builder/extracted/application/ ./

USER libris
EXPOSE 8080

# Konfigurację podaję zmiennymi środowiskowymi: DB_URL, DB_USER, DB_PASSWORD,
# LIBRIS_JWT_SECRET, LIBRIS_ADMIN_USERNAME, LIBRIS_ADMIN_PASSWORD.
ENTRYPOINT ["java", "-jar", "application.jar"]
