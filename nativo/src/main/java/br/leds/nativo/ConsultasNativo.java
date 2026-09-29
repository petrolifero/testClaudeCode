package br.leds.nativo;

import br.leds.comum.jdbc.ConsultasJdbc;
import javax.sql.DataSource;

/** Consultas com {@code &&} sobre arrays; a execução JDBC é herdada, aqui só há o SQL. */
final class ConsultasNativo extends ConsultasJdbc {

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

    ConsultasNativo(DataSource dataSource) {
        super(dataSource, SQL_CONCURSOS_POR_CPF, SQL_CANDIDATOS_POR_CONCURSO);
    }
}
