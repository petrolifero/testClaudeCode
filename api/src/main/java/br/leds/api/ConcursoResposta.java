package br.leds.api;

import br.leds.comum.dominio.ConcursoEncontrado;

/** Contrato HTTP de um concurso, independente do modelo de domínio. */
record ConcursoResposta(String orgao, String edital, long codigo) {

    static ConcursoResposta de(ConcursoEncontrado concurso) {
        return new ConcursoResposta(concurso.orgao(), concurso.edital(), concurso.codigo());
    }
}
