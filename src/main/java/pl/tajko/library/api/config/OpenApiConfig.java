package pl.tajko.library.api.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguracja ogólnych informacji o API widocznych w dokumentacji OpenAPI i w Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Tworzy opis API (tytuł, opis i odnośnik do repozytorium).
     *
     * @return opis API w formacie OpenAPI
     */
    @Bean
    OpenAPI librisOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Libris API")
                        .version("v1")
                        .description("REST API biblioteki Libris: zarządzanie książkami, egzemplarzami, "
                                + "czytelnikami i wypożyczeniami. Projekt portfolio do nauki Javy i Spring Boot."))
                .externalDocs(new ExternalDocumentation()
                        .description("Repozytorium projektu na GitHubie")
                        .url("https://github.com/Tajko725/libris-api"));
    }
}