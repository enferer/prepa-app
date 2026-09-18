package app.prepa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrepaApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrepaApiApplication.class, args);
	}

}
