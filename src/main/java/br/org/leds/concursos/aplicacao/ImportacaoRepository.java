package br.org.leds.concursos.aplicacao;

import br.org.leds.concursos.dominio.Candidato;
import br.org.leds.concursos.dominio.Concurso;

import java.util.List;

/**
 * Porta de escrita usada na carga inicial dos arquivos.
 */
public interface ImportacaoRepository {

    /** Indica se já existe algum candidato ou concurso gravado. */
    boolean possuiDados();

    void salvarCandidatos(List<Candidato> candidatos);

    void salvarConcursos(List<Concurso> concursos);
}
