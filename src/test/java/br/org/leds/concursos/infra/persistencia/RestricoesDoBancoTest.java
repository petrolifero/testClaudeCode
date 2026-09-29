package br.org.leds.concursos.infra.persistencia;

import br.org.leds.concursos.infra.PostgresDeTeste;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os DOMAINs do esquema rejeitam dados inválidos mesmo que alguém grave direto no banco.
 */
@SpringBootTest
@Transactional
class RestricoesDoBancoTest {

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.registrar(registro);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest(name = "cpf={0}, profissoes={1}")
    @CsvSource(delimiter = ';', value = {
            "43815092698;{pintor}",             // CPF sem pontuação
            "438.150.926-9;{pintor}",           // CPF curto
            "438.150.926-98;{}",                // lista vazia
            "438.150.926-98;'{pintor,NULL}'"    // elemento nulo
    })
    void candidatoInvalidoERejeitado(String cpf, String profissoes) {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO candidatos VALUES ('Fulano', DATE '2000-01-01', ?, ?::text[])", cpf, profissoes))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({"6182845084", "6182845084a"})
    void codigoDeConcursoInvalidoERejeitado(String codigo) {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concursos (orgao, edital, codigo, vagas) VALUES ('SEDU', '1/2020', ?, '{pintor}')", codigo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
