package br.leds.comum.jdbc;

import br.leds.comum.aplicacao.ConsultasConcursos;
import br.leds.comum.dominio.CandidatoEncontrado;
import br.leds.comum.dominio.ConcursoEncontrado;
import br.leds.comum.erro.ErroInfraestrutura;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Execução JDBC das duas consultas. O que muda entre as modelagens é <b>só o SQL</b>, recebido no construtor
 * (cada consulta tem um único parâmetro e devolve as colunas na ordem documentada abaixo).
 *
 * <ul>
 *   <li>{@code sqlConcursosPorCpf}: parâmetro CPF (texto); colunas orgao, edital, codigo</li>
 *   <li>{@code sqlCandidatosPorConcurso}: parâmetro código (bigint); colunas nome, data_nascimento, cpf</li>
 * </ul>
 */
public class ConsultasJdbc implements ConsultasConcursos {

    private final DataSource dataSource;
    private final String sqlConcursosPorCpf;
    private final String sqlCandidatosPorConcurso;

    public ConsultasJdbc(DataSource dataSource, String sqlConcursosPorCpf, String sqlCandidatosPorConcurso) {
        this.dataSource = dataSource;
        this.sqlConcursosPorCpf = sqlConcursosPorCpf;
        this.sqlCandidatosPorConcurso = sqlCandidatosPorConcurso;
    }

    @Override
    public List<ConcursoEncontrado> concursosPorCpf(String cpf) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sqlConcursosPorCpf)) {
            ps.setString(1, cpf);
            List<ConcursoEncontrado> resultado = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new ConcursoEncontrado(rs.getString(1), rs.getString(2), rs.getLong(3)));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new ErroInfraestrutura("Falha na consulta: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CandidatoEncontrado> candidatosPorConcurso(long codigoConcurso) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sqlCandidatosPorConcurso)) {
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
            throw new ErroInfraestrutura("Falha na consulta: " + e.getMessage(), e);
        }
    }
}
