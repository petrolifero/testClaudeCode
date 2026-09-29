package br.leds.comum;

import java.util.List;

/**
 * Contrato de armazenamento. As duas soluções (acadêmica e nativa) implementam esta mesma interface,
 * então a CLI, o parser e o gerador de dados são idênticos e só a modelagem no PostgreSQL muda.
 */
public interface RepositorioConcursos {

    /** Cria tabelas e índices caso ainda não existam. */
    void criarEsquema();

    /** Apaga os dados atuais e grava os informados, tudo em uma única transação. */
    void substituirDados(List<Candidato> candidatos, List<Concurso> concursos);

    /** Concursos que têm ao menos uma vaga igual a alguma profissão do candidato (CPF só com dígitos). */
    List<ConcursoEncontrado> concursosPorCpf(String cpf);

    /** Candidatos com ao menos uma profissão igual a alguma vaga de algum concurso com este código. */
    List<CandidatoEncontrado> candidatosPorConcurso(long codigoConcurso);
}
