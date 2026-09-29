package br.leds.comum.dominio;

/** Resultado da consulta "concursos compatíveis com um candidato". */
public record ConcursoEncontrado(String orgao, String edital, long codigo) {
}
