package br.leds.academico;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Modelagem normalizada: candidato/concurso + profissao + duas tabelas de associação. */
public final class RepositorioAcademico implements RepositorioConcursos {

    private static final int TAMANHO_LOTE = 5_000;

    /**
     * Caminho: candidato (índice do CPF) -> suas profissões (PK) -> concursos com a profissão (índice inverso)
     * -> concurso (PK). O IN (semi-join) evita duplicar um concurso que casa por mais de uma profissão.
     */
    private static final String SQL_CONCURSOS_POR_CPF = """
            SELECT c.orgao, c.edital, c.codigo
              FROM concurso c
             WHERE c.id IN (SELECT cv.concurso_id
                              FROM candidato ca
                              JOIN candidato_profissao cp ON cp.candidato_id = ca.id
                              JOIN concurso_vaga cv       ON cv.profissao_id = cp.profissao_id
                             WHERE ca.cpf = ?)
             ORDER BY c.orgao, c.edital, c.codigo
            """;

    /** Caminho espelhado: concursos com o código (índice) -> vagas (PK) -> candidatos (índice inverso) -> candidato (PK). */
    private static final String SQL_CANDIDATOS_POR_CONCURSO = """
            SELECT ca.nome, ca.data_nascimento, ca.cpf
              FROM candidato ca
             WHERE ca.id IN (SELECT cp.candidato_id
                               FROM concurso c
                               JOIN concurso_vaga cv       ON cv.concurso_id = c.id
                               JOIN candidato_profissao cp ON cp.profissao_id = cv.profissao_id
                              WHERE c.codigo = ?)
             ORDER BY ca.nome, ca.cpf
            """;

    private final Banco banco;

    public RepositorioAcademico(Banco banco) {
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
            throw new ErroAplicacao("Falha ao importar (nada foi gravado): " + e.getMessage(), e);
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
        try (InputStream in = RepositorioAcademico.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new ErroAplicacao("Recurso ausente: " + caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ErroAplicacao("Falha ao ler " + caminho, e);
        }
    }
}
