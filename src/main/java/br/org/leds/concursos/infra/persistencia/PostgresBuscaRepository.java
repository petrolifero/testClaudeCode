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
 * Consultas sobre o modelo normalizado (tabelas associativas candidato_profissao e concurso_vaga).
 *
 * <p>A regra "existe uma profissão em comum" é expressa com {@code EXISTS}, que o PostgreSQL
 * transforma em semi-join: para cada linha externa basta achar <em>uma</em> profissão em comum,
 * sem gerar o produto de todas as combinações nem precisar de {@code DISTINCT} sobre ele.</p>
 */
@Repository
public class PostgresBuscaRepository implements BuscaRepository {

    static final String EXISTE_CANDIDATO = "SELECT EXISTS (SELECT 1 FROM candidato WHERE cpf = :cpf)";

    static final String EXISTE_CONCURSO = "SELECT EXISTS (SELECT 1 FROM concurso WHERE codigo = :codigo)";

    static final String CONCURSOS_COMPATIVEIS = """
            SELECT DISTINCT o.sigla AS orgao, c.codigo, c.edital
              FROM concurso c
              JOIN orgao o ON o.id = c.orgao_id
             WHERE EXISTS (SELECT 1
                             FROM candidato ca
                             JOIN candidato_profissao cp ON cp.candidato_id = ca.id
                             JOIN concurso_vaga cv       ON cv.profissao_id = cp.profissao_id
                            WHERE ca.cpf = :cpf
                              AND cv.concurso_id = c.id)
             ORDER BY orgao, c.codigo, c.edital
            """;

    /*
     * Primeiro resolve o conjunto (sem repetição) de profissões com vaga no código, depois os candidatos
     * que têm alguma delas. Os dois IN viram semi-joins, evitando multiplicar linhas quando a mesma
     * profissão aparece em vários concursos do código.
     */
    static final String CANDIDATOS_COMPATIVEIS = """
            SELECT ca.nome, ca.data_nascimento, ca.cpf
              FROM candidato ca
             WHERE ca.id IN (SELECT cp.candidato_id
                               FROM candidato_profissao cp
                              WHERE cp.profissao_id IN (SELECT cv.profissao_id
                                                          FROM concurso c
                                                          JOIN concurso_vaga cv ON cv.concurso_id = c.id
                                                         WHERE c.codigo = :codigo))
             ORDER BY ca.nome, ca.cpf
            """;

    private final JdbcClient jdbc;

    public PostgresBuscaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeCandidato(Cpf cpf) {
        return jdbc.sql(EXISTE_CANDIDATO).param("cpf", cpf.digitos()).query(Boolean.class).single();
    }

    @Override
    public boolean existeConcurso(CodigoConcurso codigo) {
        return jdbc.sql(EXISTE_CONCURSO).param("codigo", codigo.valor()).query(Boolean.class).single();
    }

    @Override
    public List<ConcursoCompativel> concursosCompativeis(Cpf cpf) {
        return jdbc.sql(CONCURSOS_COMPATIVEIS)
                .param("cpf", cpf.digitos())
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
                        new Cpf(rs.getString("cpf")).formatado()))
                .list();
    }
}
