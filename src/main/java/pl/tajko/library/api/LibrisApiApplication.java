package pl.tajko.library.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punkt wejścia aplikacji Libris API, czyli REST API biblioteki.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LibrisApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LibrisApiApplication.class, args);
	}

}
