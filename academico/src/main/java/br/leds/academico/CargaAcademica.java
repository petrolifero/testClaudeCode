package br.leds.academico;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.sql.DataSource;

/** Carga no esquema normalizado: catálogo de profissões, entidades e tabelas de associação. */
final class CargaAcademica implements CargaDados {

    private static final int TAMANHO_LOTE = 5_000;

    private final DataSource dataSource;

    CargaAcademica(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void criarEsquema() {
        try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
            st.execute(Recursos.lerTexto(CargaAcademica.class, "/schema.sql"));
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
                    st.execute("TRUNCATE candidato_profissao, concurso_vaga, candidato, concurso, profissao");
                }
                Map<String, Long> profissoes = inserirProfissoes(con, candidatos, concursos);
                inserirCandidatos(con, candidatos, profissoes);
                inserirConcursos(con, concursos, profissoes);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ErroInfraestrutura("Falha ao importar (nada foi gravado): " + e.getMessage(), e);
        }
    }

    private Map<String, Long> inserirProfissoes(Connection con, List<Candidato> candidatos, List<Concurso> concursos)
            throws SQLException {
        TreeSet<String> nomes = new TreeSet<>();
        candidatos.forEach(c -> nomes.addAll(c.profissoes()));
        concursos.forEach(c -> nomes.addAll(c.vagas()));
        Map<String, Long> ids = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO profissao (id, nome) VALUES (?, ?)")) {
            for (String nome : nomes) {
                long id = ids.size() + 1L;
                ids.put(nome, id);
                ps.setLong(1, id);
                ps.setString(2, nome);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return ids;
    }

    private void inserirCandidatos(Connection con, List<Candidato> candidatos, Map<String, Long> profissoes)
            throws SQLException {
        try (PreparedStatement pc = con.prepareStatement(
                     "INSERT INTO candidato (id, nome, data_nascimento, cpf) VALUES (?, ?, ?, ?)");
             PreparedStatement pa = con.prepareStatement(
                     "INSERT INTO candidato_profissao (candidato_id, profissao_id) VALUES (?, ?)")) {
            long id = 0;
            int pendentes = 0;
            for (Candidato c : candidatos) {
                id++;
                pc.setLong(1, id);
                pc.setString(2, c.nome());
                pc.setDate(3, Date.valueOf(c.dataNascimento()));
                pc.setString(4, c.cpf());
                pc.addBatch();
                for (String profissao : c.profissoes()) {
                    pa.setLong(1, id);
                    pa.setLong(2, profissoes.get(profissao));
                    pa.addBatch();
                }
                if (++pendentes == TAMANHO_LOTE) {
                    pc.executeBatch(); // pais antes dos filhos, por causa da chave estrangeira
                    pa.executeBatch();
                    pendentes = 0;
                }
            }
            pc.executeBatch();
            pa.executeBatch();
        }
    }

    private void inserirConcursos(Connection con, List<Concurso> concursos, Map<String, Long> profissoes)
            throws SQLException {
        try (PreparedStatement pc = con.prepareStatement(
                     "INSERT INTO concurso (id, orgao, edital, codigo) VALUES (?, ?, ?, ?)");
             PreparedStatement pv = con.prepareStatement(
                     "INSERT INTO concurso_vaga (concurso_id, profissao_id) VALUES (?, ?)")) {
            long id = 0;
            int pendentes = 0;
            for (Concurso c : concursos) {
                id++;
                pc.setLong(1, id);
                pc.setString(2, c.orgao());
                pc.setString(3, c.edital());
                pc.setLong(4, c.codigo());
                pc.addBatch();
                for (String vaga : c.vagas()) {
                    pv.setLong(1, id);
                    pv.setLong(2, profissoes.get(vaga));
                    pv.addBatch();
                }
                if (++pendentes == TAMANHO_LOTE) {
                    pc.executeBatch();
                    pv.executeBatch();
                    pendentes = 0;
                }
            }
            pc.executeBatch();
            pv.executeBatch();
        }
    }
}
