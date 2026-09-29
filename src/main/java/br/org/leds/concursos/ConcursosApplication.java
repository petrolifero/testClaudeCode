package br.org.leds.concursos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada do serviço de busca de concursos públicos compatíveis com candidatos.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ConcursosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConcursosApplication.class, args);
    }
}
