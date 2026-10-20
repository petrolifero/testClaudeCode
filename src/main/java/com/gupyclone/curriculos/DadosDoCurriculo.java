package com.gupyclone.curriculos;

/**
 * Dados de um curriculo vistos de fora do agregado: sem JPA.
 *
 * <p>Nao traz {@code conteudoPdf} nem {@code texto} de proposito. Sao os dois
 * campos grandes da tabela, e quem atravessa agregados quer saber de que
 * curriculo se trata, nao ler o PDF; o texto e assunto da triagem, que o
 * consulta por SQL nativo.
 */
public record DadosDoCurriculo(Long id, Long candidatoId, String nomeArquivo,
                               OrigemTexto origemTexto, int paginas) {
}
