package br.leds.nativo;

import br.leds.comum.aplicacao.CargaDados;
import br.leds.comum.aplicacao.ConsultasConcursos;
import br.leds.comum.aplicacao.Modelagem;
import javax.sql.DataSource;

/** Modelagem com tipos ricos do PostgreSQL: {@code text[]} e índices GIN. */
public final class ModelagemNativa implements Modelagem {

    @Override
    public String nome() {
        return "nativo";
    }

    @Override
    public ConsultasConcursos consultas(DataSource dataSource) {
        return new ConsultasNativo(dataSource);
    }

    @Override
    public CargaDados carga(DataSource dataSource) {
        return new CargaNativa(dataSource);
    }
}
