package br.org.leds.concursos.aplicacao;

/**
 * Resultado da busca 1: concurso que se encaixa no perfil de um candidato.
 */
public record ConcursoCompativel(String orgao, String codigo, String edital) {
}
