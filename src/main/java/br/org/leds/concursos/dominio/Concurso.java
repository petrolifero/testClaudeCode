package br.org.leds.concursos.dominio;

import java.util.Objects;
import java.util.Set;

/**
 * Concurso lido do arquivo {@code concursos.txt}.
 *
 * @param orgao  sigla do órgão (ex.: SEDU)
 * @param edital número do edital (ex.: 9/2016)
 * @param codigo código do concurso (não é único)
 * @param vagas  profissões com vagas no concurso (não vazia)
 */
public record Concurso(String orgao, String edital, CodigoConcurso codigo, Set<String> vagas) {

    public Concurso {
        Objects.requireNonNull(codigo, "codigo");
        if (orgao == null || orgao.isBlank()) {
            throw new DadoInvalidoException("Órgão do concurso é obrigatório (código " + codigo + ")");
        }
        if (edital == null || edital.isBlank()) {
            throw new DadoInvalidoException("Edital do concurso é obrigatório (código " + codigo + ")");
        }
        if (vagas == null || vagas.isEmpty()) {
            throw new DadoInvalidoException("Concurso sem vagas (código " + codigo + ")");
        }
        orgao = orgao.strip();
        edital = edital.strip();
        vagas = Set.copyOf(vagas);
    }
}
