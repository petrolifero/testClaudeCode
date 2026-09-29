package br.leds.academico;

import br.leds.comum.aplicacao.CargaDados;
import br.leds.comum.aplicacao.ConsultasConcursos;
import br.leds.comum.aplicacao.Modelagem;
import javax.sql.DataSource;

/** Modelagem normalizada (3FN): profissões e vagas em tabelas de associação. */
public final class ModelagemAcademica implements Modelagem {

    @Override
    public String nome() {
        return "academico";
    }

    @Override
    public ConsultasConcursos consultas(DataSource dataSource) {
        return new ConsultasAcademico(dataSource);
    }

    @Override
    public CargaDados carga(DataSource dataSource) {
        return new CargaAcademica(dataSource);
    }
}
