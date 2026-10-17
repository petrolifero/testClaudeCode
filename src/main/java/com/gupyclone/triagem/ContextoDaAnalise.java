package com.gupyclone.triagem;

import java.util.List;

/**
 * Tudo o que um analisador precisa para decidir, sem depender de JPA.
 *
 * <p>Deliberadamente um record com dados primitivos: uma implementacao pode
 * consultar o banco pelo {@code curriculoId} (busca textual), montar um prompt
 * a partir de {@code textoDoCurriculo} (LLM) ou usar embeddings — o contrato
 * nao muda.
 */
public record ContextoDaAnalise(
        long candidaturaId,
        long vagaId,
        String tituloDaVaga,
        String descricaoDaVaga,
        int pontuacaoMinima,
        long curriculoId,
        String textoDoCurriculo,
        List<Criterio> criterios) {

    public ContextoDaAnalise {
        criterios = List.copyOf(criterios);
    }

    /**
     * Criterio de aceite em forma de dado.
     *
     * @param expressao  consulta textual livre, ex: {@code "java or jvm"}
     * @param peso       quanto o criterio vale na pontuacao final
     * @param obrigatorio se verdadeiro, nao atender reprova a candidatura
     */
    public record Criterio(long id, String descricao, String expressao, int peso, boolean obrigatorio) {
    }

    public int pesoTotal() {
        return criterios.stream().mapToInt(Criterio::peso).sum();
    }
}
