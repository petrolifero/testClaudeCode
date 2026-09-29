package br.leds.comum;

import java.time.LocalDate;

/** Resultado da consulta "candidatos compatíveis com um concurso". */
public record CandidatoEncontrado(String nome, LocalDate dataNascimento, String cpf) {
}
