package br.org.leds.concursos.infra;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Operações auxiliares dos testes sobre o esquema com arrays.
 */
@Component
public class BancoDeTeste {

    private final JdbcTemplate jdbc;

    public BancoDeTeste(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void limpar() {
        jdbc.execute("TRUNCATE candidatos, concursos RESTART IDENTITY");
    }
}
