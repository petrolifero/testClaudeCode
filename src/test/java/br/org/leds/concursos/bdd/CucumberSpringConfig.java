package br.org.leds.concursos.bdd;

import br.org.leds.concursos.infra.PostgresDeTeste;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Sobe a aplicação completa (HTTP real em porta aleatória + PostgreSQL) para os cenários.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.registrar(registro);
    }
}
