package com.gupyclone.curriculos;

/**
 * Porta usada por quem submete um curriculo (o fluxo de candidatura) sem
 * precisar conhecer como o texto e extraido.
 */
public interface RecebedorDeCurriculos {

    /**
     * Guarda o PDF do candidato ja com o texto extraido (camada de texto ou OCR).
     *
     * @throws CurriculoInvalidoException se o arquivo nao for um PDF legivel
     *                                   ou nao render texto algum
     */
    Curriculo receber(Long candidatoId, String nomeArquivo, byte[] pdf);
}
