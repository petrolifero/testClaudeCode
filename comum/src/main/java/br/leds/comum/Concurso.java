package br.leds.comum;

import java.util.List;

/**
 * Linha do arquivo de concursos.
 *
 * <p>Atenção: o código do concurso <b>não é único</b> nos dados de exemplo (o mesmo código aparece
 * em órgãos/editais diferentes), por isso nenhuma das soluções o usa como chave primária.
 *
 * @param vagas sem repetições, minúsculas
 */
public record Concurso(String orgao, String edital, long codigo, List<String> vagas) {
}
