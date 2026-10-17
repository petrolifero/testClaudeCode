package com.gupyclone.seguranca;

/** O token apresentado nao e valido (expirado, assinatura errada ou malformado). */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public TokenInvalidoException(String mensagem) {
        super(mensagem);
    }
}
