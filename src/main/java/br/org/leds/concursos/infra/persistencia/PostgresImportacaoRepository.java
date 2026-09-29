package br.org.leds.concursos.infra.persistencia;

import br.org.leds.concursos.aplicacao.ImportacaoRepository;
import br.org.leds.concursos.dominio.Candidato;
import br.org.leds.concursos.dominio.Concurso;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Grava candidatos e concursos no modelo com arrays: uma linha por linha do arquivo,
 * com as listas enviadas como {@code text[]} (JDBC batch).
 */
@Repository
public class PostgresImportacaoRepository implements ImportacaoRepository {

    private static final String INSERIR_CANDIDATO =
            "INSERT INTO candidatos (nome, data_nascimento, cpf, profissoes) VALUES (?, ?, ?, ?::text[])";

    private static final String INSERIR_CONCURSO =
            "INSERT INTO concursos (orgao, edital, codigo, vagas) VALUES (?, ?, ?, ?::text[])";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public PostgresImportacaoRepository(JdbcTemplate jdbcTemplate, JdbcClient jdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
    }

    @Override
    public boolean possuiDados() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM candidatos) OR EXISTS (SELECT 1 FROM concursos)")
                .query(Boolean.class).single();
    }

    @Override
    public void salvarCandidatos(List<Candidato> candidatos) {
        jdbcTemplate.batchUpdate(INSERIR_CANDIDATO, candidatos, candidatos.size(), (ps, c) -> {
            ps.setString(1, c.nome());
            ps.setDate(2, Date.valueOf(c.dataNascimento()));
            ps.setString(3, c.cpf().formatado());
            ps.setArray(4, ps.getConnection().createArrayOf("text", ordenado(c.profissoes())));
        });
    }

    @Override
    public void salvarConcursos(List<Concurso> concursos) {
        jdbcTemplate.batchUpdate(INSERIR_CONCURSO, concursos, concursos.size(), (ps, c) -> {
            ps.setString(1, c.orgao());
            ps.setString(2, c.edital());
            ps.setString(3, c.codigo().valor());
            ps.setArray(4, ps.getConnection().createArrayOf("text", ordenado(c.vagas())));
        });
    }

    /** Arrays gravados em ordem alfabética: facilita a leitura e deixa a representação determinística. */
    private static String[] ordenado(Collection<String> itens) {
        return new TreeSet<>(itens).toArray(String[]::new);
    }
}
