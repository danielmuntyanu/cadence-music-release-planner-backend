package dev.danyil;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AppTests {

	@Test
	void contextLoads() {
	}

	@Test
	void mainShouldCallSpringApplicationRun() {
		try (MockedStatic<SpringApplication> mockedStatic = mockStatic(SpringApplication.class)) {
			String[] args = new String[] {"--server.port=0"};

			App.main(args);

			mockedStatic.verify(() -> SpringApplication.run(App.class, args));
		}
	}

}
