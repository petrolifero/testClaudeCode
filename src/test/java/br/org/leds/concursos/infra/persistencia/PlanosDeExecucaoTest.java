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
 * incompatível com ele. O parâmetro é enviado como {@code text} de propósito — foi assim que se
 * descobriu que {@code char(11)} fazia o índice do CPF ser ignorado.</p>
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
    void busca1UsaIndiceDoCpfEIndiceInversoDasVagas() {
        String plano = plano(PostgresBuscaRepository.CONCURSOS_COMPATIVEIS, ":cpf", "43815092698");

        assertThat(plano).contains("uk_candidato_cpf").contains("ix_concurso_vaga_inv");
    }

    @Test
    void busca2UsaIndiceDoCodigoEIndiceInversoDasProfissoes() {
        String plano = plano(PostgresBuscaRepository.CANDIDATOS_COMPATIVEIS, ":codigo", "61828450843");

        assertThat(plano).contains("ix_concurso_codigo").contains("ix_candidato_profissao_inv");
    }

    private String plano(String sql, String nomeParametro, String valor) {
        jdbc.execute("SET LOCAL enable_seqscan = off");
        String explain = "EXPLAIN " + sql.replace(nomeParametro, "CAST(? AS text)");
        return String.join("\n", jdbc.queryForList(explain, String.class, valor));
    }
}
