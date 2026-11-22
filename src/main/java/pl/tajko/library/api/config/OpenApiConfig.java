package pl.tajko.library.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Konfiguracja ogólnych informacji o API widocznych w dokumentacji OpenAPI i w Swagger UI,
 * wraz ze schematem uwierzytelniania tokenem JWT (przycisk Authorize w Swagger UI).
 */
@Configuration
public class OpenApiConfig {

    /** Nazwa schematu uwierzytelniania w specyfikacji. */
    public static final String BEARER_SCHEME = "bearerAuth";

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String PROBLEM_JSON = "application/problem+json";

    /**
     * Tworzy opis API (tytuł, opis, odnośnik do repozytorium) i schemat uwierzytelniania Bearer JWT.
     * Schemat jest wymagany domyślnie dla wszystkich operacji; logowanie je wyłącza adnotacją
     * {@code @SecurityRequirements}.
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
                                + "czytelnikami i wypożyczeniami. Projekt portfolio do nauki Javy i Spring Boot. "
                                + "Najpierw loguję się przez POST /api/v1/auth/login, a otrzymany token wklejam "
                                + "pod przyciskiem Authorize."))
                .externalDocs(new ExternalDocumentation()
                        .description("Repozytorium projektu na GitHubie")
                        .url("https://github.com/Tajko725/libris-api"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token z POST /api/v1/auth/login (sam token, bez słowa Bearer)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * Dopisuje do każdej operacji chronionej tokenem opis odpowiedzi 401 (brak tokenu) i 403 (zła rola),
     * żeby nie powtarzać ich w każdym kontrolerze. Operacje z własnym wpisem (np. logowanie) zostają bez zmian.
     *
     * @return dostosowanie specyfikacji
     */
    @Bean
    OpenApiCustomizer securityResponsesCustomizer() {
        return openApi -> openApi.getPaths().forEach((url, path) -> path.readOperations().forEach(operation -> {
            // null oznacza, że operacja dziedziczy globalne wymaganie tokenu; pusta lista oznacza operację publiczną
            if (operation.getSecurity() == null && !LOGIN_PATH.equals(url)) {
                addIfMissing(operation, "401", "Brak ważnego tokenu dostępu");
                addIfMissing(operation, "403", "Rola zalogowanego użytkownika nie pozwala na tę operację");
            }
        }));
    }

    private static void addIfMissing(Operation operation, String code, String description) {
        if (operation.getResponses().containsKey(code)) {
            return;
        }
        Schema<?> problem = new Schema<>().$ref("#/components/schemas/Problem");
        operation.getResponses().addApiResponse(code, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(problem))));
    }
}
