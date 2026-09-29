package br.org.leds.concursos.infra.persistencia;

import br.org.leds.concursos.infra.PostgresDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que as consultas <em>conseguem</em> usar os índices projetados para elas.
 *
 * <p>Com poucos dados o planejador prefere seq scan (é o correto), então desligamos o seq scan
 * na transação do teste: se ainda assim o índice não aparecer no plano, é porque a consulta ficou
 * incompatível com ele (ex.: tipo do parâmetro diferente do tipo da coluna, função sobre a coluna).</p>
 */
@SpringBootTest
@Transactional
class PlanosDeExecucaoTest {

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.registrar(registro);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void busca1UsaPkDoCpfEGinDasVagas() {
        String plano = plano(PostgresBuscaRepository.CONCURSOS_COMPATIVEIS.replace(":cpf", "?"), "438.150.926-98");

        assertThat(plano).contains("pk_candidatos").contains("ix_concursos_vagas");
    }

    @Test
    void busca2UsaIndiceDoCodigoEGinDasProfissoes() {
        String plano = plano(PostgresBuscaRepository.CANDIDATOS_COMPATIVEIS.replace(":codigo", "?"), "61828450843");

        assertThat(plano).contains("ix_concursos_codigo").contains("ix_candidatos_profissoes");
    }

    private String plano(String sql, String parametro) {
        jdbc.execute("SET LOCAL enable_seqscan = off");
        return String.join("\n", jdbc.queryForList("EXPLAIN " + sql, String.class, parametro));
    }
}
