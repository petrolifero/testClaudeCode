package com.gupyclone.curriculos;

/**
 * O OCR nao pode ser executado: binario ausente, processo abortado ou tempo
 * esgotado.
 *
 * <p>Separada de {@link CurriculoInvalidoException} de proposito. O PDF do
 * candidato pode estar perfeito e ainda assim o servidor nao conseguir le-lo:
 * isso e falha nossa, de infraestrutura, e nao um arquivo a recusar.
 */
public class OcrIndisponivelException extends RuntimeException {

    public OcrIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public OcrIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
