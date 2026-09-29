package br.org.leds.concursos.infra.persistencia;

import br.org.leds.concursos.aplicacao.BuscaRepository;
import br.org.leds.concursos.aplicacao.CandidatoCompativel;
import br.org.leds.concursos.aplicacao.ConcursoCompativel;
import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Cpf;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Consultas sobre o modelo com arrays nativos.
 *
 * <p>"Ter ao menos uma profissão em comum" é exatamente o operador de sobreposição de arrays
 * {@code &&}, atendido pelos índices GIN de {@code concursos.vagas} e {@code candidatos.profissoes}.
 * O array do "outro lado" é obtido por uma subconsulta escalar, que o PostgreSQL executa uma única
 * vez (InitPlan) antes de varrer o índice.</p>
 */
@Repository
public class PostgresBuscaRepository implements BuscaRepository {

    static final String EXISTE_CANDIDATO = "SELECT EXISTS (SELECT 1 FROM candidatos WHERE cpf = :cpf)";

    static final String EXISTE_CONCURSO = "SELECT EXISTS (SELECT 1 FROM concursos WHERE codigo = :codigo)";

    static final String CONCURSOS_COMPATIVEIS = """
            SELECT DISTINCT orgao, codigo, edital
              FROM concursos
             WHERE vagas && (SELECT profissoes FROM candidatos WHERE cpf = :cpf)
             ORDER BY orgao, codigo, edital
            """;

    /*
     * Vários concursos podem ter o mesmo código: une as vagas de todos eles (unnest + array_agg
     * DISTINCT, lendo só o índice ix_concursos_codigo graças ao INCLUDE) e busca quem tem alguma delas.
     */
    static final String CANDIDATOS_COMPATIVEIS = """
            SELECT nome, data_nascimento, cpf
              FROM candidatos
             WHERE profissoes && (SELECT array_agg(DISTINCT vaga)
                                    FROM concursos c
                                   CROSS JOIN LATERAL unnest(c.vagas) AS vaga
                                   WHERE c.codigo = :codigo)
             ORDER BY nome, cpf
            """;

    private final JdbcClient jdbc;

    public PostgresBuscaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeCandidato(Cpf cpf) {
        return jdbc.sql(EXISTE_CANDIDATO).param("cpf", cpf.formatado()).query(Boolean.class).single();
    }

    @Override
    public boolean existeConcurso(CodigoConcurso codigo) {
        return jdbc.sql(EXISTE_CONCURSO).param("codigo", codigo.valor()).query(Boolean.class).single();
    }

    @Override
    public List<ConcursoCompativel> concursosCompativeis(Cpf cpf) {
        return jdbc.sql(CONCURSOS_COMPATIVEIS)
                .param("cpf", cpf.formatado())
                .query((rs, i) -> new ConcursoCompativel(
                        rs.getString("orgao"), rs.getString("codigo"), rs.getString("edital")))
                .list();
    }

    @Override
    public List<CandidatoCompativel> candidatosCompativeis(CodigoConcurso codigo) {
        return jdbc.sql(CANDIDATOS_COMPATIVEIS)
                .param("codigo", codigo.valor())
                .query((rs, i) -> new CandidatoCompativel(
                        rs.getString("nome"),
                        rs.getObject("data_nascimento", LocalDate.class),
                        rs.getString("cpf")))
                .list();
    }
}
