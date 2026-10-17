package com.gupyclone.curriculos;

/**
 * Resultado da extracao de texto de um PDF.
 *
 * @param texto   texto do curriculo, ja normalizado
 * @param origem  camada de texto do PDF ou OCR
 * @param paginas quantidade de paginas do documento
 */
public record TextoExtraido(String texto, OrigemTexto origem, int paginas) {

    public TextoExtraido {
        if (texto == null) {
            throw new IllegalArgumentException("texto nao pode ser nulo");
        }
        if (paginas < 0) {
            throw new IllegalArgumentException("paginas nao pode ser negativo");
        }
    }
}
