package br.org.leds.concursos.aplicacao;

import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Cpf;

import java.util.List;

/**
 * Porta de consulta. Cada estratégia de modelagem do banco fornece sua própria implementação.
 *
 * <p>Regra de "encaixe no perfil": um candidato se encaixa num concurso quando ao menos uma de
 * suas profissões aparece na lista de vagas do concurso.</p>
 */
public interface BuscaRepository {

    boolean existeCandidato(Cpf cpf);

    boolean existeConcurso(CodigoConcurso codigo);

    /** Concursos com ao menos uma vaga para alguma profissão do candidato, ordenados por órgão, código e edital,
     * sem repetição. */
    List<ConcursoCompativel> concursosCompativeis(Cpf cpf);

    /**
     * Candidatos com ao menos uma profissão presente nas vagas de algum concurso com o código informado,
     * ordenados por nome e CPF, sem repetição.
     */
    List<CandidatoCompativel> candidatosCompativeis(CodigoConcurso codigo);
}
