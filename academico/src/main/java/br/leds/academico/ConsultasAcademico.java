package br.leds.academico;

import br.leds.comum.jdbc.ConsultasJdbc;
import javax.sql.DataSource;

/** Consultas sobre o esquema normalizado; a execução JDBC é herdada, aqui só há o SQL. */
final class ConsultasAcademico extends ConsultasJdbc {

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

    ConsultasAcademico(DataSource dataSource) {
        super(dataSource, SQL_CONCURSOS_POR_CPF, SQL_CANDIDATOS_POR_CONCURSO);
    }
}
