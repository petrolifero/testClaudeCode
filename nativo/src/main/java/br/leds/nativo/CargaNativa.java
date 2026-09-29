package br.leds.nativo;

import br.leds.comum.aplicacao.CargaDados;
import br.leds.comum.dominio.Candidato;
import br.leds.comum.dominio.Concurso;
import br.leds.comum.erro.ErroInfraestrutura;
import br.leds.comum.jdbc.Recursos;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;

/** Carga no esquema com arrays: uma linha por candidato/concurso, listas como {@code text[]}. */
final class CargaNativa implements CargaDados {

    private static final int TAMANHO_LOTE = 5_000;

    private final DataSource dataSource;

    CargaNativa(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void criarEsquema() {
        try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
            st.execute(Recursos.lerTexto(CargaNativa.class, "/schema.sql"));
        } catch (SQLException e) {
            throw new ErroInfraestrutura("Falha ao criar o esquema: " + e.getMessage(), e);
        }
    }

    @Override
    public void substituirDados(List<Candidato> candidatos, List<Concurso> concursos) {
        try (Connection con = dataSource.getConnection()) {
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
            throw new ErroInfraestrutura("Falha ao importar (nada foi gravado): " + e.getMessage(), e);
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
}
