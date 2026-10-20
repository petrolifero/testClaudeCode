package com.gupyclone.curriculos;

/** O arquivo enviado nao e um PDF legivel ou nao rendeu texto algum. */
public class CurriculoInvalidoException extends RuntimeException {

    public CurriculoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public CurriculoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
