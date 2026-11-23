package pl.tajko.library.api;

import org.springframework.boot.SpringApplication;

public class TestLibrisApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(LibrisApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
