package br.leds.comum.dominio;

import java.time.LocalDate;
import java.util.List;

/**
 * Linha do arquivo de candidatos.
 *
 * @param cpf         somente dígitos (11)
 * @param profissoes  sem repetições, minúsculas
 */
public record Candidato(String nome, LocalDate dataNascimento, String cpf, List<String> profissoes) {
}
