package br.org.leds.concursos.infra.persistencia;

import br.org.leds.concursos.aplicacao.ImportacaoRepository;
import br.org.leds.concursos.dominio.Candidato;
import br.org.leds.concursos.dominio.Concurso;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Grava candidatos e concursos no modelo normalizado.
 *
 * <p>Estratégia: primeiro as tabelas de domínio (profissão, órgão), depois as entidades em lote
 * (JDBC batch com retorno das chaves geradas) e por fim as tabelas associativas, também em lote.</p>
 */
@Repository
public class PostgresImportacaoRepository implements ImportacaoRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public PostgresImportacaoRepository(JdbcTemplate jdbcTemplate, JdbcClient jdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
    }

    @Override
    public boolean possuiDados() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM candidato) OR EXISTS (SELECT 1 FROM concurso)")
                .query(Boolean.class).single();
    }

    @Override
    public void salvarCandidatos(List<Candidato> candidatos) {
        Map<String, Integer> profissoes = garantirProfissoes(
                candidatos.stream().flatMap(c -> c.profissoes().stream()).collect(Collectors.toSet()));

        List<Long> ids = inserirComChaves(
                "INSERT INTO candidato (cpf, nome, data_nascimento) VALUES (?, ?, ?)",
                candidatos, (ps, c) -> {
                    ps.setString(1, c.cpf().digitos());
                    ps.setString(2, c.nome());
                    ps.setDate(3, Date.valueOf(c.dataNascimento()));
                });

        List<Object[]> associacoes = new ArrayList<>();
        for (int i = 0; i < candidatos.size(); i++) {
            for (String profissao : candidatos.get(i).profissoes()) {
                associacoes.add(new Object[]{ids.get(i), profissoes.get(profissao)});
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO candidato_profissao (candidato_id, profissao_id) VALUES (?, ?)", associacoes);
    }

    @Override
    public void salvarConcursos(List<Concurso> concursos) {
        Map<String, Integer> profissoes = garantirProfissoes(
                concursos.stream().flatMap(c -> c.vagas().stream()).collect(Collectors.toSet()));
        Map<String, Integer> orgaos = garantirDominio("orgao", "sigla",
                concursos.stream().map(Concurso::orgao).collect(Collectors.toSet()));

        List<Long> ids = inserirComChaves(
                "INSERT INTO concurso (orgao_id, edital, codigo) VALUES (?, ?, ?)",
                concursos, (ps, c) -> {
                    ps.setInt(1, orgaos.get(c.orgao()));
                    ps.setString(2, c.edital());
                    ps.setString(3, c.codigo().valor());
                });

        List<Object[]> vagas = new ArrayList<>();
        for (int i = 0; i < concursos.size(); i++) {
            for (String vaga : concursos.get(i).vagas()) {
                vagas.add(new Object[]{ids.get(i), profissoes.get(vaga)});
            }
        }
        jdbcTemplate.batchUpdate("INSERT INTO concurso_vaga (concurso_id, profissao_id) VALUES (?, ?)", vagas);
    }

    private Map<String, Integer> garantirProfissoes(Set<String> nomes) {
        return garantirDominio("profissao", "nome", nomes);
    }

    /**
     * Insere os valores ainda inexistentes numa tabela de domínio (id, valor único) e devolve o
     * mapa valor → id de todos os valores pedidos.
     */
    private Map<String, Integer> garantirDominio(String tabela, String coluna, Collection<String> valores) {
        String[] ordenados = new TreeSet<>(valores).toArray(String[]::new);
        // Nomes de tabela/coluna são constantes internas desta classe, nunca vindos do usuário.
        jdbc.sql("INSERT INTO %s (%s) SELECT unnest(?::text[]) ON CONFLICT (%s) DO NOTHING"
                        .formatted(tabela, coluna, coluna))
                .param(ordenados)
                .update();
        return jdbc.sql("SELECT %s, id FROM %s WHERE %s = ANY (?::text[])".formatted(coluna, tabela, coluna))
                .param(ordenados)
                .query((rs, i) -> Map.entry(rs.getString(1), rs.getInt(2)))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private <T> List<Long> inserirComChaves(String sql, List<T> itens, PreenchedorLinha<T> preenchedor) {
        KeyHolder chaves = new GeneratedKeyHolder();
        jdbcTemplate.batchUpdate(
                con -> con.prepareStatement(sql, new String[]{"id"}),
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        preenchedor.preencher(ps, itens.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return itens.size();
                    }
                },
                chaves);
        return chaves.getKeyList().stream().map(m -> ((Number) m.get("id")).longValue()).toList();
    }

    @FunctionalInterface
    private interface PreenchedorLinha<T> {
        void preencher(PreparedStatement ps, T item) throws SQLException;
    }
}
