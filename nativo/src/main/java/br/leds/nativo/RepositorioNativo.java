package br.leds.nativo;

import br.leds.comum.Banco;
import br.leds.comum.Candidato;
import br.leds.comum.CandidatoEncontrado;
import br.leds.comum.Concurso;
import br.leds.comum.ConcursoEncontrado;
import br.leds.comum.ErroAplicacao;
import br.leds.comum.RepositorioConcursos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Modelagem com tipos ricos do PostgreSQL: {@code text[]} nas duas tabelas e índices GIN. */
public final class RepositorioNativo implements RepositorioConcursos {

    private static final int TAMANHO_LOTE = 5_000;

    /**
     * O candidato é buscado pela PK e o JOIN usa o operador de sobreposição {@code &&}. Como o array do
     * candidato é uma constante por linha externa, o planejador faz nested loop com Bitmap Index Scan no GIN
     * de {@code concurso.vagas}. Sem duplicatas: cada concurso aparece uma vez, mesmo com várias profissões em comum.
     */
    private static final String SQL_CONCURSOS_POR_CPF = """
            SELECT c.orgao, c.edital, c.codigo
              FROM candidato ca
              JOIN concurso c ON c.vagas && ca.profissoes
             WHERE ca.cpf = ?
             ORDER BY c.orgao, c.edital, c.codigo
            """;

    /** Espelho: concursos pelo índice B-tree do código; para cada um, GIN em {@code candidato.profissoes}. */
    private static final String SQL_CANDIDATOS_POR_CONCURSO = """
            SELECT DISTINCT ca.nome, ca.data_nascimento, ca.cpf
              FROM concurso c
              JOIN candidato ca ON ca.profissoes && c.vagas
             WHERE c.codigo = ?
             ORDER BY ca.nome, ca.cpf
            """;

    private final Banco banco;

    public RepositorioNativo(Banco banco) {
        this.banco = banco;
    }

    @Override
    public void criarEsquema() {
        try (Connection con = banco.abrir(); Statement st = con.createStatement()) {
            st.execute(lerRecurso("/schema.sql"));
        } catch (SQLException e) {
            throw new ErroAplicacao("Falha ao criar o esquema: " + e.getMessage(), e);
        }
    }

    @Override
    public void substituirDados(List<Candidato> candidatos, List<Concurso> concursos) {
        try (Connection con = banco.abrir()) {
            con.setAutoCommit(false);
            try {
                try (Statement st = con.createStatement()) {
                    st.execute("TRUNCATE candidato, concurso");
                }
                inserirCandidatos(con, candidatos);
                inserirConcursos(con, concursos);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ErroAplicacao("Falha ao importar (nada foi gravado): " + e.getMessage(), e);
        }
    }

    private void inserirCandidatos(Connection con, List<Candidato> candidatos) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO candidato (cpf, nome, data_nascimento, profissoes) VALUES (?, ?, ?, ?)")) {
            int pendentes = 0;
            for (Candidato c : candidatos) {
                ps.setString(1, c.cpf());
                ps.setString(2, c.nome());
                ps.setDate(3, Date.valueOf(c.dataNascimento()));
                ps.setArray(4, con.createArrayOf("text", c.profissoes().toArray()));
                ps.addBatch();
                if (++pendentes == TAMANHO_LOTE) {
                    ps.executeBatch();
                    pendentes = 0;
                }
            }
            ps.executeBatch();
        }
    }

    private void inserirConcursos(Connection con, List<Concurso> concursos) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO concurso (id, orgao, edital, codigo, vagas) VALUES (?, ?, ?, ?, ?)")) {
            long id = 0;
            int pendentes = 0;
            for (Concurso c : concursos) {
                ps.setLong(1, ++id);
                ps.setString(2, c.orgao());
                ps.setString(3, c.edital());
                ps.setLong(4, c.codigo());
                ps.setArray(5, con.createArrayOf("text", c.vagas().toArray()));
                ps.addBatch();
                if (++pendentes == TAMANHO_LOTE) {
                    ps.executeBatch();
                    pendentes = 0;
                }
            }
            ps.executeBatch();
        }
    }

    @Override
    public List<ConcursoEncontrado> concursosPorCpf(String cpf) {
        try (Connection con = banco.abrir(); PreparedStatement ps = con.prepareStatement(SQL_CONCURSOS_POR_CPF)) {
            ps.setString(1, cpf);
            List<ConcursoEncontrado> resultado = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new ConcursoEncontrado(rs.getString(1), rs.getString(2), rs.getLong(3)));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new ErroAplicacao("Falha na consulta: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CandidatoEncontrado> candidatosPorConcurso(long codigoConcurso) {
        try (Connection con = banco.abrir(); PreparedStatement ps = con.prepareStatement(SQL_CANDIDATOS_POR_CONCURSO)) {
            ps.setLong(1, codigoConcurso);
            List<CandidatoEncontrado> resultado = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new CandidatoEncontrado(
                            rs.getString(1), rs.getDate(2).toLocalDate(), rs.getString(3)));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new ErroAplicacao("Falha na consulta: " + e.getMessage(), e);
        }
    }

    private static String lerRecurso(String caminho) {
        try (InputStream in = RepositorioNativo.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new ErroAplicacao("Recurso ausente: " + caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ErroAplicacao("Falha ao ler " + caminho, e);
        }
    }
}
