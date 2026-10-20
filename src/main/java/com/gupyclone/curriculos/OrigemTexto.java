package com.gupyclone.curriculos;

/** De onde saiu o texto de um curriculo em PDF. */
public enum OrigemTexto {
    /** O PDF tinha camada de texto selecionavel. */
    CAMADA_DE_TEXTO,
    /** O PDF era imagem; o texto veio de OCR sobre as paginas rasterizadas. */
    OCR
}
