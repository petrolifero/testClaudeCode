package com.gupyclone.curriculos;

/**
 * Le o texto de um curriculo em PDF.
 *
 * <p>Seam de troca: a implementacao padrao tenta a camada de texto e cai para
 * OCR quando o PDF e so imagem. Outra implementacao (um servico externo de
 * extracao, por exemplo) pode substitui-la sem tocar no resto do sistema.
 */
public interface ExtratorDeTextoDePdf {

    TextoExtraido extrair(byte[] pdf);
}
