package br.leds.comum.aplicacao;

import br.leds.comum.dominio.CandidatoEncontrado;
import br.leds.comum.dominio.ConcursoEncontrado;
import java.util.List;

/**
 * Porta de <b>leitura</b>: as duas consultas do desafio. Quem só consulta (CLI, API REST) depende apenas disto,
 * sem enxergar operações de carga (Interface Segregation).
 *
 * <p>"Compatível" = o candidato tem ao menos uma profissão igual a alguma vaga do concurso.
 */
public interface ConsultasConcursos {

    /** Concursos compatíveis com o candidato; {@code cpf} já normalizado (11 dígitos). */
    List<ConcursoEncontrado> concursosPorCpf(String cpf);

    /** Candidatos compatíveis com algum concurso que tenha este código (o código não é único). */
    List<CandidatoEncontrado> candidatosPorConcurso(long codigoConcurso);
}
