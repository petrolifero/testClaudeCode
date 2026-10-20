package com.gupyclone.triagem;

/**
 * Decide se uma candidatura e aprovada ou reprovada.
 *
 * <p>Seam principal de troca do sistema. Hoje existem duas implementacoes
 * ({@code full-text-search} e {@code llm}); amanha pode entrar uma baseada em
 * embeddings/pgvector sem alterar o processador da fila. A escolha vem da
 * propriedade {@code gupy.triagem.estrategia}, casada com {@link #nome()}.
 */
public interface AnalisadorDeCandidatura {

    /** Nome da estrategia, como aparece em {@code gupy.triagem.estrategia}. */
    String nome();

    ResultadoDaAnalise analisar(ContextoDaAnalise contexto);
}
